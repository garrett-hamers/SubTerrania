package com.atlyn.subterranea.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlyn.subterranea.BuildConfig
import com.atlyn.subterranea.ui.observability.Telemetry

/**
 * Phase O-3: settings overlay reachable from the gear icon in [TopHUD].
 *
 * Currently exposes:
 *   - Send feedback (opens an email composer pre-filled with the app version
 *     and device model so reports are easy to triage).
 *   - App version footer.
 *
 * Reserves space for Phase O-2 sound/haptic/music toggles; those will be added
 * once we ship audio. The Composable signature is intentionally small so that
 * adding new toggles later is a one-row addition.
 */
@Composable
fun SettingsMenu(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BackHandler(enabled = true) { onDismiss() }

    Card(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 380.dp)
            .heightIn(max = 480.dp)
            .border(1.dp, Color(0x6629B6F6), RoundedCornerShape(18.dp))
            // Consume taps so background backdrop dismissal doesn't fire inside the card.
            .clickable(enabled = true, onClick = {}),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE111827))
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Settings",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = "Close settings" }
                ) {
                    Text("✕", color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(
                "Send feedback",
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Email us about bugs, feature requests, or anything else. " +
                    "Your message will include your app version and device model.",
                color = Color(0xFF90A4AE),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    Telemetry.trackEvent("feedback_sent", emptyMap())
                    sendFeedbackEmail(context)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Email the developer" }
            ) {
                Text("✉  Email the developer", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))

            // Footer: version + telemetry status. Kept terse so it doesn't crowd
            // future toggles.
            Text(
                "SubTerrania ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = Color(0xFF90A4AE),
                fontSize = 11.sp
            )
            Text(
                if (BuildConfig.APPINSIGHTS_CONNECTION_STRING.isNotBlank())
                    "Crash + usage telemetry: on (no personal data)"
                else
                    "Crash + usage telemetry: off",
                color = Color(0xFF90A4AE),
                fontSize = 11.sp
            )
        }
    }
}

private fun sendFeedbackEmail(context: Context) {
    val subject = "SubTerrania ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) feedback"
    val body = buildString {
        appendLine("Type your feedback above this line.")
        appendLine()
        appendLine("--- Don't delete (helps us reproduce) ---")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:atlyn.help@gmail.com")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    val chooser = Intent.createChooser(intent, "Send feedback")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
