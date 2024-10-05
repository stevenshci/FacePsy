package com.rahulislam.facepsy.flower

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.auth.FirebaseAuth
import com.rahulislam.facepsy.R
import java.lang.Boolean
import java.util.*
import java.util.concurrent.ExecutionException
import com.rahulislam.facepsy.MainActivity
import com.rahulislam.facepsy.processing.EndlessService


class Flower4x4Activity : AppCompatActivity(), View.OnClickListener {
    private val TAG = "FlowerTask4x4"
    var buttons = arrayOfNulls<ImageButton>(16)
    val btnIdRef = HashMap<Int, Int>()
    var mNext4x4: Button? = null
    var mTextView: TextView? = null
    var mActivity: Activity? = null
    var timeA: Long = 0
    var timeB:kotlin.Long = 0
    var difference:kotlin.Long = 0
    lateinit var diff: LongArray
    private lateinit var tappedSeq: IntArray

    private var gameId = ""
    private var no_of_glows = 0
    private var correct = 0
    private var incorrect = 0
    private var count = 0
    private var dCount = 0
    var inputTime: List<Long>? = null
    var flowerData: List<List<List<Long>>>? = null
    private lateinit var arr: IntArray


    var unixTime: Long? = null

    var startGlowTime: Long? = null
    var endGlowTime: Long? = null
    private lateinit var tapSeqTime: LongArray

    var flowerClickable:kotlin.Boolean = Boolean.FALSE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flower4x4)

        // Initialize the Couchbase Lite system

        mNext4x4 = findViewById(R.id.next4x4)
        mTextView = findViewById(R.id.textView)

        Log.d(TAG, "OnCreate")
        for (i in 0..15) {
            val buttonID = "button_$i"
            val resID = resources.getIdentifier(buttonID, "id", packageName)
            buttons[i] = findViewById(resID)
            buttons[i]?.setOnClickListener(this)

            btnIdRef[resID] = i
        }

        gameId = intent.extras.getString("gameId")
        no_of_glows = intent.extras.getInt("glows")
        correct = intent.extras.getInt("correct")
        incorrect = intent.extras.getInt("incorrect")
        unixTime = intent.extras.getLong("unix")
//        flowerData =getIntent().getExtras().getParcelable("flowereData");
//        flowerData = getIntent().getParcelableArrayListExtra().get("flowerData");
        //        flowerData =getIntent().getExtras().getParcelable("flowereData");
//        flowerData = getIntent().getParcelableArrayListExtra().get("flowerData");
        Log.d(TAG, "getExtra flowerData: $flowerData")
        mActivity = this

        //First time launch of activity
        //newGame(no_of_glows);

        //First time launch of activity
        //newGame(no_of_glows);
        mNext4x4?.setVisibility(View.VISIBLE)
        mTextView?.setText("correct, tap next to continue")
