package com.timesup

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
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

/**
 *  Main activity of the application
 *  It shows the timer and the UI elements, managing orientation changes through the sensor
 */
class MainActivity : AppCompatActivity(), SensorEventListener {

    // Timer-related variables and constants
    private lateinit var countDownTimer: CountDownTimer
    private var remainingTime : Long = TIMER_DURATION
    private var timerIsRunning : Boolean = true

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

            // Start the countdown timer
            startCountdownTimer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
                    timerIsRunning = true
                }
                "Pause" -> {
                    countDownTimer.cancel()
                    btnPlay.text = getString(R.string.restartCmd)
                    timerIsRunning = false

                }
                else -> {
                    tvTime.text = getString(R.string.fullTime)
                    remainingTime = TIMER_DURATION
                    btnPlay.text = getString(R.string.playCmd)
                    updateProgress(0f)
                    timerIsRunning = false
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE) {
            // User accepted the permission
            if (grantResults.isNotEmpty() && grantResults[0] == PermissionChecker.PERMISSION_GRANTED) {
                countDownTimer.cancel()
                // Do nothing: application correctly in use
            } else {
                countDownTimer.cancel()
                // User denied the permission
                (getSystemService(ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
            }
        }
    }

    /**
     * Retrieve the saved preferences from SharedPreferences
     */
    private fun getPreferences(){
        val preferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        remainingTime = preferences.getLong("remainingTime", TIMER_DURATION)
        isFirstPortraitCall = preferences.getBoolean("isFirstPortraitCall", false)
        isFirstReverseCall = preferences.getBoolean("isFirstReverseCall", true)
    }

    /**
     * Start the countdown timer
     */
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

    /**
     * Update the progress bar based on the given percentage
     * @param percentage The percentage of time passed
     *
     * NOTE: All the calculations were done manually:
     *
     * - the height of the black borders at the top and bottom
     *   of the hourglass is 1/10 of the height of the hourglass itself
     *   @see BORDERS_HEIGHT
     *
     * - The distance between the center of the hourglass and the vertex
     *   that allows the sand to flow is equal to 1/40 of the height of the hourglass
     *   @see DISTANCE_FROM_CENTER
     *
     * - The maximum height of the sand in the globes is 3/8 of the height of the hourglass
     *   (1/2-1/10-1/40 = 3/8)
     *   @see MAX_PROGRESS
     */
    private fun updateProgress(percentage: Float) {
        val topLayoutParams = imgTopProgressBar.layoutParams as ViewGroup.MarginLayoutParams
        val bottomLayoutParams = imgBottomProgressBar.layoutParams as ViewGroup.MarginLayoutParams

        // Calculate margins to adapt the progress bars to the layout
        topLayoutParams.topMargin = imgBlackMargin.height/BORDERS_HEIGHT
        bottomLayoutParams.topMargin = (imgBlackMargin.height*(1/2f+DISTANCE_FROM_CENTER)).toInt()

        // Calculate heights and widths for the progress bars
        imgTopProgressBar.layoutParams.height = (percentage * (imgBlackMargin.height*MAX_PROGRESS)).toInt()
        imgTopProgressBar.layoutParams.width = imgBlackMargin.width
        imgTopProgressBar.requestLayout()

        imgBottomProgressBar.layoutParams.height = ((imgBlackMargin.height*MAX_PROGRESS) - (percentage*(imgBlackMargin.height*MAX_PROGRESS))).toInt()
        imgBottomProgressBar.layoutParams.width = imgBlackMargin.width
        imgBottomProgressBar.requestLayout()
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        //Run the timer
        timerIsRunning = true

        //Re-opening activity after background
        if(isBound) {
            hourglassService.setPreferences()
            hourglassService.stopTimer()
            getPreferences()
            startCountdownTimer()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)

        //Saving preferences
        if(isBound) {
            hourglassService.setRemainingTime(remainingTime)
            hourglassService.setFirstPortraitCall(isFirstPortraitCall)
            hourglassService.setFirstReverseCall(isFirstReverseCall)
            if(timerIsRunning)
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
        // Do nothing, accuracy changed
    }

    companion object {
        const val TIMER_DURATION: Long = 10 * 60 * 1000
        const val COUNT_DOWN_INTERVAL : Long = 1000
        private const val BORDERS_HEIGHT : Int = 10
        private const val DISTANCE_FROM_CENTER : Float = 1/40f
        private const val MAX_PROGRESS : Float = 3/8f
        private const val REQUEST_CODE = 1
    }
}