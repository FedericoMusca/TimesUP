package com.timesup

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.*
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import android.app.ActivityManager

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Timer-related variables and constants
    private lateinit var countDownTimer: CountDownTimer
    private var remainingTime : Long = TIMER_DURATION

    // Sensor-related variables
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var rotation: Int? = 0
    private var isFirstPortraitCall = false
    private var isFirstReverseCall = true

    // UI elements
    private lateinit var imgTopProgressBar : View
    private lateinit var imgBottomProgressBar : View
    private lateinit var imgTopSand : ImageView
    private lateinit var imgBottomSand: ImageView
    private lateinit var imgBlackMargin : ImageView
    private lateinit var btnPlay : Button
    private lateinit var tvTime : TextView

    //Service-related variables
    private lateinit var hourglassService: HourglassBoundService
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HourglassBoundService.HourglassBinder
            hourglassService = binder.service
            isBound = true

            //Retrieve timer parameters from the service
            hourglassService.setPreferences()
            hourglassService.stopTimer()
            getPreferences()

            if (rotation == Surface.ROTATION_180) {
                if (isFirstReverseCall) {
                    remainingTime = TIMER_DURATION - remainingTime
                    isFirstReverseCall = false
                    isFirstPortraitCall = true
                }
            } else if (rotation == Surface.ROTATION_0) {
                if (isFirstPortraitCall) {
                    remainingTime = TIMER_DURATION - remainingTime
                    isFirstPortraitCall = false
                    isFirstReverseCall = true
                }
            }

            // Create the notification channel
            hourglassService.createNotificationChannel()

            startCountdownTimer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e("MainActivity", "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize sensor manager and accelerometer sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Initialize UI elements
        imgTopProgressBar = findViewById(R.id.topProgressBar)
        imgBottomProgressBar = findViewById(R.id.bottomProgressBar)
        imgBottomSand = findViewById(R.id.img_bottomSand)
        imgTopSand = findViewById(R.id.img_topSand)
        imgBlackMargin = findViewById(R.id.img_blackMargin)
        btnPlay = findViewById(R.id.btn_play)
        tvTime = findViewById(R.id.tv_time)

        // Determine the screen rotation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = display
            rotation = display?.rotation
        } else {
            rotation = windowManager.defaultDisplay.rotation
        }

        // Request permission to post notifications for API >= 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }

        //Connection to the service
        val intent = Intent(this, HourglassBoundService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        // Button click listener to easily manipulate the hourglass
        btnPlay.setOnClickListener {
            when (btnPlay.text) {
                "Play" -> {
                    startCountdownTimer()
                }
                "Pause" -> {
                    countDownTimer.cancel()
                    btnPlay.text = getString(R.string.restartCmd)
                }
                else -> {
                    tvTime.text = getString(R.string.fullTime)
                    remainingTime = TIMER_DURATION
                    btnPlay.text = getString(R.string.playCmd)
                    updateProgress(0f)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PermissionChecker.PERMISSION_GRANTED) {
                Log.i("MainActivity", "Permesso per le notifiche concesso.")
            } else {
                (getSystemService(ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
                Log.i("MainActivity", "Permesso per le notifiche negato.")
            }
        }
    }

    private fun getPreferences(){
        val preferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        remainingTime = preferences.getLong("remainingTime", TIMER_DURATION)
        isFirstPortraitCall = preferences.getBoolean("isFirstPortraitCall", false)
        isFirstReverseCall = preferences.getBoolean("isFirstReverseCall", true)
    }

    // Start the countdown timer
    private fun startCountdownTimer() {
        countDownTimer = object : CountDownTimer(remainingTime, COUNT_DOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                // Update the TextView with the remaining time
                remainingTime = millisUntilFinished
                val minutes = remainingTime / 1000 / 60
                val seconds = remainingTime / 1000 % 60
                tvTime.text = String.format("%02d:%02d", minutes, seconds)
                // Compute the percentage of the time passed
                val percentage = 1-(remainingTime.toFloat() / (TIMER_DURATION))

                updateProgress(percentage)
            }

            override fun onFinish() {
                remainingTime = TIMER_DURATION
                tvTime.text = getString(R.string.fullTime)
                btnPlay.text = getString(R.string.restartCmd)

                // Create the notification
                hourglassService.buildNotification()
            }
        }.start()
        btnPlay.text = getString(R.string.pauseCmd)
    }

    // Update the progress bar based on the given percentage
    private fun updateProgress(percentage: Float) {
        val topLayoutParams = imgTopProgressBar.layoutParams as ViewGroup.MarginLayoutParams
        val bottomLayoutParams = imgBottomProgressBar.layoutParams as ViewGroup.MarginLayoutParams

        // Calculate margins to adapt the progress bars to the layout
        topLayoutParams.topMargin = imgBlackMargin.height/10
        bottomLayoutParams.topMargin = imgBlackMargin.height*21/40

        // Calculate heights and widths for the progress bars
        imgTopProgressBar.layoutParams.height = (percentage * (imgBlackMargin.height*3/8)).toInt()
        imgTopProgressBar.layoutParams.width = imgBlackMargin.width
        imgTopProgressBar.requestLayout()

        imgBottomProgressBar.layoutParams.height = ((imgBlackMargin.height*3/8) - (percentage*(imgBlackMargin.height*3/8))).toInt()
        imgBottomProgressBar.layoutParams.width = imgBlackMargin.width
        imgBottomProgressBar.requestLayout()
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        Log.e("MainActivity", "onResumeCalled")

        //Re-opening activity after background
        if(isBound) {
            hourglassService.setPreferences()
            hourglassService.stopTimer()
            getPreferences()
            startCountdownTimer()
        }
    }

    override fun onPause() {
        Log.e("MainActivity", "onPauseCalled")
        super.onPause()
        sensorManager.unregisterListener(this)

        if(isBound) {
            hourglassService.setRemainingTime(remainingTime)
            hourglassService.setFirstPortraitCall(isFirstPortraitCall)
            hourglassService.setFirstReverseCall(isFirstReverseCall)
            hourglassService.startTimer()
            countDownTimer.cancel()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Get the accelerometer values
            val x = event.values[0]
            val y = event.values[1]

            // Rotate UI elements based on screen rotation
            when (rotation) {
                Surface.ROTATION_0 -> {
                    imgTopProgressBar.rotation = if (y < 0) -x else x
                    imgBottomProgressBar.rotation = if (y < 0) -x else x
                    imgBottomSand.rotation = if (y < 0) -x else x
                    imgTopSand.rotation = if (y < 0) -x else x
                    imgBlackMargin.rotation = if (y < 0) -x else x
                }
                Surface.ROTATION_90 -> {
                    imgTopProgressBar.rotation = if (x > 0) -y else y
                    imgBottomProgressBar.rotation = if (x > 0) -y else y
                    imgBottomSand.rotation = if (x > 0) -y else y
                    imgTopSand.rotation = if (x > 0) -y else y
                    imgBlackMargin.rotation = if (x > 0) -y else y
                }
                Surface.ROTATION_180 -> {
                    imgTopSand.rotation = if (y > 0) x else -x
                    imgBottomSand.rotation = if (y > 0) x else -x
                    imgTopProgressBar.rotation = if (y > 0) x + 180 else -x + 180
                    imgBottomProgressBar.rotation = if (y > 0) x + 180 else -x + 180
                    imgBlackMargin.rotation = if (y > 0) x + 180 else -x + 180
                }
                Surface.ROTATION_270 -> {
                    imgTopProgressBar.rotation = if (x < 0) y else -y
                    imgBottomProgressBar.rotation = if (x < 0) y else -y
                    imgBottomSand.rotation = if (x < 0) y else -y
                    imgTopSand.rotation = if (x < 0) y else -y
                    imgBlackMargin.rotation = if (x < 0) y else -y
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("MainActivity", "onAccuracyChanged: $sensor, accuracy: $accuracy")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("MainActivity", "onDestroyCalled")
    }

    companion object {
        const val TIMER_DURATION: Long = 10 * 60 * 1000
        const val COUNT_DOWN_INTERVAL : Long = 1000
        private const val REQUEST_CODE = 1
    }
}