//        timeA = System.currentTimeMillis();
    }

    private fun randomSequence(n: Int): IntArray {
        val arr = IntArray(n)
        var i = 0
        while (i < arr.size) {

            //arr[i] = (int)(Math.random()*10);//note, this generates numbers from [0,9]
            //(int)(Math.random() * ((max - min) + 1)) + min; --> range[min,max]
            arr[i] = (Math.random() * (16 + 1)).toInt() //note, this generates numbers from [0,limit]
            if (arr[i] == 16) {
                i--
                i++
                continue
            }
            for (j in 0 until i) {
                if (arr[i] == arr[j] || arr[j] == 16) {
                    i-- //if a[i] is a duplicate of a[j], then run the outer loop on i again
                    break
                }
            }
            i++
        }
        return arr
    }

    fun newGame(view: View?) {
        mNext4x4?.setVisibility(View.INVISIBLE)
        mTextView?.setText("watch the flowers light up")
        count = 0
        dCount = 0
        //Set blank flowers
        for (i in 0..15) {
            buttons.get(i)?.setImageResource(R.drawable.flower_blank)
            buttons.get(i)?.setTag(false)
        }

        //Generate random sequence
        arr = randomSequence(no_of_glows)
        diff = LongArray(no_of_glows)
        inputTime = ArrayList<Long>()
        tappedSeq = IntArray(no_of_glows) { -1 }
        tapSeqTime = LongArray(no_of_glows) { -1 }

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

        Flower3x3Activity.db.collection("flowerGameData")
                .add(gameData)
                .addOnSuccessListener { documentReference ->
                    Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error adding document", e)
                }
    }

    override fun onClick(v: View) {
        if (flowerClickable == Boolean.FALSE) return

        timeB = System.currentTimeMillis()
        difference = timeB - timeA
        timeA = timeB
        diff[dCount] = difference
        dCount++
//        inputTime.add(difference)
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
                //When all the answers are correct i.e. count = 5
                if (count == no_of_glows) {
                    correct++

                    Log.d(TAG, "Yippe : You win!")
                    Toast.makeText(this, "Yippe : You win!", Toast.LENGTH_SHORT).show()
                    saveGameData(4, Boolean.TRUE)
                    if (correct == 5 || incorrect == 2) {
//                        val intent = Intent(this@FlowerTask4x4, QuestionnaireActivity::class.java)
//                        intent.putExtra("unix", unixTime)
//                        startActivity(intent)
                        finish()
                    }
                    if (no_of_glows != 7) {
                        no_of_glows++
                        //                        SystemClock.sleep(2000);//2 seconds sleep
                        //newGame(no_of_glows);
                        mNext4x4!!.visibility = View.VISIBLE
                        mTextView!!.text = "correct, tap next to continue"
                    }
                }
            } else {
                incorrect++
//                createDocument()
                saveGameData(4, Boolean.FALSE)
                Log.d(TAG, "OnClick : Wrong!$incorrect")
                val b = v.findViewById<ImageButton>(v.id)
                b.setImageResource(R.drawable.flower_red_cross)
                if (correct == 5 || incorrect == 2) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("unix", unixTime)
                    startActivity(intent)
                    finish()
                    return

                }

                //restart game on 3x3 fragment

//                            Toast.makeText(this, "Wrong!",Toast.LENGTH_LONG).show();
                //newGame();
                Log.d(TAG, "no_of_glows: $no_of_glows")
                if (no_of_glows == 5) {
                    no_of_glows--
                    SystemClock.sleep(2000) //2 seconds sleep
                    val intent = Intent(this, Flower3x3Activity::class.java)
                    intent.putExtra("gameId", gameId)
                    intent.putExtra("glows", no_of_glows)
                    intent.putExtra("correct", correct)
                    intent.putExtra("incorrect", incorrect)
                    intent.putExtra("flowerData", flowerData as Parcelable?)
                    intent.putExtra("flag", 1)
                    intent.putExtra("unix", unixTime)
                    startActivity(intent)
                    finish()
                    /*                    ((CognitiveTaskActivity) Objects.requireNonNull(getActivity())).setCorrect_response(correct);
                    ((CognitiveTaskActivity) Objects.requireNonNull(getActivity())).setIncorrect_response(incorrect);
                    ((CognitiveTaskActivity) Objects.requireNonNull(getActivity())).setNo_of_glows(no_of_glows);
                    ((CognitiveTaskActivity) Objects.requireNonNull(getActivity())).setViewPager(0);*/
                } else {
                    no_of_glows--
                    //                    SystemClock.sleep(2000);//2 seconds sleep
//                    newGame(no_of_glows);
                    mNext4x4!!.visibility = View.VISIBLE
                    mTextView!!.text = "try again, tap next to continue"
                }
            }
        } else {
            Log.d("Fragment4x4", "OnClick : Wrong, Clicked before!")
            val b = v.findViewById<ImageButton>(v.id)
            b.setImageResource(R.drawable.flower_red_cross)
            //restart game on 3x3 fragment


//                        Toast.makeText(this, "Wrong!",Toast.LENGTH_LONG).show();
            //newGame();
        }
    }

}