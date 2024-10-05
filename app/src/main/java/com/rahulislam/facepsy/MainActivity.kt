package com.rahulislam.facepsy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cottacush.android.hiddencam.CaptureTimeFrequency
import com.cottacush.android.hiddencam.HiddenCam
import com.cottacush.android.hiddencam.OnImageCapturedListener
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.AuthUI.IdpConfig.EmailBuilder
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.rahulislam.facepsy.flower.Flower3x3Activity
import com.rahulislam.facepsy.processing.Actions
import com.rahulislam.facepsy.processing.EndlessService
import com.rahulislam.facepsy.processing.ServiceState
import com.rahulislam.facepsy.processing.getServiceState
import com.rahulislam.facepsy.stroop.StroopDescriptionActivity
import com.rahulislam.facepsy.utils.Downloader
import com.rahulislam.facepsy.utils.Model
import com.rahulislam.facepsy.utils.UserDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class MainActivity : AppCompatActivity(), OnImageCapturedListener {
//    private lateinit var navController: NavController
//    private lateinit var appBarConfiguration: AppBarConfiguration
    private val requiredPermissions =
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)

    private lateinit var hiddenCam: HiddenCam
    private lateinit var baseStorageFolder: File

    private val detectorActor = newDetectorActor()
    private val lock = ReentrantLock()
    private lateinit var modelDir: File
    private lateinit var modelsJson: File
    private var currentModelId = -1
    private var modelsFetched = false

    private val graphicOverlay: GraphicOverlay? = null

    lateinit var currentProcessingTv: TextView

    init {
        System.loadLibrary("native-lib")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkPermissions()) onPermissionsGranted()

        Thread.setDefaultUncaughtExceptionHandler(CustomUncaughtExpHandler(this))

        modelDir = getDir("models", Activity.MODE_PRIVATE)
        modelsJson = File(getDir("models", Activity.MODE_PRIVATE), "models.json")

        // handle display rotation (keep track of the current loaded model)
        if (savedInstanceState != null) {
            currentModelId = savedInstanceState.getInt(model_id, -1)
        }

//        if (!modelsJson.exists()) {
//            fetchModels(::loadModelsFromJson)
//
//        } else
//        {
//            // ..warn user about eventual new models and updates
//            loadModelsFromJson(modelsJson.readText())  // load models from (stored) models.json
//
//            val updates = ArrayList<Model>()
//            var newModels = 0
//
//            UserDialog(this,
//                    title = "Check for new models?",
//                    msg = "this will check if there are new models or updates",
//                    onPositive = {
//                        fetchModels { json ->
//                            val jarray = JSONArray(json)
//                            val size = jarray.length()
//
//                            for (i in 0 until size) {
//                                val obj  = jarray.getJSONObject(i)
//                                val mod1 = Model.fromJsonObject(obj)
//                                val mod2 = models.getOrNull(mod1.id)
//
//                                // keep track of new models and models updates
//                                if (mod2 == null) {
//                                    newModels++
//
//                                } else if (mod1.version > mod2.version) {
//                                    updates.add(mod1)
//                                }
//                            }
//
//                            // warn user
//                            if (updates.size > 0) {
//                                UserDialog(this,
//                                        title = "Fetch summary:",
//                                        msg = "There are $newModels new models and $updates updates.\nDo you want to download the updated models?",
//                                        positiveLabel = "Update",
//                                        negativeLabel = "Close",
//                                        onPositive = {
//                                            updates.forEach { it ->
//                                                Downloader(this,
//                                                        title = "Update model ${it.name}",
//                                                        destination = File(modelDir, it.file),
//                                                        onError = {}   //TODO: show error message
//                                                ).start(it.url)
//                                            }
//                                        }
//                                ).show()
//                            }
//                        }
//                    }
//            ).show()
//        }
//
//        GlobalScope.launch(Dispatchers.Main){
//            loadModel(4)
//        }

        // Update TextView
        var avaForProcessTv: TextView = findViewById<TextView>(R.id.avaForProcessTv)

        val thread: Thread = object : Thread() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                try {
                    while (!this.isInterrupted) {
                        sleep(1000)
                        runOnUiThread {
                            val dcim = getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/HiddenCam")
                            if (dcim.listFiles() != null) {
                                val pics = dcim.listFiles().size
                                avaForProcessTv.text = "Number of Images: $pics";

                            }
                        }
                    }
                } catch (e: InterruptedException) {
                }
            }
        }

        thread.start()

        // Start button
        var startBtn: Button = findViewById(R.id.startBtn)
        currentProcessingTv = findViewById(R.id.currentProcessingTv)

        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Inference").apply {
