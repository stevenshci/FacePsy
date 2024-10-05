package com.rahulislam.facepsy.flower

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.rahulislam.facepsy.R
import com.rahulislam.facepsy.processing.EndlessService
import com.rahulislam.facepsy.processing.ImageProcessingWorker
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Boolean
import java.util.*

class Flower3x3Activity : AppCompatActivity(), View.OnClickListener {

    private val TAG = "FlowerTask3x3"
    var buttons = arrayOfNulls<ImageButton>(9)
    val btnIdRef = HashMap<Int, Int>()
    var mNext3x3: Button? = null
    var mTextView: TextView? = null
    var mActivity: Activity? = null
    var timeA: Long = 0
    var timeB:kotlin.Long = 0
    var difference:kotlin.Long = 0
    lateinit var diff: LongArray
    var tapSeq = JSONArray()
    private lateinit var tappedSeq: IntArray
//    var result = 0
    private var gameId = ""
    private var no_of_glows = 0
    private var correct = 0
    private var incorrect = 0
    private var count = 0
    private var dCount = 0
    private lateinit var arr: IntArray
    var unixTime: Long? = null

    var startGlowTime: Long? = null
    var endGlowTime: Long? = null
    private lateinit var tapSeqTime: LongArray

    var flowerClickable:kotlin.Boolean = Boolean.FALSE


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flower3x3)

        for (i in 0..8) {
            val buttonID = "button_$i"
            val resID = resources.getIdentifier(buttonID, "id", packageName)
            Log.i(TAG, resID.toString())
            buttons[i] = findViewById(resID)
            buttons[i]?.setOnClickListener(this)

            btnIdRef[resID] = i
        }
        mNext3x3 = findViewById(R.id.next3x3)
        mTextView = findViewById(R.id.textView)

        unixTime = intent.getLongExtra("unix", -1)
        val flag = intent.getIntExtra("flag", -1)

        //PreTaskQuestion --> FlowerTask3x3

        //PreTaskQuestion --> FlowerTask3x3
        if (flag == -1) {
            gameId = UUID.randomUUID().toString();
            no_of_glows = 3
            correct = 0
            incorrect = 0
            mActivity = this
            mNext3x3?.setText("start")
            mNext3x3?.setVisibility(View.VISIBLE)
            mTextView?.setText("tap start to begin the task")
        } else {
            gameId = intent.extras.getString("gameId")
            no_of_glows = intent.extras.getInt("glows")
            correct = intent.extras.getInt("correct")
            incorrect = intent.extras.getInt("incorrect")
            mNext3x3?.setVisibility(View.VISIBLE)
            mTextView?.setText("try again, tap next to continue")

            flowerClickable = Boolean.TRUE
        }

        val intent = Intent()
        intent.action = "com.rahulislam.facepsy.triggers"
        intent.putExtra("packageName", "flowerGame")
        intent.putExtra("duration", EndlessService.triggerDuration["flowerGame"].toString())
        intent.putExtra("gameId", gameId)
        this.sendBroadcast(intent)
    }

    private fun randomSequence(n: Int): IntArray {
        val arr = IntArray(n)
        var i = 0
        while (i < arr.size) {

            //arr[i] = (int)(Math.random()*10);//note, this generates numbers from [0,9]
            //(int)(Math.random() * ((max - min) + 1)) + min; --> range[min,max]
            arr[i] = (Math.random() * (9 + 1)).toInt() //note, this generates numbers from [0,limit]
            if (arr[i] == 9) {
                i--
                i++
                continue
            }
            for (j in 0 until i) {
                if (arr[i] == arr[j] || arr[j] == 9) {
                    i-- //if a[i] is a duplicate of a[j], then run the outer loop on i again
                    break
                }
            }
            i++
        }
        return arr
    }

    fun newGame(view: View?) {
        flowerClickable = Boolean.FALSE
        mNext3x3?.setText("next")
        mNext3x3?.setVisibility(View.INVISIBLE)
        mTextView?.setText("watch the flowers light up")
        count = 0
        dCount = 0
        //Set blank flowers
        for (i in 0..8) {
            buttons.get(i)?.setImageResource(R.drawable.flower_blank)
            buttons.get(i)?.setTag(false)
        }

        //Generate random sequence
        arr = randomSequence(no_of_glows)
        diff = LongArray(no_of_glows)
        tappedSeq = IntArray(no_of_glows) { -1 }
        tapSeqTime = LongArray(no_of_glows) { -1 }

//        result = -1

        //thread to blink flowers for the game
        startGlowTime = System.currentTimeMillis()
        Thread {
            var i = 0
            //always run background
            while (i < arr.size) {
                SystemClock.sleep(1000) //1 second sleep
                if (mActivity == null) {
                    mActivity = this
                }

                //update Android UI on Main Thread
                val finalI: Int = arr.get(i)
                mActivity!!.runOnUiThread(Runnable {
                    Log.d(TAG, "finalI : $finalI")
                    buttons.get(finalI)?.setImageResource(R.drawable.flower_blue)
                })
                SystemClock.sleep(1000) //1 second sleep
                mActivity!!.runOnUiThread(Runnable {
                    Log.d(TAG, "finalI : $finalI")
                    buttons.get(finalI)?.setImageResource(R.drawable.flower_blank)
                })
                i++
            }
            mTextView?.post(Runnable { mTextView!!.setText("tap in the previous sequence") })
            timeA = System.currentTimeMillis()
            endGlowTime = timeA
            flowerClickable = Boolean.TRUE
        }.start() //start thread


//        timeA = System.currentTimeMillis();
    }

    fun saveGameData(num_span: Int, status: kotlin.Boolean) {
        flowerClickable = Boolean.FALSE
        val gameData = hashMapOf(
                "user_id" to FirebaseAuth.getInstance().currentUser?.uid,
                "game_id" to gameId,
                "complexity" to no_of_glows,
                "num_span" to num_span,
                "status" to status,
                "time_diff" to diff.toList(),
                "target_seq" to arr.toList(),
                "tapped_seq" to tappedSeq.toList(),
                "startGlowTime" to startGlowTime,
                "endGlowTime" to endGlowTime,
                "tapSeqTime" to tapSeqTime.toList(),
                "timestamp" to EndlessService.kronosClock.getCurrentTimeMs()
        )

        db.collection("flowerGameData")
                .add(gameData)
                .addOnSuccessListener { documentReference ->
                    Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error adding document", e)
                }
    }

    //    @Override
    //    public void onBackPressed() { }
    override fun onClick(v: View) {
        if (flowerClickable == Boolean.FALSE) return

        timeB = System.currentTimeMillis()
        difference = timeB - timeA
        timeA = timeB
        diff[dCount] = difference
        dCount++
        Log.d(TAG, "Difference : $difference")

        tappedSeq[count] = btnIdRef[v.id]!!
        tapSeqTime[count] = timeB

        //When the button is not clicked before i.e. Tag = False
        if (v.tag.toString() == "false") {
            v.tag = Boolean.TRUE
            //if the button pressed is correct then replace with flower_blue_tick
            if (buttons[arr[count]]!!.id == v.id) {
                Log.d(TAG, "OnClick : Correct!")
                //SystemClock.sleep(1000);
                buttons[arr[count]]!!.setImageResource(R.drawable.flower_blue_tick)
                count++
                //When all the answers are correct i.e. count = no_of_glows
                if (count == no_of_glows) {
                    correct++
//                    result = 1

                    Log.d(TAG, "Yippe : You win!")
                    Toast.makeText(this, "Yippe : You win!", Toast.LENGTH_SHORT).show()
                    saveGameData(3, Boolean.TRUE)
                    if (correct == 5 || incorrect == 2) {
//                        val intent = Intent(this@FlowerTask3x3, QuestionnaireActivity::class.java)
//                        intent.putExtra("unix", unixTime)
//                        startActivity(intent)
                        finish()
                    }
                    if (no_of_glows == 4) {
                        //Start FlowerTask4x4 and newgame
                        no_of_glows++
                        val intent = Intent(this, Flower4x4Activity::class.java)
                        intent.putExtra("gameId", gameId)
                        intent.putExtra("glows", no_of_glows)
                        intent.putExtra("correct", correct)
                        intent.putExtra("incorrect", incorrect)
                        intent.putExtra("unix", unixTime)
                        intent.putExtra("flag", 1)
                        startActivity(intent)
                        finish()

                        /*
                        ((CognitiveTaskActivity) Objects.requireNonNull(getActivity())).setCorrect_response(correct);
                        ((CognitiveTaskActivity) Objects.requireNonNull(getActivity())).setIncorrect_response(incorrect);
                        ((CognitiveTaskActivity) Objects.requireNonNull(getActivity())).setNo_of_glows(no_of_glows);
                        ((CognitiveTaskActivity) Objects.requireNonNull(getActivity())).setViewPager(1);
*/
                    } else {
                        no_of_glows++
                        //                        SystemClock.sleep(2000);//2 seconds sleep
//                        newGame();
                        mNext3x3!!.visibility = View.VISIBLE
                        mTextView!!.text = "correct, tap next to continue"
                    }
                }
            } else {
                incorrect++
//                result = 0
                saveGameData(3, Boolean.FALSE)
                Log.d(TAG, "OnClick : Wrong!")
                val b = v.findViewById<ImageButton>(v.id)
                b.setImageResource(R.drawable.flower_red_cross)
                if (correct == 5 || incorrect == 2) {
//                    val intent = Intent(this@FlowerTask3x3, QuestionnaireActivity::class.java)
//                    intent.putExtra("unix", unixTime)
//                    startActivity(intent)
                    finish()
                }

                //restart game on 3x3 fragment
                if (no_of_glows == 3) {
//                    SystemClock.sleep(2000);//2 seconds sleep
//                    newGame();
                    mNext3x3!!.visibility = View.VISIBLE
                    mTextView!!.text = "try again, tap next to continue"
                } else {
                    no_of_glows--
                    //                    SystemClock.sleep(2000);//2 seconds sleep
//                    newGame();
                    mNext3x3!!.visibility = View.VISIBLE
                    mTextView!!.text = "try again, tap next to continue"
                }

//                            Toast.makeText(this, "Wrong!",Toast.LENGTH_LONG).show();
                //newGame();
            }
        } else {
            Log.d(TAG, "OnClick : Wrong, Clicked before!")
            val b = v.findViewById<ImageButton>(v.id)
            b.setImageResource(R.drawable.flower_red_cross)
            //restart game on 3x3 fragment


//                        Toast.makeText(this, "Wrong!",Toast.LENGTH_LONG).show();
            //newGame();
        }
    }

    companion object{
//        const val TAG = "ImageProcessingWorker"
        val db = Firebase.firestore
    }

}

