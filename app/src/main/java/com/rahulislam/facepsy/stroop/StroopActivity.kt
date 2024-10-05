package com.rahulislam.facepsy.stroop

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.rahulislam.facepsy.MainActivity
import com.rahulislam.facepsy.R
import com.rahulislam.facepsy.flower.Flower3x3Activity
import com.rahulislam.facepsy.processing.EndlessService
import com.rahulislam.facepsy.utils.Model
import java.lang.Boolean
import java.util.*


lateinit var stroopColorTv: TextView
var stroopWord = ""
var stroopColor = ""
var stimulusShownAt: Long? = null
var stimulusRespondedAt: Long? = null
private var gameId = ""
var seq: Int = 0
var stroopResponse:String = ""

class StroopActivity : AppCompatActivity(), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stroop)

        stroopColorTv = findViewById<TextView>(R.id.stroopColorTv)
        val redbtn = findViewById<Button>(R.id.redBtn)
        val greenBtn = findViewById<Button>(R.id.greenBtn)
        val blueBtn = findViewById<Button>(R.id.blueBtn)
        val yellowBtn = findViewById<Button>(R.id.yellowBtn)

        redbtn.setOnClickListener(this)
        greenBtn.setOnClickListener(this)
        blueBtn.setOnClickListener(this)
        yellowBtn.setOnClickListener(this)

        gameId = UUID.randomUUID().toString();
        seq = 0

        newGame()
        Log.i("gameId", gameId)
        val intent = Intent()
        intent.action = "com.rahulislam.facepsy.triggers"
        intent.putExtra("packageName", "stroopTask")
        intent.putExtra("duration", EndlessService.triggerDuration["stroopTask"].toString())
        intent.putExtra("gameId", gameId)
        this.sendBroadcast(intent)
    }

    fun newGame(){
        val list = listOf("RED", "GREEN", "BLUE", "YELLOW")
        stroopWord = list.shuffled().find { true }!!
        stroopColor = list.shuffled().find { true }!!

        stroopColorTv.text = stroopWord
        stroopColorTv.setTextColor(ContextCompat.getColor(this, resources.getIdentifier(stroopColor, "color", getPackageName())));
        stimulusShownAt = System.currentTimeMillis()
    }

    fun saveGameData() {
        val gameData = hashMapOf(
                "user_id" to FirebaseAuth.getInstance().currentUser?.uid,
                "game_id" to gameId,
                "stroopWord" to stroopWord,
                "stroopColor" to stroopColor,
                "stroopResponse" to stroopResponse,
                "stimulusShownAt" to stimulusShownAt,
                "stimulusRespondedAt" to stimulusRespondedAt,
                "seq" to seq,
                "timestamp" to EndlessService.kronosClock.getCurrentTimeMs()
        )

        Flower3x3Activity.db.collection("stroopData")
                .add(gameData)
                .addOnSuccessListener { documentReference ->
                    Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error adding document", e)
                }
    }

    override fun onClick(v: View?) {

        stimulusRespondedAt = System.currentTimeMillis()

        when (v!!.id) {
            R.id.redBtn -> {
                stroopResponse = "RED"
            }
            R.id.greenBtn -> {
                stroopResponse = "GREEN"
            }
            R.id.blueBtn -> {
                stroopResponse = "BLUE"
            }
            R.id.yellowBtn -> {
                stroopResponse = "YELLOW"
            }
        }

        seq++
        if (seq < EndlessService.stroopConfig["rounds"]!!.toInt()) {
            saveGameData()
            newGame()
        } else {
            saveGameData()

            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        }

    }

    companion object {
        const val TAG = "StroopActivity"
    }
}

