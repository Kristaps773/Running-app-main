package com.orienteer.app.debug

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Session debug NDJSON for Cursor debug mode (path-lead investigation).
 * Posts to the ingest server; mirrors to Logcat + app files dir.
 */
object AgentDebugLog {
    private const val TAG = "AgentDebug"
    private const val SESSION_ID = "ba53e3"
    private const val ENDPOINT =
        "http://127.0.0.1:7600/ingest/fabd1b8d-c2a7-40f0-852c-ce5a13dbc2cd"
    /** Emulator → host loopback. */
    private const val ENDPOINT_EMU =
        "http://10.0.2.2:7600/ingest/fabd1b8d-c2a7-40f0-852c-ce5a13dbc2cd"
    private const val FILE_NAME = "debug-ba53e3.log"

    private val executor = Executors.newSingleThreadExecutor()
    private val appContext = AtomicReference<Context?>(null)

    fun install(context: Context) {
        appContext.compareAndSet(null, context.applicationContext)
    }

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix"
    ) {
        val payload = JSONObject()
        payload.put("sessionId", SESSION_ID)
        payload.put("runId", runId)
        payload.put("hypothesisId", hypothesisId)
        payload.put("location", location)
        payload.put("message", message)
        payload.put("timestamp", System.currentTimeMillis())
        val dataObj = JSONObject()
        data.forEach { (k, v) -> dataObj.put(k, v ?: JSONObject.NULL) }
        payload.put("data", dataObj)
        val line = payload.toString()
        Log.i(TAG, line)
        executor.execute {
            appendLocal(line)
            post(ENDPOINT, line)
            post(ENDPOINT_EMU, line)
        }
    }

    private fun appendLocal(line: String) {
        runCatching {
            val ctx = appContext.get() ?: return
            val file = File(ctx.filesDir, FILE_NAME)
            file.appendText(line + "\n")
        }
    }

    private fun post(url: String, body: String) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 400
                readTimeout = 400
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Debug-Session-Id", SESSION_ID)
            }
            BufferedOutputStream(conn.outputStream).use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            conn.responseCode
            conn.disconnect()
        }
    }
}
