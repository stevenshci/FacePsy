//package com.rahulislam.facepsy
//
//class ImageProcessingWorker {
//}

package com.rahulislam.facepsy.processing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.os.Environment

import android.graphics.Bitmap
import android.os.Environment.getExternalStoragePublicDirectory
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import org.checkerframework.checker.units.qual.s





class ImageProcessingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override val coroutineContext = Dispatchers.IO

    override suspend fun doWork(): Result = coroutineScope {

        val imageUriInput =
                inputData.getString("IMAGE_URI") ?: return@coroutineScope Result.failure()
        val timestamp =
                inputData.getString("TIMESTAMP") ?: return@coroutineScope Result.failure()
        val seq_id =
                inputData.getString("SEQ_ID") ?: return@coroutineScope Result.failure()
        val gameId =
                inputData.getString("GAME_ID") ?: return@coroutineScope Result.failure()
        val triggerName =
                inputData.getString("TRIGGER_NAME") ?: return@coroutineScope Result.failure()

        val resultJsonArray: JSONArray
        try {
            val jobs = async {
                    extractFeatures(imageUriInput)
                }
            // awaitAll will throw an exception if a download fails, which CoroutineWorker will treat as a failure
            // Do something with the URL
            resultJsonArray = jobs.await()

            for (i in 0 until resultJsonArray.length()) {
                val jsonObject = resultJsonArray.getJSONObject(i)
                val retMap: HashMap<String, Any> = Gson().fromJson(
                        jsonObject.toString(), object : TypeToken<HashMap<String?, Any?>?>() {}.type
                )

                val metadataJson = JSONObject()
                metadataJson.put("timestamp", timestamp)
                metadataJson.put("seq_id", seq_id)
                metadataJson.put("gameId", gameId)
                metadataJson.put("triggerName", triggerName)
                metadataJson.put("appVersion", applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName)


                retMap["metadata"] = Gson().fromJson(
                        metadataJson.toString(), object : TypeToken<HashMap<String?, Any?>?>() {}.type
                )
                retMap["timestamp"] = timestamp
                retMap["gameId"] = gameId
                FirebaseAuth.getInstance().currentUser?.uid?.let { retMap.put("user_id", it) }

                // Add a new document with a generated ID
                db.collection("features")
                        .add(retMap)
                        .addOnSuccessListener { documentReference ->
                            Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Error adding document", e)
                        }
            }

        } catch (e: Exception) {
            if (runAttemptCount <3) {

                Result.retry()
            } else {
                Result.failure()
            }
        }

        Result.success()
    }

    fun toGrayscale(bmpOriginal: Bitmap): Bitmap? {
        val height: Int = bmpOriginal.height
        val width: Int = bmpOriginal.width
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmpGrayscale)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val f = ColorMatrixColorFilter(cm)
        paint.colorFilter = f
        c.drawBitmap(bmpOriginal, 0f, 0f, paint)
        return bmpGrayscale
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer? {
        var inputSize = 200
        var IMAGE_MEAN = 128;
        var IMAGE_STD = 128.0f;
        val byteBuffer = ByteBuffer.allocateDirect(4 * 1 * inputSize * inputSize * 1)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val `val` = intValues[pixel++]
                byteBuffer.putFloat(((`val` shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            }
        }
        return byteBuffer
    }



    @SuppressLint("LongLogTag")
    private suspend fun extractFeatures(imageUriInput: String): JSONArray {
        // Do the work here--in this case, upload the images.
        val pathSplit: List<String> = imageUriInput.split("/")
        val imageFileName = pathSplit[pathSplit.size - 1]
        Log.i(TAG, imageFileName)


        val highAccuracyOpts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()

        val detector = FaceDetection.getClient(highAccuracyOpts)

        Log.d(TAG, imageUriInput)
        var image: InputImage = InputImage.fromFilePath(this.applicationContext, Uri.fromFile(File(imageUriInput)))

        var bitmapGray = image.bitmapInternal?.let { toGrayscale(it) }

        val jsonArray = JSONArray()
        val result = detector.process(image)

        try {
            Tasks.await(result)
            for (face in result.result!!) {
//                                    Native.analiseFrame(image.byteBuffer?.array(), image.rotationDegrees, image.width, image.height, face.boundingBox)

//                                    if (graphicOverlay != null) {
//                                        graphicOverlay.add(FaceGraphic(graphicOverlay, face))
//                                    }

                var cropFace:Bitmap
                val matrix = Matrix()
                val auObj = JSONObject()
                if (bitmapGray != null) {
                    Log.i(TAG, bitmapGray.width.toString() + " " + bitmapGray.height.toString() +" "+ (face.boundingBox.left + face.boundingBox.width()).toString() + " " + face.boundingBox.top.toString() + " " +  face.boundingBox.width().toString() + " " + face.boundingBox.height().toString())
                    if ((face.boundingBox.top + face.boundingBox.height() <= bitmapGray.height) && (face.boundingBox.left + face.boundingBox.width() <= bitmapGray.width)) {
                        cropFace = Bitmap.createBitmap(bitmapGray, face.boundingBox.left, face.boundingBox.top, face.boundingBox.width(), face.boundingBox.height(), matrix, true)
//                        try {
////                            if (cropFace != null) {
////                                FileOutputStream(File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/Inference/lol.png")).use { out ->
////                                    cropFace.compress(Bitmap.CompressFormat.PNG, 100, out) // bmp is your Bitmap instance
////                                }
////                            }
////                        } catch (e: IOException) {
////                            e.printStackTrace()
////                        }


                        val scaledBitmap = Bitmap.createScaledBitmap(cropFace, 200, 200, true)
                        val imageProcessor: ImageProcessor = ImageProcessor.Builder()
//                                .add(ResizeOp(200, 200, ResizeOp.ResizeMethod.BILINEAR))
                                .build()

                        var tImage = convertBitmapToByteBuffer(scaledBitmap)

                        val probabilityBuffer: TensorBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 12), DataType.FLOAT32)

                        // Initialise the model
                        var tflite:Interpreter
                        try{
                            var tfliteModel = FileUtil.loadMappedFile(applicationContext, "AU_200.tflite")
                            tflite =  Interpreter(tfliteModel)

                            // Running inference
                            if(null != tflite) {
                                tflite.run(tImage , probabilityBuffer.getBuffer());

                                arrayOf("AU01", "AU02", "AU04", "AU06", "AU07", "AU10", "AU12", "AU14", "AU15", "AU17", "AU23", "AU24")
                                        .forEachIndexed { index, auName ->
                                            auObj.put(auName, probabilityBuffer.getFloatValue(index))
                                        }
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                            Log.e("tfliteSupport", "Error reading model", e);
                        }
                    }
                }

                // Save Left Eye Region
                var eyeContour = face.getContour(FaceContour.LEFT_EYE)?.points

                var l_left = eyeContour?.get(0)!!.x.toInt() - 20
                var l_top = eyeContour?.get(4)!!.y.toInt() - 20
                var l_width = eyeContour?.get(8)!!.x.toInt()?.minus(eyeContour?.get(0)!!.x.toInt()!!) + 40
                var l_height = eyeContour?.get(12)!!.y.toInt()?.minus(eyeContour?.get(4)!!.y.toInt()!!) + 40

                cropFace = Bitmap.createBitmap(image.bitmapInternal, l_left, l_top, l_width, l_height, matrix, true)
//                saveTempBitmap(cropFace)

                var uid = FirebaseAuth.getInstance().currentUser?.uid.toString()

                val i: Int = imageFileName.lastIndexOf(".")
                val imageFileNamePNG = arrayOf<String>(imageFileName.substring(0, i), imageFileName.substring(i+1))[0]

                var storage = FirebaseStorage.getInstance()

                // Create a storage reference from our app
                var storageRef = storage.reference

                // Create a reference to "mountains.jpg"
                var eyeRegionImg = storageRef.child("eyeRegion/$uid/$imageFileNamePNG"+"_LEFT.png")

                var baos = ByteArrayOutputStream()
                cropFace.compress(Bitmap.CompressFormat.PNG, 100, baos)
                var data = baos.toByteArray()

//                val ur = Uri.fromFile(File(imageUriInput))
//                var uploadTask = eyeRegionImg.putFile(ur)
                var uploadTask = eyeRegionImg.putBytes(data)
                uploadTask.addOnFailureListener {
                    // Handle unsuccessful uploads
                    Log.i(TAG, "FAIL")
                }.addOnSuccessListener { taskSnapshot ->
                    // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
                    // ...
                    Log.i(TAG, FirebaseAuth.getInstance().currentUser?.uid.toString())
                }

                // Save Right Eye Region
                eyeContour = face.getContour(FaceContour.RIGHT_EYE)?.points

                l_left = eyeContour?.get(0)!!.x.toInt() - 20
                l_top = eyeContour?.get(4)!!.y.toInt() - 20
                l_width = eyeContour?.get(8)!!.x.toInt()?.minus(eyeContour?.get(0)!!.x.toInt()!!) + 40
                l_height = eyeContour?.get(12)!!.y.toInt()?.minus(eyeContour?.get(4)!!.y.toInt()!!) + 40

                cropFace = Bitmap.createBitmap(image.bitmapInternal, l_left, l_top, l_width, l_height, matrix, true)
//                saveTempBitmap(cropFace)

                uid = FirebaseAuth.getInstance().currentUser?.uid.toString()

                storage = FirebaseStorage.getInstance()

                // Create a storage reference from our app
                storageRef = storage.reference

                // Create a reference to "mountains.jpg"
                eyeRegionImg = storageRef.child("eyeRegion/$uid/$imageFileNamePNG"+"_RIGHT.png")

                baos = ByteArrayOutputStream()
                cropFace.compress(Bitmap.CompressFormat.PNG, 100, baos)
                data = baos.toByteArray()

//                val ur = Uri.fromFile(File(imageUriInput))
//                var uploadTask = eyeRegionImg.putFile(ur)
                uploadTask = eyeRegionImg.putBytes(data)
                uploadTask.addOnFailureListener {
                    // Handle unsuccessful uploads
                    Log.i(TAG, "FAIL")
                }.addOnSuccessListener { taskSnapshot ->
                    // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
                    // ...
                    Log.i(TAG, FirebaseAuth.getInstance().currentUser?.uid.toString())
                }
                // End

                val jsonFace = JSONObject()

                val jsonLandMarksArray = JSONArray()
                for (landmark in face.allLandmarks){
                    val jsonLandMarkObj = JSONObject()
                    jsonLandMarkObj.put("type", landmark.landmarkType);
                    jsonLandMarkObj.put("x", landmark.position.x)
                    jsonLandMarkObj.put("y", landmark.position.y)
                    jsonLandMarksArray.put(jsonLandMarkObj)
                }

                val jsonContoursArray = JSONArray()
                for (contour in face.allContours){

                    for (point in contour.points) {
                        val jsonContoursObj = JSONObject()
                        jsonContoursObj.put("x", point.x)
                        jsonContoursObj.put("y", point.y)
                        jsonContoursArray.put(jsonContoursObj)
                    }
                }

                Log.d(TAG, jsonContoursArray.toString())


                val jsonHeadEulerObj = JSONObject()
                jsonHeadEulerObj.put("X", face.headEulerAngleX)
                jsonHeadEulerObj.put("Y", face.headEulerAngleY) // Head is rotated to the right rotY degrees
                jsonHeadEulerObj.put("Z", face.headEulerAngleZ) // Head is tilted sideways rotZ degrees

                val jsonClassificationObj = JSONObject()
                jsonClassificationObj.put("leftEyeOpenProbability", face.leftEyeOpenProbability)
                jsonClassificationObj.put("rightEyeOpenProbability", face.rightEyeOpenProbability) // Head is rotated to the right rotY degrees
                jsonClassificationObj.put("smilingProbability", face.smilingProbability) // Head is tilted sideways rotZ degrees

                jsonFace.put("fileName", imageUriInput);
                jsonFace.put("landmarks", jsonLandMarksArray)
                jsonFace.put("contours", jsonContoursArray)
                jsonFace.put("boundingBox", face.boundingBox.flattenToString())
                jsonFace.put("headEulerAngle", jsonHeadEulerObj)
                jsonFace.put("classification", jsonClassificationObj)
                jsonFace.put("au", auObj)

                Log.d(TAG, jsonArray.toString())
                jsonArray.put(jsonFace)

                val bounds = face.boundingBox
                val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
                val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees

                // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
                // nose available):
                val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
                leftEar?.let {
                    val leftEarPos = leftEar.position
                }

                // If contour detection was enabled:
//                val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
                val upperLipBottomContour = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points

                // If classification was enabled:
                if (face.smilingProbability != null) {
                    val smileProb = face.smilingProbability
                    Log.d(TAG, smileProb.toString())
                }
                if (face.rightEyeOpenProbability != null) {
                    val rightEyeOpenProb = face.rightEyeOpenProbability
                }

                // If face tracking was enabled:
                if (face.trackingId != null) {
                    val id = face.trackingId
                }

                // ************************************************************
                // Uncommet this code to save the face image to firebase storage
                // ************************************************************
                /*
                faceImg = storageRef.child("faceImages/$uid/$imageFileNamePNG.jpg")

                baos = ByteArrayOutputStream()
                image.bitmapInternal?.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                data = baos.toByteArray()

                val ur = Uri.fromFile(File(imageUriInput))
                uploadTask = faceImg.putFile(ur)

                uploadTask.addOnFailureListener {
                    // Handle unsuccessful uploads
                    Log.i(TAG, "FAIL")
                }.addOnSuccessListener { taskSnapshot ->
                    // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
                    // ...
                    Log.i(TAG, FirebaseAuth.getInstance().currentUser?.uid.toString())
                }
                */


            }

            val file = File(imageUriInput)
            file.delete()

            // Moves file to different folder
//            file.renameTo(File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/Inference/image/${file.name}"))

            Log.d(TAG, "Currently Processing\t" + imageUriInput)
        } catch (e: Exception) {
            val file = File(imageUriInput)
//            file.delete()
            throw e
        }

        return jsonArray
    }

    fun saveTempBitmap(bitmap: Bitmap) {
        if (isExternalStorageWritable()) {
            saveImage(bitmap)
        } else {
            //prompt the user or do something
        }
    }

    private fun saveImage(finalBitmap: Bitmap) {
//        val root = Environment.getExternalStorageDirectory().toString()
        val root = getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
        val myDir = File("$root/Inference/image")
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
        } catch (e: java.lang.Exception) {
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
    companion object{
        const val TAG = "ImageProcessingWorker"
        val db = Firebase.firestore
    }
}