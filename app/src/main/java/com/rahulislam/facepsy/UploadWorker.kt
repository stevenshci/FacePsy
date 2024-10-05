package com.rahulislam.facepsy

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters

class UploadWorker(appContext: Context, workerParams: WorkerParameters):
        Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val imageUriInput =
                inputData.getString("IMAGE_URI") ?: return Result.failure()
        // Do the work here--in this case, upload the images.
        Log.i(TAG, imageUriInput)
        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }

    companion object{
        const val TAG = "UploadWorker"
    }
}
