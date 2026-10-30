package com.nullnoop.pocket_pilots_notif

import android.content.Context

data class FlightNotificationData(
    val route: String,
    val arrival: String,
    val distance: Int,
    val phaseLabel: String,
    val stage: Int,
    val segments: List<Int>
)

object LatestFlightNotificationStore {
    private const val PREFERENCES_NAME = "latest_flight_notification"
    private const val KEY_ROUTE = "route"
    private const val KEY_ARRIVAL = "arrival"
    private const val KEY_DISTANCE = "distance"
    private const val KEY_PHASE_LABEL = "phase_label"
    private const val KEY_STAGE = "stage"
    private const val KEY_SEGMENTS = "segments"

    fun save(context: Context, notification: FlightNotificationData) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROUTE, notification.route)
            .putString(KEY_ARRIVAL, notification.arrival)
            .putInt(KEY_DISTANCE, notification.distance)
            .putString(KEY_PHASE_LABEL, notification.phaseLabel)
            .putInt(KEY_STAGE, notification.stage)
            .putString(KEY_SEGMENTS, notification.segments.joinToString(","))
            .apply()
    }

    fun load(context: Context): FlightNotificationData? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val route = preferences.getString(KEY_ROUTE, null) ?: return null
        val arrival = preferences.getString(KEY_ARRIVAL, null) ?: return null
        val phaseLabel = preferences.getString(KEY_PHASE_LABEL, null) ?: return null
        val segments = preferences.getString(KEY_SEGMENTS, null)
            ?.split(",")
            ?.mapNotNull(String::toIntOrNull)
            ?.takeIf { it.size == 5 }
            ?: return null
        val stage = preferences.getInt(KEY_STAGE, 0).takeIf { it in 1..5 } ?: return null

        return FlightNotificationData(
            route = route,
            arrival = arrival,
            distance = preferences.getInt(KEY_DISTANCE, 0),
            phaseLabel = phaseLabel,
            stage = stage,
            segments = segments
        )
    }
}