package com.nullnoop.pocket_pilots_notif

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var settingsManager: ServerSettingsManager
    private lateinit var tvServerUrlSubtitle: TextView
    private lateinit var tvServerStatusSubtitle: TextView
    private lateinit var tvFcmTokenSubtitle: TextView
    private lateinit var tvPairCodeSubtitle: TextView

    private var cachedFcmToken: String? = null
    private var isRegisteringToken = false
    private var isCheckingStatus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Enable Material You dynamic wallpaper colors (Android 12+)
        DynamicColors.applyToActivityIfAvailable(this)

        // 2. Enable Edge-to-Edge rendering
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsManager = ServerSettingsManager(this)
        if (
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("use_local_api", false)
        ) {
            settingsManager.serverUrl = "http://127.0.0.1:8000"
        }

        // Initialize UI Elements
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        tvServerUrlSubtitle = findViewById(R.id.tvServerUrlSubtitle)
        tvServerStatusSubtitle = findViewById(R.id.tvServerStatusSubtitle)
        tvFcmTokenSubtitle = findViewById(R.id.tvFcmTokenSubtitle)
        tvPairCodeSubtitle = findViewById(R.id.tvPairCodeSubtitle)

        val cardServerUrl = findViewById<MaterialCardView>(R.id.cardServerUrl)
        val cardServerStatus = findViewById<MaterialCardView>(R.id.cardServerStatus)
        val cardPairCode = findViewById<MaterialCardView>(R.id.cardPairCode)
        val cardFcmToken = findViewById<MaterialCardView>(R.id.cardFcmToken)
        val cardTestNotify = findViewById<MaterialCardView>(R.id.cardTestNotify)
        val cardRestoreNotify = findViewById<MaterialCardView>(R.id.cardRestoreNotify)
        val switchPlanesResetNotify = findViewById<MaterialSwitch>(R.id.switchPlanesResetNotify)
        val switchWorldEventsResetNotify = findViewById<MaterialSwitch>(R.id.switchWorldEventsResetNotify)

        // 3. Dynamically apply top padding to topAppBar for status bar & camera cutout
        ViewCompat.setOnApplyWindowInsetsListener(topAppBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        // Display current server URL setting
        updateServerUrlDisplay()
        updatePairCodeDisplay()

        // Set click listeners
        cardServerUrl.setOnClickListener {
            showEditServerUrlDialog()
        }

        cardServerStatus.setOnClickListener {
            Log.d(TAG, "Server Status card clicked by user")
            checkServerStatus()
        }

        cardPairCode.setOnClickListener {
            val pairCode = settingsManager.pairCode
            if (pairCode.isNullOrBlank()) {
                Toast.makeText(this, R.string.pair_code_not_ready, Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Pocket Pilots pairing code", formatPairCode(pairCode)))
                Toast.makeText(this, R.string.pair_code_copied, Toast.LENGTH_SHORT).show()
            }
        }

        cardFcmToken.setOnClickListener {
            Log.d(TAG, "FCM Token card clicked by user")
            copyAndRegisterFcmToken()
        }

        cardTestNotify.setOnClickListener {
            Log.d(TAG, "Test Notification card clicked by user")
            sendTestNotification()
        }

        cardRestoreNotify.setOnClickListener {
            Log.d(TAG, "Restore Flight Notification card clicked by user")
            restoreLatestFlightNotification()
        }

        switchPlanesResetNotify.isChecked = PlanesSaleResetNotifications.isEnabled(this)
        switchPlanesResetNotify.setOnCheckedChangeListener { _, enabled ->
            PlanesSaleResetNotifications.setEnabled(this, enabled)
        }
        if (switchPlanesResetNotify.isChecked) {
            PlanesSaleResetNotifications.scheduleNextReset(this)
        }
        switchWorldEventsResetNotify.isChecked = WorldEventsResetNotifications.isEnabled(this)
        switchWorldEventsResetNotify.setOnCheckedChangeListener { _, enabled ->
            WorldEventsResetNotifications.setEnabled(this, enabled)
        }
        if (switchWorldEventsResetNotify.isChecked) {
            WorldEventsResetNotifications.scheduleNextReset(this)
        }

        // Request notification permissions for Android 13+
        requestNotificationPermission()

        // Fetch FCM Token & Register with Server
        fetchFcmToken()

        // Initial Server Status Check
        checkServerStatus()
    }

    private fun updateServerUrlDisplay() {
        val currentUrl = settingsManager.serverUrl
        Log.d(TAG, "Current saved Server URL: $currentUrl")
        tvServerUrlSubtitle.text = currentUrl
    }

    private fun updatePairCodeDisplay() {
        val pairCode = settingsManager.pairCode
        tvPairCodeSubtitle.text = if (pairCode.isNullOrBlank()) {
            getString(R.string.pair_code_not_ready)
        } else {
            formatPairCode(pairCode)
        }
    }

    private fun formatPairCode(code: String): String = code.chunked(6).joinToString("-")

    private fun checkServerStatus() {
        if (isCheckingStatus) {
            Log.d(TAG, "Server status check already in progress. Skipping.")
            return
        }
        isCheckingStatus = true
        Log.d(TAG, "Initiating server status check for '${settingsManager.serverUrl}'...")
        tvServerStatusSubtitle.text = getString(R.string.status_checking)
        val client = ServerApiClient(settingsManager.serverUrl)
        client.checkStatus(settingsManager.pairCode) { result ->
            isCheckingStatus = false
            result.onSuccess { statusResp ->
                Log.i(TAG, "Server status check succeeded: $statusResp")
                tvServerStatusSubtitle.text = when (statusResp.isTokenRegistered) {
                    true -> getString(R.string.status_online_token_registered)
                    false -> getString(R.string.status_online_token_not_registered)
                    null -> getString(R.string.status_online)
                }
            }.onFailure { error ->
                Log.w(TAG, "Server status check failed for '${settingsManager.serverUrl}': ${error.message}")
                tvServerStatusSubtitle.text = getString(R.string.status_offline)
            }
        }
    }

    private fun registerTokenWithServer(token: String) {
        if (isRegisteringToken) {
            Log.d(TAG, "Token registration already in progress. Skipping.")
            return
        }
        isRegisteringToken = true
        Log.d(TAG, "Initiating FCM token registration with '${settingsManager.serverUrl}'...")
        val client = ServerApiClient(settingsManager.serverUrl)
        client.registerToken(token, settingsManager.deviceId, settingsManager.pairCode) { result ->
            isRegisteringToken = false
            result.onSuccess { registration ->
                settingsManager.pairCode = registration.pairCode
                updatePairCodeDisplay()
                Log.i(TAG, "Successfully registered FCM token at /register-token on '${settingsManager.serverUrl}'")
                checkServerStatus()
            }.onFailure { error ->
                Log.e(TAG, "Failed to register FCM token at /register-token on '${settingsManager.serverUrl}'", error)
                checkServerStatus()
            }
        }
    }

    private fun showEditServerUrlDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_url, null)
        val inputEditText = dialogView.findViewById<TextInputEditText>(R.id.urlEditText)

        inputEditText.setText(settingsManager.serverUrl)
        inputEditText.setSelection(inputEditText.text?.length ?: 0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_server_url_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_save) { dialog, _ ->
                val newUrl = inputEditText.text?.toString()?.trim() ?: ""
                if (newUrl.isNotEmpty()) {
                    Log.i(TAG, "Updating Server URL from '${settingsManager.serverUrl}' to '$newUrl'")
                    settingsManager.serverUrl = newUrl
                    updateServerUrlDisplay()
                    Toast.makeText(this, getString(R.string.url_saved), Toast.LENGTH_SHORT).show()

                    // Re-register token and re-check status on the new URL
                    cachedFcmToken?.let { token ->
                        registerTokenWithServer(token)
                    } ?: run {
                        checkServerStatus()
                    }

                    dialog.dismiss()
                } else {
                    Toast.makeText(this, getString(R.string.url_error_empty), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun copyAndRegisterFcmToken() {
        val token = cachedFcmToken
        if (!token.isNullOrEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("FCM Token", token)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.token_copied), Toast.LENGTH_SHORT).show()
            registerTokenWithServer(token)
        } else {
            Toast.makeText(this, getString(R.string.token_not_ready), Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun fetchFcmToken() {
        tvFcmTokenSubtitle.text = getString(R.string.token_not_ready)
        Log.d(TAG, "Fetching FCM registration token from FirebaseMessaging...")
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            cachedFcmToken = token
            Log.i(TAG, "Fetched device FCM token")
            tvFcmTokenSubtitle.text = getString(R.string.token_available)

        }
    }

    private fun sendTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission()
                return
            }
        }

        FlightNotificationRenderer.showTestSequence(applicationContext)
    }

    private fun restoreLatestFlightNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission()
            return
        }

        val notification = LatestFlightNotificationStore.load(applicationContext)
        if (notification == null) {
            Toast.makeText(this, R.string.restore_notify_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        FlightNotificationRenderer.show(
            context = applicationContext,
            route = notification.route,
            arrival = notification.arrival,
            distance = notification.distance,
            phaseLabel = notification.phaseLabel,
            stage = notification.stage,
            segments = notification.segments,
            photoPos = notification.photoPos,
            detail = notification.detail
        )
        Toast.makeText(this, R.string.restore_notify_posted, Toast.LENGTH_SHORT).show()
    }
}