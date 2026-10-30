package com.neuralroots.kosh.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class SheetsException(message: String) : Exception(message)

data class PingResult(val spreadsheetName: String, val spreadsheetUrl: String)

/**
 * Talks to the user's Apps Script Web App. Apps Script answers a POST with a 302 to
 * script.googleusercontent.com, which must be fetched with GET — so redirects are followed manually.
 */
object SheetsClient {
    private const val TIMEOUT_MS = 30_000

    suspend fun ping(url: String, token: String): PingResult {
        val res = post(url, JSONObject().put("token", token).put("action", "ping"))
        return PingResult(res.optString("spreadsheetName"), res.optString("spreadsheetUrl"))
    }

    /** Sends a batch of ops; returns the response JSON. */
    suspend fun batch(url: String, token: String, ops: JSONArray): JSONObject =
        post(url, JSONObject().put("token", token).put("action", "batch").put("ops", ops))

    suspend fun post(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) throw SheetsException("The Web app URL must start with https://")
        try {
            var conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS * 2
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            var code = conn.responseCode
            var hops = 0
            while (code in 300..399 && hops < 5) {
                val location = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                conn = (URL(location).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS * 2
                }
                code = conn.responseCode
                hops++
            }
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            conn.disconnect()

            when {
                code == 401 || code == 403 -> throw SheetsException(
                    "Access denied ($code). In Apps Script, redeploy with \"Who has access: Anyone\".",
                )
                code == 404 -> throw SheetsException("Web app not found (404). Check that you copied the full /exec URL.")
                code >= 400 -> throw SheetsException("Server error $code. Try again in a minute.")
            }
            val trimmed = text.trimStart()
            if (trimmed.startsWith("<")) throw SheetsException(
                "Got a Google web page instead of data. Make sure the deployment is a Web app with " +
                    "\"Who has access: Anyone\" and that the URL ends in /exec.",
            )
            val json = try { JSONObject(trimmed) } catch (e: JSONException) {
                throw SheetsException("Unexpected response from the script. Paste the latest script and redeploy.")
            }
            if (!json.optBoolean("ok", false)) throw SheetsException(json.optString("error", "The script reported an error"))
            json
        } catch (e: SheetsException) {
            throw e
        } catch (e: UnknownHostException) {
            throw SheetsException("No internet connection")
        } catch (e: SocketTimeoutException) {
            throw SheetsException("Connection timed out")
        } catch (e: IOException) {
            throw SheetsException("Network error: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
