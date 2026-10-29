package com.nullnoop.pocket_pilots_notif

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ServerStatusResponse(
    val status: String,
    val isTokenRegistered: Boolean
)

class ServerApiClient(private val baseUrl: String) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun formatUrl(endpoint: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val formattedBase = if (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://")) {
            "http://$cleanBase"
        } else {
            cleanBase
        }
        val cleanEndpoint = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
        return "$formattedBase$cleanEndpoint"
    }

    private fun parseTokenRegistered(json: JSONObject): Boolean {
        val keys = listOf("fcs_token_reg", "fcm_token_reg", "fcs_token_registered", "fcm_token_registered", "token_registered")
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                val raw = json.get(key)
                Log.d(TAG, "Parsing key '$key' with raw value '$raw' (Type: ${raw.javaClass.simpleName})")
                when (raw) {
                    is Boolean -> return raw
                    is Number -> return raw.toInt() != 0
                    is String -> {
                        val str = raw.trim().lowercase()
                        if (str == "false" || str == "0" || str.isEmpty() || str == "none" || str == "null") {
                            return false
                        }
                        return true
                    }
                }
            }
        }
        return false
    }

    fun registerToken(token: String, callback: ((Result<Boolean>) -> Unit)? = null) {
        executor.execute {
            val urlString = formatUrl("/register-token")
            Log.i(TAG, "========================================")
            Log.i(TAG, "--> POST $urlString")
            try {
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    doInput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val jsonBody = JSONObject().apply {
                    put("token", token)
                }

                val bodyStr = jsonBody.toString()
                Log.d(TAG, "Request Body: $bodyStr")

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(bodyStr)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val responseMsg = conn.responseMessage ?: ""
                Log.i(TAG, "<-- HTTP $responseCode $responseMsg ($urlString)")

                val isSuccess = responseCode in 200..299
                if (isSuccess) {
                    val responseText = try {
                        BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    } catch (_: Exception) { "" }
                    Log.d(TAG, "Response Body: $responseText")
                    Log.i(TAG, "Successfully registered FCM token with server!")
                } else {
                    val errorText = try {
                        BufferedReader(InputStreamReader(conn.errorStream, "UTF-8")).use { it.readText() }
                    } catch (_: Exception) { "" }
                    Log.w(TAG, "Server returned error body: $errorText")
                }
                Log.i(TAG, "========================================")
                conn.disconnect()

                mainHandler.post {
                    callback?.invoke(if (isSuccess) Result.success(true) else Result.failure(Exception("HTTP $responseCode $responseMsg")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "<-- Exception connecting to $urlString: ${e.message}", e)
                Log.i(TAG, "========================================")
                mainHandler.post {
                    callback?.invoke(Result.failure(e))
                }
            }
        }
    }

    fun checkStatus(callback: (Result<ServerStatusResponse>) -> Unit) {
        executor.execute {
            val urlString = formatUrl("/status")
            Log.i(TAG, "========================================")
            Log.i(TAG, "--> GET $urlString")
            try {
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val responseCode = conn.responseCode
                val responseMsg = conn.responseMessage ?: ""
                Log.i(TAG, "<-- HTTP $responseCode $responseMsg ($urlString)")

                if (responseCode in 200..299) {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    Log.d(TAG, "Response Body: $responseText")

                    val json = JSONObject(responseText)
                    val status = json.optString("status", "ok")
                    val isTokenRegistered = parseTokenRegistered(json)

                    Log.i(TAG, "Parsed status response -> status: '$status', isTokenRegistered: $isTokenRegistered")
                    Log.i(TAG, "========================================")
                    conn.disconnect()

                    mainHandler.post {
                        callback(Result.success(ServerStatusResponse(status, isTokenRegistered)))
                    }
                } else {
                    val errorText = try {
                        BufferedReader(InputStreamReader(conn.errorStream, "UTF-8")).use { it.readText() }
                    } catch (_: Exception) { "" }
                    Log.w(TAG, "Server returned error body: $errorText")
                    Log.i(TAG, "========================================")
                    conn.disconnect()

                    mainHandler.post {
                        callback(Result.failure(Exception("HTTP $responseCode $responseMsg")))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "<-- Exception connecting to $urlString: ${e.message}", e)
                Log.i(TAG, "========================================")
                mainHandler.post {
                    callback(Result.failure(e))
                }
            }
        }
    }

    companion object {
        private const val TAG = "ServerApiClient"
    }
}
