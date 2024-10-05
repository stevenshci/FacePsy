package com.rahulislam.facepsy

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.core.view.accessibility.AccessibilityManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.lyft.kronos.KronosClock
import com.rahulislam.facepsy.processing.EndlessService
import com.rahulislam.facepsy.stroop.*


class FacePsyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()

        //Configure these here for compatibility with API 13 and below.
        val config = AccessibilityServiceInfo()
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        if (Build.VERSION.SDK_INT >= 16) //Just in case this helps
            config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = config
    }
    @SuppressLint("LongLogTag")
    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        Log.d("ABC-", accessibilityEvent.packageName.toString() + " -- " + accessibilityEvent.className)
        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (accessibilityEvent.packageName != null && accessibilityEvent.className != null) {
                val componentName = ComponentName(
                        accessibilityEvent.packageName.toString(),
                        accessibilityEvent.className.toString()
                )
                val activityInfo = tryGetActivity(componentName)
                val isActivity = activityInfo != null
                if (isActivity){
                    Log.i("CurrentActivity", accessibilityEvent.packageName.toString())
//                    if (componentName.flattenToShortString() == "com.whatsapp/.Conversation"){

                    // save app usage data
                        val appUsageData = hashMapOf(
                            "user_id" to FirebaseAuth.getInstance().currentUser?.uid,
                            "action" to accessibilityEvent.packageName.toString(),
                             "timestamp" to EndlessService.kronosClock.getCurrentTimeMs()
                        )
                        EndlessService.db.collection("phoneUsageData")
                                .add(appUsageData)
                                .addOnSuccessListener { documentReference ->
                                    Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Error adding document", e)
                                }

                      if (EndlessService.appTrackingList.contains(accessibilityEvent.packageName.toString())){
                        val intent = Intent()
                        intent.action = "com.rahulislam.facepsy.triggers"
                        intent.putExtra("packageName", accessibilityEvent.packageName.toString())
                        intent.putExtra("duration", EndlessService.triggerDuration["app"].toString())
                        intent.putExtra("gameId", "appUsage")
                        this.sendBroadcast(intent)
                        Log.i("CurrentActivity", "Intent Registered")
                    }

                }
            }
        }
    }

    private fun tryGetActivity(componentName: ComponentName): ActivityInfo? {
        return try {
            packageManager.getActivityInfo(componentName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Given a package name, get application label in the default language of the device
     *
     * @param package_name
     * @return appName
     */
    private fun getApplicationName(package_name: String): String? {
        val packageManager = packageManager
        val appInfo: ApplicationInfo?
        appInfo = try {
            packageManager.getApplicationInfo(package_name, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        var appName = ""
        if (appInfo != null && packageManager.getApplicationLabel(appInfo) != null) {
            appName = packageManager.getApplicationLabel(appInfo) as String
        }
        return appName
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    override fun onInterrupt() {}

    companion object {
        const val TAG =  "FacePsyAccessibilityService"
    }

}