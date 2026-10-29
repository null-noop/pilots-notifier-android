package com.nullnoop.pocket_pilots_notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.core.graphics.toColorInt

class FCMService : FirebaseMessagingService() {

    private val CHANNEL_SILENT_ID = "flight_tracker_silent"
    private val CHANNEL_ALERT_ID = "flight_tracker_alerts"
    private val TAG = "FCMService"

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM Token generated: $token")

        val serverUrl = ServerSettingsManager(this).serverUrl
        ServerApiClient(serverUrl).registerToken(token) { result ->
            result.onSuccess {
                Log.d(TAG, "Successfully registered token with remote server")
            }.onFailure { e ->
                Log.e(TAG, "Failed to register token with remote server", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Incoming FCM Raw Data: ${remoteMessage.data}")

        try {
            val data = remoteMessage.data
            if (data.isNotEmpty()) {
                val route = data["route"] ?: extractRouteFromTitle(data["title"]) ?: "SFO ➔ JFK"
                val arrival = extractArrival(data) ?: "Arrived"
                val distance = extractDistance(data) ?: 0
                val statusRaw = data["status"] ?: extractStatusFromProgress(data["progress"]) ?: "1"
                val phaseLabel = data["phase"] ?: getPhaseLabelFromStatus(statusRaw)
                val stage = determineStage(data, statusRaw)

                // Parse dynamic 5-segment array passed from FastAPI (e.g., "5,20,50,20,5")
                val segments = data["segments"]
                    ?.split(",")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.takeIf { it.size == 5 }
                    ?: listOf(5, 22, 46, 22, 5) // Default 5-stage allocation fallback

                sendStyledNotification(route, arrival, distance, phaseLabel, stage, segments)
                Log.d(TAG, "Notification successfully posted to NotificationManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRASH rendering notification in onMessageReceived: ${e.message}", e)
        }
    }

    private fun determineStage(data: Map<String, String>, status: CharSequence): Int {
        data["stage"]?.toIntOrNull()?.let { return it.coerceIn(1, 5) }
        data["stage_id"]?.toIntOrNull()?.let { return it.coerceIn(1, 5) }
        data["status"]?.toIntOrNull()?.let { return it.coerceIn(1, 5) }
        status.toString().toIntOrNull()?.let { return it.coerceIn(1, 5) }

        data["progress"]?.toIntOrNull()?.let { p ->
            return when {
                p >= 95 -> 5
                p >= 85 -> 4
                p >= 33 -> 3
                p >= 5  -> 2
                else    -> 1
            }
        }

        val phase = (data["phase"] ?: status.toString()).lowercase()
        return when {
            phase.contains("landed") || phase.contains("arrived") -> 5
            phase.contains("descent") -> 4
            phase.contains("cruise") || phase.contains("en route") -> 3
            phase.contains("ascent") || phase.contains("climb") -> 2
            phase.contains("takeoff") || phase.contains("departure") -> 1
            else -> 1
        }
    }

    private fun getPhaseLabelFromStatus(status: String): String {
        return when (status) {
            "1" -> "Takeoff"
            "2" -> "Ascent"
            "3" -> "Cruise"
            "4" -> "Descent"
            "5" -> "Landed"
            else -> status
        }
    }

    private fun extractRouteFromTitle(title: String?): String? {
        if (title == null) return null
        val idx = title.indexOf("(")
        return if (idx > 0) title.substring(0, idx).trim() else title
    }

    private fun extractArrival(data: Map<String, String>): String? {
        data["arrival"]?.let { return it }
        val title = data["title"]
        if (title != null && title.contains("Arrives ")) {
            val start = title.indexOf("Arrives ") + "Arrives ".length
            val end = title.indexOf(")", start)
            if (start > 0 && end > start) {
                return "Arrives " + title.substring(start, end).trim()
            }
        }
        val arrivalSec = data["arrival_sec"]?.toDoubleOrNull()
        if (arrivalSec != null && arrivalSec > 0) {
            try {
                val instant = Instant.ofEpochSecond(arrivalSec.toLong())
                val formatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
                return formatter.format(instant)
            } catch (e: Exception) {
                Log.w(TAG, "Could not format arrival_sec: $arrivalSec", e)
            }
        }
        return null
    }

    private fun extractDistance(data: Map<String, String>): Int? {
        data["distance"]?.toIntOrNull()?.let { return it }
        data["dist"]?.toIntOrNull()?.let { return it }
        val message = data["message"]
        if (message != null) {
            val regex = Regex("(\\d+)\\s*nm")
            val match = regex.find(message)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        }
        return null
    }

    private fun extractStatusFromProgress(progressStr: String?): String? {
        val p = progressStr?.toIntOrNull() ?: return null
        return when {
            p >= 95 -> "5"
            p >= 85 -> "4"
            p >= 33 -> "3"
            p >= 5  -> "2"
            else    -> "1"
        }
    }

    private fun createNotificationChannel() {
        val silentChannel = NotificationChannel(
            CHANNEL_SILENT_ID,
            "Live Flight Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent background progress updates while en route"
            setSound(null, null)
            enableVibration(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT_ID,
            "Flight Arrival Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts and sound when flight lands"
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(silentChannel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    private fun sendStyledNotification(
        route: CharSequence,
        arrival: CharSequence,
        distance: Int,
        phaseLabel: CharSequence,
        stage: Int,
        segments: List<Int>
    ) {
        createNotificationChannel()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val isLanded = stage == 5 || phaseLabel.toString().equals("Landed", ignoreCase = true)
        val contentText = if (isLanded) "Landed safely" else if (distance > 0) "En Route - $distance nm" else "En Route"

        val channelId = if (isLanded) CHANNEL_ALERT_ID else CHANNEL_SILENT_ID

        val builder = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_airplane_ticket)
            .setContentTitle("$route ($arrival)")
            .setContentText(contentText)
            .setSubText(phaseLabel)
            .setColorized(true)
            .setOngoing(!isLanded)
            .setPriority(if (isLanded) Notification.PRIORITY_HIGH else Notification.PRIORITY_LOW)

        if (android.os.Build.VERSION.SDK_INT >= 35) {
            builder.setStyle(getProgressStyle(stage, segments))
        } else {
            val progressVal = segments.take(stage).sum()
            builder.setProgress(100, progressVal, false)
        }

        notificationManager.notify(1001, builder.build())
    }

    private fun getProgressStyle(stage: Int, segments: List<Int>): Notification.ProgressStyle {
        val progressStyle = Notification.ProgressStyle()
            .setStyledByProgress(false)
            .setProgressStartIcon(Icon.createWithResource(applicationContext, R.drawable.ic_flight_takeoff))
            .setProgressEndIcon(Icon.createWithResource(applicationContext, R.drawable.ic_flight_land))

        val blue = "#1E88E5".toColorInt()
        val clear = Color.TRANSPARENT
        val green = "#4CAF50".toColorInt()
        val yellow = "#FFB300".toColorInt()

        // Active progress equals total cumulative percentage of active and completed segments
        val activeProgress = segments.take(stage).sum()
        progressStyle.setProgress(activeProgress)

        // Maps each of the 5 segments to its respective state color:
        // Completed = Blue, Active = Yellow (or Green on touchdown), Future = Clear
        val segmentList = segments.mapIndexed { index, weight ->
            val color = when {
                index < stage - 1 -> blue
                index == stage - 1 -> if (stage == 5) green else yellow
                else -> clear
            }
            Notification.ProgressStyle.Segment(weight).setColor(color)
        }

        return progressStyle.setProgressSegments(segmentList)
    }
}