package com.nullnoop.pocket_pilots_notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

object PlanesSaleResetNotifications {
    private const val PREFERENCES_NAME = "planes_sale_reset_notifications"
    private const val KEY_ENABLED = "enabled"
    private const val CHANNEL_ID = "planes_sale_reset"
    private const val NOTIFICATION_ID = 2001
    private const val ACTION_RESET = "com.nullnoop.pocket_pilots_notif.PLANES_SALE_RESET"

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            scheduleNextReset(context)
        } else {
            cancel(context)
        }
    }

    fun scheduleNextReset(context: Context) {
        if (!isEnabled(context)) return

        val nextMidnightUtc = Instant.now()
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMidnightUtc,
            alarmPendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context))
    }

    fun showNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.setting_planes_reset_notify_title),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_airplane_ticket)
            .setContentTitle(context.getString(R.string.planes_reset_notification_title))
            .setContentText(context.getString(R.string.planes_reset_notification_message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun alarmPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, PlanesSaleResetReceiver::class.java).setAction(ACTION_RESET),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

object WorldEventsResetNotifications {
    private const val PREFERENCES_NAME = "world_events_reset_notifications"
    private const val KEY_ENABLED = "enabled"
    private const val CHANNEL_ID = "world_events_reset"
    private const val RESET_NOTIFICATION_ID = 2002
    private const val ANNOUNCEMENT_NOTIFICATION_ID = 2003
    const val ACTION_RESET = "com.nullnoop.pocket_pilots_notif.WORLD_EVENTS_RESET"
    const val ACTION_ANNOUNCEMENT = "com.nullnoop.pocket_pilots_notif.WORLD_EVENTS_ANNOUNCEMENT"

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            scheduleNextReset(context)
        } else {
            cancel(context)
        }
    }

    fun scheduleNextReset(context: Context) {
        if (!isEnabled(context)) return

        val now = Instant.now()
        val nextMondayUtc = now
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
        var announcementTime = nextMondayUtc.minus(Duration.ofHours(24))
        if (!announcementTime.isAfter(now)) {
            announcementTime = announcementTime.plus(Duration.ofDays(7))
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMondayUtc.toEpochMilli(),
            alarmPendingIntent(context, ACTION_RESET, RESET_NOTIFICATION_ID)
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            announcementTime.toEpochMilli(),
            alarmPendingIntent(context, ACTION_ANNOUNCEMENT, ANNOUNCEMENT_NOTIFICATION_ID)
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context, ACTION_RESET, RESET_NOTIFICATION_ID))
        alarmManager.cancel(alarmPendingIntent(context, ACTION_ANNOUNCEMENT, ANNOUNCEMENT_NOTIFICATION_ID))
    }

    fun showNotification(context: Context, isAnnouncement: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.setting_world_events_reset_notify_title),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            if (isAnnouncement) ANNOUNCEMENT_NOTIFICATION_ID else RESET_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_airplane_ticket)
            .setContentTitle(
                context.getString(
                    if (isAnnouncement) R.string.world_events_announcement_notification_title
                    else R.string.world_events_reset_notification_title
                )
            )
            .setContentText(
                context.getString(
                    if (isAnnouncement) R.string.world_events_announcement_notification_message
                    else R.string.world_events_reset_notification_message
                )
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(
            if (isAnnouncement) ANNOUNCEMENT_NOTIFICATION_ID else RESET_NOTIFICATION_ID,
            notification
        )
    }

    private fun alarmPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, PlanesSaleResetReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

class PlanesSaleResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED ||
                    !NotificationManagerCompat.from(context).areNotificationsEnabled()
                ) {
                    return
                }

                val notification = LatestFlightNotificationStore.load(context) ?: return
                FlightNotificationRenderer.show(
                    context = context,
                    route = notification.route,
                    arrival = notification.arrival,
                    distance = notification.distance,
                    phaseLabel = notification.phaseLabel,
                    stage = notification.stage,
                    segments = notification.segments,
                    photoPos = notification.photoPos,
                    detail = notification.detail
                )
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                PlanesSaleResetNotifications.scheduleNextReset(context)
                WorldEventsResetNotifications.scheduleNextReset(context)
            }
            "com.nullnoop.pocket_pilots_notif.PLANES_SALE_RESET" -> {
                if (PlanesSaleResetNotifications.isEnabled(context)) {
                    PlanesSaleResetNotifications.showNotification(context)
                    PlanesSaleResetNotifications.scheduleNextReset(context)
                }
            }
            WorldEventsResetNotifications.ACTION_RESET,
            WorldEventsResetNotifications.ACTION_ANNOUNCEMENT -> {
                if (WorldEventsResetNotifications.isEnabled(context)) {
                    WorldEventsResetNotifications.showNotification(
                        context,
                        isAnnouncement = intent.action == WorldEventsResetNotifications.ACTION_ANNOUNCEMENT
                    )
                    WorldEventsResetNotifications.scheduleNextReset(context)
                }
            }
        }
    }
}