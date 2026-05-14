package com.atlyn.subterranea

import android.app.Application
import com.atlyn.subterranea.ui.observability.Telemetry

/**
 * Phase O-3: minimal [Application] subclass whose only job is to bootstrap
 * Azure Application Insights telemetry on app start.
 *
 * Telemetry is intentionally PII-free; see [Telemetry] for what is and isn't
 * sent. If [BuildConfig.APPINSIGHTS_CONNECTION_STRING] is empty (the default
 * for local debug builds), [Telemetry.init] becomes a no-op and the rest of
 * the app behaves identically.
 */
class SubterraneaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Telemetry.init(this)
    }
}
