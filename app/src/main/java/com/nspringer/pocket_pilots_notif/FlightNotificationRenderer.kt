package com.nullnoop.pocket_pilots_notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.toColorInt

object FlightNotificationRenderer {
    private const val CHANNEL_SILENT_ID = "flight_tracker_silent"
    private const val CHANNEL_ALERT_ID = "flight_tracker_alerts"
    private const val NOTIFICATION_ID = 1001

    private data class TestStage(
        val label: String,
        val distance: Int
    )

    fun show(
        context: Context,
        route: CharSequence,
        arrival: CharSequence,
        distance: Int,
        phaseLabel: CharSequence,
        stage: Int,
        segments: List<Int>
    ) {
        createNotificationChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val isLanded = stage == 5 || phaseLabel.toString().equals("Landed", ignoreCase = true)
        val contentText = if (isLanded) "Landed safely" else if (distance > 0) "En Route - $distance nm" else "En Route"
        val channelId = if (isLanded) CHANNEL_ALERT_ID else CHANNEL_SILENT_ID

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_airplane_ticket)
            .setContentTitle("$route ($arrival)")
            .setContentText(contentText)
            .setSubText(phaseLabel)
            .setColorized(true)
            .setOngoing(!isLanded)
            .setPriority(if (isLanded) Notification.PRIORITY_HIGH else Notification.PRIORITY_LOW)

        if (android.os.Build.VERSION.SDK_INT >= 35) {
            builder.setStyle(getProgressStyle(context, stage, segments))
        } else {
            builder.setProgress(100, segments.take(stage).sum(), false)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun showTestSequence(context: Context) {
        val appContext = context.applicationContext
        val stages = listOf(
            TestStage("Takeoff", 3200),
            TestStage("Ascent", 2400),
            TestStage("Cruise", 1200),
            TestStage("Descent", 180),
            TestStage("Landed", 0)
        )
        val segments = listOf(5, 22, 46, 22, 5)
        val handler = Handler(Looper.getMainLooper())

        stages.forEachIndexed { index, testStage ->
            handler.postDelayed({
                show(
                    context = appContext,
                    route = "SFO ➔ JFK",
                    arrival = "Arrives 4:15 PM",
                    distance = testStage.distance,
                    phaseLabel = testStage.label,
                    stage = index + 1,
                    segments = segments
                )
            }, index * 2500L)
        }
    }

    private fun createNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT_ID, "Live Flight Progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Silent background progress updates while en route"
                setSound(null, null)
                enableVibration(false)
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT_ID, "Flight Arrival Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts and sound when flight lands"
            }
        )
    }

    private fun getProgressStyle(context: Context, stage: Int, segments: List<Int>): Notification.ProgressStyle {
        val progressStyle = Notification.ProgressStyle()
            .setStyledByProgress(false)
            .setProgressStartIcon(Icon.createWithResource(context, R.drawable.ic_flight_takeoff))
            .setProgressEndIcon(Icon.createWithResource(context, R.drawable.ic_flight_land))

        val blue = "#1E88E5".toColorInt()
        val clear = Color.TRANSPARENT
        val green = "#4CAF50".toColorInt()
        val yellow = "#FFB300".toColorInt()
        progressStyle.setProgress(segments.take(stage).sum())

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