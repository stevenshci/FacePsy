package com.rahulislam.facepsy

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat.startForegroundService
import com.rahulislam.facepsy.processing.*
import java.io.IOException
import kotlin.system.exitProcess


class CustomUncaughtExpHandler: Thread.UncaughtExceptionHandler {
    private lateinit var cxt: Context

    constructor(context: Context) {
        cxt = context
    }

    @SuppressLint("LongLogTag")
    override fun uncaughtException(t: Thread?, ex: Throwable?) {
        Log.e(TAG,
                "*--------------- <APP> just ran into an Unhandled Exception ---------------*");
        Log.e(TAG, "Unhandled Exception: ");
//        Log.e(TAG, "Cause : " + ex.getCause().toString());
//        Log.e(TAG, "Msg : " + ex.getMessage());

        try {
            val intent = Intent(cxt, EndlessService::class.java).also {
                it.action = Actions.START.name
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    log("Starting the service in >=26 Mode")
                    startForegroundService(cxt, it)
                }
                log("Starting the service in < 26 Mode")
//                startService(it)

            }

            intent.putExtra("crash", true)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_NEW_TASK)

            val pendingIntent = PendingIntent.getActivity(cxt, 0, intent, intent.flags)

            var alarmManager: AlarmManager = cxt.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent);

//        android.os.Process.killProcess(android.os.Process.myPid());
            exitProcess(2);

        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    companion object {
        const val TAG = "CustomUncaughtExpHandler"
    }
}

