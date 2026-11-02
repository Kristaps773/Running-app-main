package com.orienteer.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import org.osmdroid.config.Configuration

@HiltAndroidApp
class OrienteerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initSentry()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@OrienteerApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
        }
    }

    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return

        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.environment = if (BuildConfig.DEBUG) "debug" else "production"
            options.release =
                "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2
            options.isSendDefaultPii = false
            options.isEnableUserInteractionTracing = true
        }
    }
}
