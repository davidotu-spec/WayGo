package com.example

import android.app.Application
import android.util.Log
import com.example.data.WayGoDatabase
import com.example.data.WayGoRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class WayGoApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { WayGoDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { WayGoRepository(database.dao()) }

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
        createNotificationChannel()
    }

    private fun initializeFirebase() {
        try {
            com.example.data.FirebaseAuthManager.init(this)

            // Skip real Firebase initialization in Robolectric tests to avoid async auth flakiness
            if (android.os.Build.FINGERPRINT == "robolectric") {
                Log.i("WayGoApplication", "Robolectric environment detected. Skipping real Firebase initialization.")
                return
            }

            com.example.data.DiagnosticAuthManager.initialize(this)
            com.example.data.AuthLogger.startObserving(this)
        } catch (e: Exception) {
            Log.w("WayGoApplication", "FirebaseApp initialization status: ${e.localizedMessage}")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            System.gc()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        System.gc()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "WayGo Alerts"
            val descriptionText = "Notifications for nearby ride requests and booking status updates"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel("waygo_driver_alerts", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
