package com.rahulislam.facepsy.processing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Camera
import android.os.Environment
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.work.*
import com.cottacush.android.hiddencam.CaptureTimeFrequency
import com.cottacush.android.hiddencam.HiddenCam
import com.cottacush.android.hiddencam.OnImageCapturedListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.rahulislam.facepsy.MainActivity
import io.grpc.ClientStreamTracer
import org.json.JSONObject
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

class ScreenActionReceiver : BroadcastReceiver(), OnImageCapturedListener {
    private val TAG = "ScreenActionReceiver"

    private lateinit var hiddenCam: HiddenCam
    private lateinit var baseStorageFolder: File


    var context: Context? = null

    override fun onReceive(context: Context, intent: Intent) {
        this.context = context;

        val action = intent.action
        Log.i(TAG, action + isCameraUsebyAnotherApp().toString() )
//        if (Intent.ACTION_USER_PRESENT == action && !isCapturing && !isCameraUsebyAnotherApp()) {
        if (Intent.ACTION_USER_PRESENT == action && !isCapturing) {
            EndlessService.triggerDuration["unlockEvent"]?.let { invokeHiddenCam(context, MainActivity.RECURRING_INTERVAL, it.toLong(),"phoneUnlock", "phoneUnlock") }

        }

        if ("com.rahulislam.facepsy.triggers" == action && !isCapturing) {
            val triggerName = intent.extras!!.getString("packageName").toString()
            val duration = intent.extras!!.getString("duration").toLong()
            val gameId = intent.extras!!.getString("gameId").toString()
            invokeHiddenCam(context, MainActivity.RECURRING_INTERVAL, duration, triggerName, gameId)

        }
    }

    fun isCameraUsebyAnotherApp(): Boolean {
        var camerastatus: Camera? = null
        camerastatus = try {
            Camera.open()
        } catch (e: RuntimeException) {
            return true
        }
//        finally {
//            if (camerastatus != null) camerastatus.release()
//        }
        return false
    }
    private fun invokeHiddenCam(context: Context, recurringInterval: Long, duration: Long, triggerName: String, gameId: String) {
        baseStorageFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "HiddenCam").apply {
            //  if (exists()) deleteRecursively()
            if (!exists()) mkdir()
        }

        val jsonMetadata = JSONObject()
        jsonMetadata.put("seq_id", EndlessService.kronosClock.getCurrentTimeMs());
        jsonMetadata.put("gameId", gameId)
//        jsonMetadata.put("timestamp", EndlessService.kronosClock.getCurrentTimeMs())

        hiddenCam = HiddenCam(
                context, baseStorageFolder, this,
                CaptureTimeFrequency.Recurring(recurringInterval),
                targetResolution = Size(1080, 1920),
                triggerName = triggerName,
                metadata = jsonMetadata
        )



        var v: Vibrator? = context!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
//        v?.vibrate(100)

        isCapturing = true
        hiddenCam.start()
        Log.d(TAG, "Start")
        channgeCapturingStatus(isCapturing)
        Timer("SettingUp", false).schedule(duration) {
            hiddenCam.stop()
            isCapturing = false
            channgeCapturingStatus(isCapturing)
//            v?.vibrate(100)
        }
    }

    fun channgeCapturingStatus(captureStatus: Boolean) {
        var oldRealTimeDb = Firebase.database
        var user = FirebaseAuth.getInstance().currentUser;
        if (user != null) {
            oldRealTimeDb.getReference("/captureStatus/${user.uid}").setValue(captureStatus)
        };
    }

    override fun onImageCaptured(image: File, packageName: String, metadata: JSONObject) {

        val message = "Image captured, saved to:${image.absolutePath}"
        log(message)
//        showToast("Yo!")


//        val constraints = Constraints.Builder()
//                .setRequiresDeviceIdle(true)
//                .build()

        // Example WorkManager
        val uploadWorkRequest: WorkRequest =
                OneTimeWorkRequestBuilder<ImageProcessingWorker>()
//                        .setConstraints(constraints)
                        .setInputData(workDataOf(
                                "IMAGE_URI" to "${image.absolutePath}",
                                "TIMESTAMP" to "${EndlessService.kronosClock.getCurrentTimeMs()}",
                                "SEQ_ID" to "${metadata["seq_id"]}",
                                "GAME_ID" to "${metadata["gameId"]}",
                                "TRIGGER_NAME" to "${packageName}"
                        ))
                        .addTag("feature-extraction")
                        .setInitialDelay(5, TimeUnit.MILLISECONDS)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MILLISECONDS)
                        .build()

        context?.let {
            WorkManager
                .getInstance(it)
                .enqueue(uploadWorkRequest)
        }
    }

    override fun onImageCaptureError(e: Throwable?) {
        e?.run {
            val message = "Image captured failed:${e.message}"
//            showToast(message)
            log(message)
            printStackTrace()
        }
    }

    private fun showToast(message: String) {
//        Toast.makeText(this@ScreenActionReceiver, message, Toast.LENGTH_LONG).show()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Log.d(TAG, message)
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }
    val filter: IntentFilter
        get() {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_USER_PRESENT)
            filter.addAction("com.rahulislam.facepsy.triggers")
            return filter
        }

    companion object {
//        const val isCapturing = false
        const val TAG = "ScreenActionReceiver"
        var isCapturing = false
    }
}