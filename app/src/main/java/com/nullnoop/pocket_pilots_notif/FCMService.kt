package com.nullnoop.pocket_pilots_notif

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FCMService : FirebaseMessagingService() {

    private val TAG = "FCMService"

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM registration token generated")

        val settings = ServerSettingsManager(this)
        ServerApiClient(settings.serverUrl).registerToken(
            token = token,
            deviceId = settings.deviceId,
            pairCode = settings.pairCode
        ) { result ->
            result.onSuccess { registration ->
                settings.pairCode = registration.pairCode
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
                if (data["event"] == "photo") {
                    FlightNotificationRenderer.showPhotoAlert(
                        this,
                        data["title"] ?: "Photo spot ahead",
                        data["message"] ?: ""
                    )
                    return
                }
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

                val photoPos = data["photo_pos"]?.toIntOrNull()
                val detail = data["detail"]

                LatestFlightNotificationStore.save(
                    this,
                    FlightNotificationData(route.toString(), arrival, distance, phaseLabel, stage, segments, photoPos, detail)
                )
                sendStyledNotification(route, arrival, distance, phaseLabel, stage, segments, photoPos, detail)
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

    private fun sendStyledNotification(
        route: CharSequence,
        arrival: CharSequence,
        distance: Int,
        phaseLabel: CharSequence,
        stage: Int,
        segments: List<Int>,
        photoPos: Int?,
        detail: String?
    ) {
        FlightNotificationRenderer.show(this, route, arrival, distance, phaseLabel, stage, segments, photoPos, detail)
    }
}