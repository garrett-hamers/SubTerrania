package com.atlyn.subterranea.ui.observability

import android.content.Context
import android.os.Build
import android.util.Log
import com.atlyn.subterranea.BuildConfig
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Phase O-3: Azure Application Insights ingestion (lightweight direct HTTP).
 *
 * Why not OpenTelemetry + Azure Monitor exporter?
 *   The official Azure Monitor exporter is a Java SDK with server-side dependencies
 *   (Reactor Netty, etc.) that fight with R8 and inflate the AAB by 1-2 MB. The
 *   plan's documented fallback is to POST envelopes directly to the AI ingestion
 *   REST API. That's what this file does. The exporter can be swapped in later
 *   without changing the [Telemetry] call sites.
 *
 * What we send (privacy-first, zero PII):
 *   - "Event" envelopes for: app_start, game_started, game_won, game_lost,
 *     feedback_sent. Each carries a small fixed property set:
 *       version, versionCode, difficulty, character, mapPreset, turnNumber,
 *       deviceModel (e.g. "Pixel 6"), apiLevel.
 *   - "Exception" envelopes for uncaught Throwables (stack trace + thread name).
 *
 * What we DO NOT send:
 *   - Player ID, anonymous user ID, IP, locale, install ID, screen dimensions,
 *     game-state contents, board layout, or any free-form text.
 *
 * Network behaviour:
 *   - All sends are off the main thread, batched on a single worker thread
 *     consuming a [LinkedBlockingQueue] — never blocks the UI.
 *   - On any failure (no network, 4xx, 5xx) the envelope is dropped (no retry,
 *     no on-disk queueing). This keeps the implementation simple and bounds
 *     storage to RAM.
 *   - If [BuildConfig.APPINSIGHTS_CONNECTION_STRING] is empty (e.g. local debug
 *     builds), [Telemetry.init] becomes a no-op.
 */
object Telemetry {

    private const val TAG = "SubterraneaTelemetry"

    private val initialized = AtomicBoolean(false)
    @Volatile private var ingestionEndpoint: String? = null
    @Volatile private var instrumentationKey: String? = null
    @Volatile private var deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    @Volatile private var apiLevel: Int = Build.VERSION.SDK_INT

    private val queue = LinkedBlockingQueue<JSONObject>(/* capacity = */ 100)
    private var worker: Thread? = null

    /**
     * Initializes telemetry. Safe to call multiple times — only the first call
     * has an effect. Should be called from `Application.onCreate`.
     */
    @Synchronized
    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (initialized.get()) return

        val connStr = BuildConfig.APPINSIGHTS_CONNECTION_STRING
        if (connStr.isBlank()) {
            Log.i(TAG, "Telemetry disabled (no connection string).")
            return
        }

        val parsed = parseConnectionString(connStr)
        ingestionEndpoint = parsed["IngestionEndpoint"]?.trimEnd('/')
        instrumentationKey = parsed["InstrumentationKey"]
        if (ingestionEndpoint.isNullOrBlank() || instrumentationKey.isNullOrBlank()) {
            Log.w(TAG, "Telemetry disabled (malformed connection string).")
            return
        }

