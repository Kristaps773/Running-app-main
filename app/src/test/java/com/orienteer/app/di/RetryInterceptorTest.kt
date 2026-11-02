package com.orienteer.app.di

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RetryInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 2))
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun retriesTransientStatusThenSucceeds() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(
            Request.Builder().url(server.url("/route")).get().build()
        ).execute()

        assertEquals(200, response.code)
        assertEquals("ok", response.body?.string())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun doesNotRetryPermanentClientError() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))

        val response = client.newCall(
            Request.Builder().url(server.url("/missing")).get().build()
        ).execute()

        assertEquals(404, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun givesUpAfterMaxRetriesOnPersistentServerError() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(502)) }

        val response = client.newCall(
            Request.Builder().url(server.url("/route")).get().build()
        ).execute()

        assertEquals(502, response.code)
        assertTrue(server.requestCount <= 3)
        assertEquals(3, server.requestCount)
    }
}