//                if (exists()) deleteRecursively()
            if (!exists()) mkdir()
        }
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Inference"), "image").apply {
//                if (exists()) deleteRecursively()
            if (!exists()) mkdir()
        }
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Inference"), "features").apply {
//                if (exists()) deleteRecursively()
            if (!exists()) mkdir()
        }

        startBtn.setOnClickListener {
            crashMe()
//            val highAccuracyOpts = FaceDetectorOptions.Builder()
//                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
//                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//                    .build()
//
//            var image: InputImage
//            val detector = FaceDetection.getClient(highAccuracyOpts)
//
//            // Get list of all the images in the DCIM/HiddenCam folder
//            val dcim = getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/HiddenCam")
//            if (dcim != null) {
//                val pics = dcim.listFiles()
//                if (pics != null) {
//                    for (pic in pics) {
//                        Log.d(TAG, pic.path)
//                        image = InputImage.fromFilePath(this, Uri.fromFile(File(pic.path)))
//
//                        val jsonArray = JSONArray()
//                        val result = detector.process(image)
//                                .addOnSuccessListener { faces ->
//                                    // Task completed successfully
//                                    // ...
//                                    for (face in faces) {
////                                    Native.analiseFrame(image.byteBuffer?.array(), image.rotationDegrees, image.width, image.height, face.boundingBox)
//
////                                    if (graphicOverlay != null) {
////                                        graphicOverlay.add(FaceGraphic(graphicOverlay, face))
////                                    }
//                                        Log.d(TAG, face.allContours.toString())
//
//                                        val jsonFace = JSONObject()
//
//                                        val jsonLandMarksArray = JSONArray()
//                                        for (landmark in face.allLandmarks){
//                                            val jsonLandMarkObj = JSONObject()
//                                            jsonLandMarkObj.put("type", landmark.landmarkType);
//                                            jsonLandMarkObj.put("x", landmark.position.x)
//                                            jsonLandMarkObj.put("y", landmark.position.y)
//                                            jsonLandMarksArray.put(jsonLandMarkObj)
//                                        }
//
//                                        val jsonContoursArray = JSONArray()
//                                        for (contour in face.allContours){
//
//                                            for (point in contour.points) {
//                                                val jsonContoursObj = JSONObject()
//                                                jsonContoursObj.put("x", point.x)
//                                                jsonContoursObj.put("y", point.y)
//                                                jsonContoursArray.put(jsonContoursObj)
//                                            }
//                                        }
//
//                                        Log.d(TAG, jsonContoursArray.toString())
//
//
//                                        val jsonHeadEulerObj = JSONObject()
//                                        jsonHeadEulerObj.put("X", face.headEulerAngleX)
//                                        jsonHeadEulerObj.put("Y", face.headEulerAngleY) // Head is rotated to the right rotY degrees
//                                        jsonHeadEulerObj.put("Z", face.headEulerAngleZ) // Head is tilted sideways rotZ degrees
//
//                                        val jsonClassificationObj = JSONObject()
//                                        jsonClassificationObj.put("leftEyeOpenProbability", face.leftEyeOpenProbability)
//                                        jsonClassificationObj.put("rightEyeOpenProbability", face.rightEyeOpenProbability) // Head is rotated to the right rotY degrees
//                                        jsonClassificationObj.put("smilingProbability", face.smilingProbability) // Head is tilted sideways rotZ degrees
//
//                                        jsonFace.put("fileName", pic.name);
//                                        jsonFace.put("landmarks", jsonLandMarksArray)
//                                        jsonFace.put("contours", jsonContoursArray)
//                                        jsonFace.put("boundingBox", face.boundingBox.flattenToString())
//                                        jsonFace.put("headEulerAngle", jsonHeadEulerObj)
//                                        jsonFace.put("classification", jsonClassificationObj)
//
//                                        Log.d(TAG, jsonArray.toString())
//                                        jsonArray.put(jsonFace)
//
//                                        val bounds = face.boundingBox
//                                        val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
//                                        val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees
//
//                                        // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
//                                        // nose available):
//                                        val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
//                                        leftEar?.let {
//                                            val leftEarPos = leftEar.position
//                                        }
//
//                                        // If contour detection was enabled:
//                                        val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
//                                        val upperLipBottomContour = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
//
//                                        // If classification was enabled:
//                                        if (face.smilingProbability != null) {
//                                            val smileProb = face.smilingProbability
//                                            Log.d(TAG, smileProb.toString())
//                                        }
//                                        if (face.rightEyeOpenProbability != null) {
//                                            val rightEyeOpenProb = face.rightEyeOpenProbability
//                                        }
//
//                                        // If face tracking was enabled:
//                                        if (face.trackingId != null) {
//                                            val id = face.trackingId
//                                        }
//                                    }
//
//                                    // Convert JsonObject to String Format
//                                    val userString: String = jsonArray.toString()
//                                    val file = File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) , "/Inference/features/${pic.name}.json")
//                                    val fileWriter = FileWriter(file)
//                                    val bufferedWriter = BufferedWriter(fileWriter)
//                                    bufferedWriter.write(userString)
//                                    bufferedWriter.close()
//                                    // END
//
//                                    // Moves file to different folder
////                                    pic.renameTo(File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/Inference/image/${pic.name}"))
//
//                                    Log.d(TAG, "Currently Processing\t" + pic.name)
//                                    currentProcessingTv.text = pic.name
//
//                                }
//                                .addOnFailureListener { e ->
//                                    // Task failed with an exception
//                                    // ...
//                                    Log.i(TAG, "Face Detection Failed")
//                                }
//
//
//                    }
//                }
//            }

        }


