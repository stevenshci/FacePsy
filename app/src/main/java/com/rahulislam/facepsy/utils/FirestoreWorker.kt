package com.rahulislam.facepsy.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class FirestoreWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override val coroutineContext = Dispatchers.IO

    override suspend fun doWork(): Result = coroutineScope {

        val imageUriInput =
                inputData.getString("IMAGE_URI") ?: return@coroutineScope Result.failure()
        try {

            // Create a new user with a first and last name
            val user = hashMapOf(
                    "first" to "Ada",
                    "last" to "Lovelace",
                    "born" to 1815
            )

// Add a new document with a generated ID
            db.collection("users")
                    .add(user)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Error adding document", e)
                    }

            val jobs = async {
//                extractFeatures(imageUriInputut)
            }
            // awaitAll will throw an exception if a download fails, which CoroutineWorker will treat as a failure
            // Do something with the URL
            val resultJsonArray = jobs.await()

        } catch (e: Exception) {
            if (runAttemptCount < 3) {

                Result.retry()
            } else {
                Log.i(TAG, "Yo" + runAttemptCount.toString())
                Result.failure()
            }
        }

        Result.success()
    }

    companion object {
        const val TAG = "FirestoreWorker"
        val db = Firebase.firestore
    }
}