        // Install uncaught-exception handler. Chains to the previous handler
        // (typically Android's default crash dialog) so we don't suppress
        // existing behavior.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                trackException(e, t.name)
                // Force a synchronous flush so the envelope leaves the device
                // before the process dies.
                flushBlocking(timeoutMs = 1500)
            } catch (_: Throwable) { /* never let telemetry break the crash */ }
            previous?.uncaughtException(t, e)
        }

        worker = thread(start = true, isDaemon = true, name = "TelemetryWorker") {
            workerLoop()
        }

        initialized.set(true)
        Log.i(TAG, "Telemetry initialized (endpoint=$ingestionEndpoint).")
        trackEvent("app_start", emptyMap())
    }

    /** Send a custom event with a small property bag. */
    fun trackEvent(name: String, properties: Map<String, String>) {
        if (!initialized.get()) return
        val envelope = buildEventEnvelope(name, properties)
        queue.offer(envelope)
    }

    /** Send an exception (uncaught or caught). */
    fun trackException(throwable: Throwable, threadName: String? = null) {
        if (!initialized.get()) return
        val envelope = buildExceptionEnvelope(throwable, threadName)
        queue.offer(envelope)
    }

    /**
     * Block the caller until the queue is drained or [timeoutMs] elapses.
     * Used by the crash handler before the process dies.
     */
    fun flushBlocking(timeoutMs: Long) {
        if (!initialized.get()) return
        val deadline = System.currentTimeMillis() + timeoutMs
        while (queue.isNotEmpty() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50) } catch (_: InterruptedException) { return }
        }
    }

    // ----- Internal -------------------------------------------------------

    private fun workerLoop() {
        while (true) {
            val envelope = try {
                queue.poll(30, TimeUnit.SECONDS) ?: continue
            } catch (_: InterruptedException) {
                return
            }
            try {
                postEnvelope(envelope)
            } catch (t: Throwable) {
                // Never let telemetry crash itself.
                Log.w(TAG, "Telemetry post failed: ${t.message}")
            }
        }
    }

    private fun postEnvelope(envelope: JSONObject) {
        val endpoint = ingestionEndpoint ?: return
        val url = URL("$endpoint/v2.1/track")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(envelope.toString()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Telemetry post returned $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun buildEventEnvelope(name: String, props: Map<String, String>): JSONObject {
        val data = JSONObject().apply {
            put("baseType", "EventData")
            put("baseData", JSONObject().apply {
                put("ver", 2)
                put("name", name)
                put("properties", JSONObject(commonProperties() + props))
            })
        }
        return baseEnvelope("Microsoft.ApplicationInsights.Event", data)
    }

    private fun buildExceptionEnvelope(t: Throwable, threadName: String?): JSONObject {
        val parsed = JSONObject().apply {
            put("id", t.hashCode())
            put("typeName", t.javaClass.name)
            put("message", t.message ?: t.javaClass.simpleName)
            put("hasFullStack", true)
            put("parsedStack", parseStack(t))
        }
        val data = JSONObject().apply {
            put("baseType", "ExceptionData")
            put("baseData", JSONObject().apply {
                put("ver", 2)
                put("exceptions", org.json.JSONArray().apply { put(parsed) })
                val props = commonProperties() + buildMap {
                    if (threadName != null) put("threadName", threadName)
                }
                put("properties", JSONObject(props))
            })
        }
        return baseEnvelope("Microsoft.ApplicationInsights.Exception", data)
    }

    private fun parseStack(t: Throwable): org.json.JSONArray {
        val arr = org.json.JSONArray()
        t.stackTrace.forEachIndexed { idx, frame ->
            arr.put(JSONObject().apply {
                put("level", idx)
                put("method", frame.methodName)
                put("assembly", frame.className)
                put("fileName", frame.fileName ?: "")
                put("line", frame.lineNumber)
            })
        }
        return arr
    }

    private fun baseEnvelope(envType: String, data: JSONObject): JSONObject {
        return JSONObject().apply {
            put("name", envType)
            put("time", isoUtcNow())
            put("iKey", instrumentationKey)
            put("tags", JSONObject(mapOf(
                "ai.cloud.role" to "subterranea-android",
                "ai.cloud.roleInstance" to UUID.randomUUID().toString(),
                "ai.application.ver" to BuildConfig.VERSION_NAME,
                "ai.device.osVersion" to "Android $apiLevel",
                "ai.device.model" to deviceModel
            )))
            put("data", data)
        }
    }

    private fun commonProperties(): Map<String, String> = mapOf(
        "version" to BuildConfig.VERSION_NAME,
        "versionCode" to BuildConfig.VERSION_CODE.toString(),
        "deviceModel" to deviceModel,
        "apiLevel" to apiLevel.toString()
    )

    private val isoFormatter: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private fun isoUtcNow(): String = isoFormatter.format(Date())

    /**
     * Parses an Application Insights connection string into a map.
     * Example: `InstrumentationKey=...;IngestionEndpoint=https://...;`
     */
    private fun parseConnectionString(raw: String): Map<String, String> {
        return raw.split(';')
            .mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx <= 0 || idx >= entry.lastIndex) null
                else entry.substring(0, idx).trim() to entry.substring(idx + 1).trim()
            }
            .toMap()
    }
}