//        // HiddenCam Test
//        val screenactionreceiver: ScreenActionReceiver = ScreenActionReceiver()
//        registerReceiver(screenactionreceiver, screenactionreceiver.filter)
//
//        // Example
//        val featureExtractionReceiver: FeatureExtractionReceiver = FeatureExtractionReceiver()
//        registerReceiver(featureExtractionReceiver, featureExtractionReceiver.filter)

        val intent = Intent()
        intent.action = "com.rahulislam.broadcast.test"
        intent.putExtra("packageName", "currentApp")
        sendBroadcast(intent)

        // Login Code
        val auth = FirebaseAuth.getInstance()
        Log.i(TAG, auth.currentUser?.uid.toString())
        if(auth.currentUser == null) {
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(Arrays.asList(
                                    EmailBuilder().build()))
                            .build(),
                    RC_SIGN_IN)

            Log.i(TAG, auth.currentUser.toString())
        } else {
//            Intent(this, CaptureService::class.java).also { intent ->
//                startForegroundService(intent)
//            }

            actionOnService(Actions.START)
        }

        val btn_click_me = findViewById(R.id.launchFlowerTaskBtn) as Button
        // set on-click listener
        btn_click_me.setOnClickListener {
            val intent = Intent(this, Flower3x3Activity::class.java).apply {
            }
            startActivity(intent)
        }

        val launchStroopBtn = findViewById<Button>(R.id.launchStroopTaskBtn)
        launchStroopBtn.setOnClickListener{
            val intent = Intent(this, StroopDescriptionActivity::class.java).apply {  }
            startActivity(intent)
        }

        val launchPreSurveyBtn = findViewById<Button>(R.id.launchPreSurveyBtn)
        launchPreSurveyBtn.setOnClickListener{
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(
                    EndlessService.surveyConfig["preLink"] + "?userId=" + FirebaseAuth.getInstance().currentUser?.uid.toString()
            ))
            startActivity(browserIntent)
        }

        val launchPostSurveyBtn = findViewById<Button>(R.id.launchPostSurveyBtn)
        launchPostSurveyBtn.setOnClickListener{
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(
                    EndlessService.surveyConfig["postLink"] + "?userId=" + FirebaseAuth.getInstance().currentUser?.uid.toString()
            ))
            startActivity(browserIntent)
        }

        val launchInstructionBtn = findViewById<Button>(R.id.launchInstructionBtn)
        launchInstructionBtn.setOnClickListener{
            val intent = Intent(this, InstructionActivity::class.java).apply {  }
            startActivity(intent)
        }
    }

    private fun actionOnService(action: Actions) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(this, EndlessService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                log("Starting the service in >=26 Mode")
                startForegroundService(it)
                return
            }
            log("Starting the service in < 26 Mode")
            startService(it)
        }
    }

    fun crashMe() {
        throw NullPointerException()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                // Successfully signed in
                val user = FirebaseAuth.getInstance().currentUser
//                Intent(this, CaptureService::class.java).also { intent ->
//                    startForegroundService(intent)
//                }
                actionOnService(Actions.START)
                // ...
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
            }
        }
    }

    /** load a model from dlib */
    private suspend fun loadModel(id: Int): Boolean {
        var model: Model
        try{
            model = models[id] ?: return true
        } catch (e: Exception){
            print(e)
            return false
        }

        Log.i(TAG, "LOLlll\n\n\nllll")
        if (model.exists(modelDir)) {
            // try loading...
            if (currentModelId != id) {

                if (lock.isLocked) // already loading another model
                    return true

                if (model.isCorrupted(modelDir)) {
                    model.delete(modelDir)
                    return model.askToUser(this, modelDir,
                            "Model corrupted",
                            "Do you want to download the model again?") == Unit
                }

                GlobalScope.launch(Dispatchers.Main) {
//                    debugText.text = "Loading: ${model.name}..."
                    Log.d(TAG, "Loading: ${model.name}...")
                    val loaded = model.loadAsync(modelDir).await()
                    // clear drawings
//                    detectorActor.send(Pair(null, null))

                    Log.d(TAG, loaded.toString())
                    lock.withLock {
//                        val loaded = model.loadAsync(modelDir).await()
                        currentModelId = when (loaded) {
                            true -> {
//                                imageTaken = false;
                                id
                            } else -> {
                                model.askToUser(this@MainActivity, modelDir,
                                        "Something goes wrong :(",
                                        "Do you want to download the model again?")
                                -1
                            }
                        }
                    }

//                    debugText.text = "Loaded: ${model.name}"
                    Log.d(TAG, "Loaded: ${model.name}")
                    delay(2000L)
//                    debugText.text = ""
                }
            }

        } else {
            // try downloading...
            model.askToUser(this, modelDir, "Model not Found", "Do you want to download the model?")
        }

        return true
    }

    fun saveTempBitmap(bitmap: Bitmap) {
        if (isExternalStorageWritable()) {
            saveImage(bitmap)
        } else {
            //prompt the user or do something
        }
    }

    private fun saveImage(finalBitmap: Bitmap) {
        val root = getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
        val myDir = File("$root/saved_images")
        myDir.mkdirs()
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fname = "Shutta_$timeStamp.jpg"
        val file = File(myDir, fname)
        if (file.exists()) file.delete()
        try {
            val out = FileOutputStream(file)
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /* Checks if external storage is available for read and write */
    fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return if (Environment.MEDIA_MOUNTED == state) {
            true
        } else false
    }

    override fun onImageCaptured(image: File, packageName: String, metadata: JSONObject) {
        val message = "Image captured, saved to:${image.absolutePath}"
        Log.i(TAG, message)
        log(message)
        showToast(message)
    }

    override fun onImageCaptureError(e: Throwable?) {
        e?.run {
            val message = "Image captured failed:${e.message}"
            showToast(message)
            log(message)
            printStackTrace()
        }
    }

    private fun showToast(message: String) {
//        Toast.makeText(this@ScreenActionReceiver, message, Toast.LENGTH_LONG).show()
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        Log.d(TAG, message)
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    fun onPermissionsGranted() {
        Log.d("Landing", "permission granted")
//        recurringButton.isEnabled = true
//        oneShotButton.isEnabled = true

        baseStorageFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "HiddenCam").apply {
//                if (exists()) deleteRecursively()
            if (!exists()) mkdir()
        }
        hiddenCam = HiddenCam(
            applicationContext, baseStorageFolder, this,
            CaptureTimeFrequency.Recurring(RECURRING_INTERVAL),
            targetResolution = Size(1080, 1920)
        )

//        hiddenCam.start()
////        isCapturing = true
//        Timer("SettingUp", false).schedule(15000) {
//            hiddenCam.stop()
////            v?.vibrate(100)
////            isCapturing = false
//        }
    }

    /** define an Actor that sends the detected landarks to a channel, for later consuming */
    private fun newDetectorActor() = GlobalScope.actor<Detection> {
        for ((face, landmarks) in channel) {
            GlobalScope.launch(Dispatchers.Main) {
//                cameraOverlay.setFaceAndLandmarks(face, landmarks)
//                cameraOverlay.invalidate()
            }

//            currentFace = face

            // trigger auto-capture
//            if (!imageTaken && face != null && landmarks != null && landmarks.isNotEmpty()) {
//                models[currentModelId]?.let {
//                    cameraPreview.capture(data = frame, region = face)
//                    imageTaken = true
//                }
//            }
        }
    }
    private fun checkPermissions(): Boolean {
        return if (hasPermissions(requiredPermissions)) true
        else {
            requestPermissions(requiredPermissions, CAMERA_AND_STORAGE_PERMISSION_REQUEST_CODE)
            false
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        Log.d("Landing", "Permsion result called")
        if (requestCode == CAMERA_AND_STORAGE_PERMISSION_REQUEST_CODE &&
                confirmPermissionResults(grantResults)
        ) onPermissionsGranted()
    }

    private fun confirmPermissionResults(results: IntArray): Boolean {
        results.forEach {
            if (it != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    /** retrieve the models.json file from GitHub */
    private fun fetchModels(callback: (String) -> Unit = {}) {
        val dialog = UserDialog(this,
                title = "Error during fetching models",
                msg = "is the device connected to the internet?",
                onPositive = { fetchModels(callback) },
                positiveLabel = "Retry?",
                negativeLabel = "Close"
        )

        Downloader(this,
                title = "Fetching available models..",
                destination = modelsJson,
                onError = { dialog.show() },
                onSuccess = { callback(it.readText()) }
        ).start(json_url)
    }

    /** load the models from the content of a json file */
    private fun loadModelsFromJson(contents: String) {
        val jarray = JSONArray(contents)

        models.clear()

        // load model info stored inside models.json
        for (i in 0 until jarray.length()) {
            val obj = jarray.getJSONObject(i)
            models.add(Model.fromJsonObject(obj))
        }

        modelsFetched = true
    }
    companion object {

        const val RC_SIGN_IN = 123
        const val TAG = "MainActivity"
        const val RECURRING_INTERVAL = 1 * 100L

        val models = ArrayList<Model>()
        const val model_id = "MainActivity.model_id"
        const val CAMERA_AND_STORAGE_PERMISSION_REQUEST_CODE = 100
        const val json_url = "https://github.com/Luca96/dlib-minified-models/raw/master/face_landmarks/models.json"
    }
}

typealias Detection = Pair<Rect?, LongArray?>
typealias ModelPair = Pair<String, Model>