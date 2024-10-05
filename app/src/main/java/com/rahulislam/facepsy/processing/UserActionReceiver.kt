package com.rahulislam.facepsy.processing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

class UserActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // This method is called when the BroadcastReceiver is receiving an Intent broadcast.
//        TODO("FeatureExtractionReceiver.onReceive() is not implemented")
        Log.i(TAG, "intent")
        val appUsageData = hashMapOf(
                "user_id" to FirebaseAuth.getInstance().currentUser?.uid,
                "action" to intent.action.toString(),
                "timestamp" to EndlessService.kronosClock.getCurrentTimeMs()
        )
        EndlessService.db.collection("phoneUsageData")
                .add(appUsageData)
                .addOnSuccessListener { documentReference ->
                    Log.d(UserActionReceiver.TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                }
                .addOnFailureListener { e ->
                    Log.w(UserActionReceiver.TAG, "Error adding document", e)
                }
    }

    val filter: IntentFilter
        get() {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_USER_PRESENT)
            return filter
        }

    companion object {
        //        const val isCapturing = false
        const val TAG = "UserActionReceiver"
    }
}
