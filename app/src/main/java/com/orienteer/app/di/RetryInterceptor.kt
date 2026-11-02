package com.orienteer.app.di

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.min

/**
 * Retries transient public-API failures (rate limits / gateway errors) with short backoff.
 * Safe for this app because OSRM and Overpass calls are GET-only.
 */
internal class RetryInterceptor(
    private val maxRetries: Int = 2
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastError: IOException? = null

        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(request)
                if (response.code !in RETRY_STATUS_CODES || attempt == maxRetries) {
                    return response
                }
                response.close()
            } catch (e: IOException) {
                lastError = e
                if (attempt == maxRetries) throw e
            }
            Thread.sleep(backoffMs(attempt))
            attempt++
        }
        throw lastError ?: IOException("Request failed after $maxRetries retries")
    }

    private fun backoffMs(attempt: Int): Long {
        val base = 350L * (1L shl attempt.coerceAtMost(3))
        val jitter = (0..120).random().toLong()
        return min(2_000L, base + jitter)
    }

    private companion object {
        private val RETRY_STATUS_CODES = setOf(429, 502, 503, 504)
    }
}
