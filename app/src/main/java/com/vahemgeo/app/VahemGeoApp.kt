package com.vahemgeo.app

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration

class VahemGeoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize osmdroid configuration
        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(applicationContext, applicationContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        }
    }
}
