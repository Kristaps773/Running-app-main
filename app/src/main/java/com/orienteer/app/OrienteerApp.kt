package com.orienteer.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class OrienteerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@OrienteerApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
        }
    }
}
