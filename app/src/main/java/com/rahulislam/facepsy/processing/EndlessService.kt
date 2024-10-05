package com.rahulislam.facepsy.processing

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.core.view.accessibility.AccessibilityManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import com.rahulislam.facepsy.CustomUncaughtExpHandler
import com.rahulislam.facepsy.MainActivity
import com.rahulislam.facepsy.R
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import com.google.firebase.firestore.SetOptions
import java.io.IOException
import kotlin.collections.HashMap
import kotlin.system.exitProcess

class EndlessService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false

    private lateinit var screenActionReceiver: ScreenActionReceiver
    private lateinit var  userActionReceiver: UserActionReceiver

    override fun onBind(intent: Intent): IBinder? {
        log("Some component want to bind with the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand executed with startId: $startId")
        if (intent != null) {
            val action = intent.action
            log("using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> log("This should never happen. No action in the received intent")
            }
        } else {
            log(
                "with a null intent. It has been probably restarted by the system."
            )
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        log("The service has been created".toUpperCase())
        val notification = createNotification()
        startForeground(1, notification)

        isAccessibilityServiceActive(applicationContext)

        // APP TRIGGER CONFIG FETCH
        val docRef = db.collection("config").document("triggers")
        docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                Log.d(TAG, "Current data: ${snapshot.data?.get("apps")?.javaClass?.kotlin}")
                var apps: ArrayList<Map<String, String>> = snapshot.data?.get("apps") as ArrayList<Map<String, String>>
                for (app in apps){
                    val appTrackingListCopy = appTrackingList.toMutableSet()
                    if(app["enable"] as Boolean) {
                        app["packageName"]?.let { appTrackingListCopy.add(it) }
                    } else {
                        app["packageName"]?.let { appTrackingListCopy.remove(it) }
                    }
                    appTrackingList = appTrackingListCopy.toTypedArray()
                }

            } else {
                Log.d(TAG, "Current data: null")
            }
        }
        // APP TRIGGER CONFIG FETCH

        // TRIGGER DURATION FETCH
        val triggerDurationDocRef = db.collection("config").document("triggerDuration")
        triggerDurationDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                triggerDuration = snapshot.data as HashMap<String, Long>
            } else {
                Log.d(TAG, "Current data: null")
            }
        }
        // TRIGGER DURATION FETCH

        // STROOP TASK FETCH
        val stroopTaskDocRef = db.collection("config").document("stroopTask")
        stroopTaskDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                stroopConfig = snapshot.data as HashMap<String, Int>
                Log.i(TAG, stroopConfig.toString())
            } else {
                Log.d(TAG, "Current data: null")
            }
        }
        // STROOP TASK FETCH

        // SURVEY FETCH
        val surveyDocRef = db.collection("config").document("survey")
        surveyDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                surveyConfig = snapshot.data as HashMap<String, String>
                Log.i(TAG, surveyConfig.toString())
            } else {
                Log.d(TAG, "Current data: null")
            }
        }
        // SURVEY FETCH

        kronosClock = AndroidClockFactory.createKronosClock(applicationContext)
        kronosClock.syncInBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        log("The service has been destroyed".toUpperCase())
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()

        // Log id Destoryed
        var oldRealTimeDb = Firebase.database
        var user = FirebaseAuth.getInstance().currentUser;
        if (user != null) {
            oldRealTimeDb.getReference("/status/${user.uid}").setValue("destroyed")
        };
        // END

        // Call Alarm Manager to restart
        try {
            val intent = Intent(this.baseContext, EndlessService::class.java).also {
                it.action = Actions.START.name
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    log("Starting the service in >=26 Mode")
                    ContextCompat.startForegroundService(this.baseContext, it)
                }
                log("Starting the service in < 26 Mode")
//                startService(it)

            }

            intent.putExtra("crash", true)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_NEW_TASK)

            val pendingIntent = PendingIntent.getActivity(this.baseContext, 0, intent, intent.flags)

            var alarmManager: AlarmManager = this.baseContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent);

//        android.os.Process.killProcess(android.os.Process.myPid());
            exitProcess(2);

        } catch (e: IOException) {
            e.printStackTrace()
        }
        // END
    }

    private fun startService() {
        if (isServiceStarted) return
        log("Starting the foreground service task")
        Toast.makeText(this, "FacePsy is running on background.", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                    acquire()
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceStarted) {
                launch(Dispatchers.IO) {
                    pingFakeServer()
                }
                delay(1 * 60 * 1000)
            }
            log("End of the loop for the service")
        }

        isAccessibilityServiceActive(applicationContext)

        // Block from OnCreate
        // Error Handler
        Thread.setDefaultUncaughtExceptionHandler(CustomUncaughtExpHandler(this))

        // PRESENCE TRACKER START
        var user = FirebaseAuth.getInstance().currentUser;
        val firestoreDb = Firebase.firestore
        val oldRealTimeDb = Firebase.database

        val usersRef = firestoreDb.collection("users"); // Get a reference to the Users collection;
        val onlineRef = oldRealTimeDb.getReference(".info/connected"); // Get a reference to the list of connections

        onlineRef.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
//                val value = dataSnapshot.getValue<String>()
                Log.d(TAG, "Value is:")
                if (user != null) {
                    Log.i(TAG, "USER TOH ${user.uid}")
                    oldRealTimeDb.getReference("/status/${user.uid}").onDisconnect().setValue("offline").onSuccessTask {
                        Log.i(TAG, "LOL")
                        val data = hashMapOf("online" to true)
                        usersRef.document("${user.uid}")
                                .set(data, SetOptions.merge())
                        oldRealTimeDb.getReference("/status/${user.uid}").setValue("online");
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException())
            }
        })
        // PRESENCE TRACKER
        // END

        // HiddenCam Test
        screenActionReceiver = ScreenActionReceiver()
        registerReceiver(screenActionReceiver, screenActionReceiver.filter)

        userActionReceiver = UserActionReceiver()
        registerReceiver(userActionReceiver, userActionReceiver.filter)
    }

    private fun stopService() {
        log("Stopping the foreground service")
        Toast.makeText(this, "FacePsy background service stopping", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(this, ServiceState.STOPPED)
    }

    private fun pingFakeServer() {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.mmmZ")
        val gmtTime = df.format(Date())

        val deviceId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)

        val json =
            """
                {
                    "deviceId": "$deviceId",
                    "createdAt": "$gmtTime"
                }
            """
        try {
            log("Yes!")
        } catch (e: Exception) {
            log("Error making the request: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(false)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        return builder
            .setContentTitle("FacePsy")
            .setContentText("Data collection is active")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Please do not close this")
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }

    @Synchronized
    private fun isAccessibilityEnabled(context: Context): Boolean {
        var enabled = false
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        //Try to fetch active accessibility services directly from Android OS database instead of broken API...
        val settingValue = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (settingValue != null) {
            if (settingValue.contains(context.packageName)) {
                enabled = true
            }
        }
        if (!enabled) {
            try {
                val enabledServices = AccessibilityManagerCompat.getEnabledAccessibilityServiceList(accessibilityManager, AccessibilityEventCompat.TYPES_ALL_MASK)
                if (!enabledServices.isEmpty()) {
                    for (service in enabledServices) {
                        if (service.id.contains(context.packageName)) {
                            enabled = true
                            break
                        }
                    }
                }
            } catch (e: NoSuchMethodError) {
            }
        }
        if (!enabled) {
            try {
                val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK)
                if (!enabledServices.isEmpty()) {
                    for (service in enabledServices) {
                        if (service.id.contains(context.packageName)) {
                            enabled = true
                            break
                        }
                    }
                }
            } catch (e: NoSuchMethodError) {
            }
        }

        Log.i(TAG, enabled.toString())
        //Keep the global setting up-to-date
//        Aware.setSetting(context, com.aware.Applications.STATUS_AWARE_ACCESSIBILITY, enabled, "com.aware.phone")
        return enabled
    }

    @Synchronized
    fun isAccessibilityServiceActive(c: Context): Boolean {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"
        if (!isAccessibilityEnabled(c)) {
            var mBuilder = NotificationCompat.Builder(c, notificationChannelId)
            mBuilder.setSmallIcon(R.mipmap.ic_launcher)
            mBuilder.setContentTitle("Please enable FacePsy")
            mBuilder.setContentText("Tap here to activate accessibility service")
            mBuilder.setAutoCancel(true)
            mBuilder.setOnlyAlertOnce(true) //notify the user only once
            mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL)
//            mBuilder = Aware.setNotificationProperties(mBuilder, Aware.AWARE_NOTIFICATION_IMPORTANCE_GENERAL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mBuilder.setChannelId(notificationChannelId)
            val accessibilitySettings = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilitySettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val clickIntent = PendingIntent.getActivity(c, 0, accessibilitySettings, PendingIntent.FLAG_UPDATE_CURRENT)
            mBuilder.setContentIntent(clickIntent)
            val notManager = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notManager.notify(42, mBuilder.build())
            return false
        }
        return true
    }

    companion object {
        const val TAG =  "EndlessService"
        var appTrackingList =  arrayOf<String>()
        var triggerDuration = HashMap<String, Long>()
        var stroopConfig = HashMap<String, Int>()
        var surveyConfig = HashMap<String, String>()
        lateinit var kronosClock: KronosClock
        val db = Firebase.firestore
    }
}
