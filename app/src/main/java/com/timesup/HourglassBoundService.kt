package com.timesup

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Service that manages the background features.
 * It also shows notifications for Time's Up! application.
 */
class HourglassBoundService : Service() {

    private val binder : IBinder = HourglassBinder()
    private var countDownTimer: CountDownTimer? = null
    private var remainingTime: Long = MainActivity.TIMER_DURATION
    private var isFirstPortraitCall : Boolean = false
    private var isFirstReverseCall : Boolean = true

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // Retrieve remaining time from shared preferences
        remainingTime = getSharedPreferences("MyPreferences", MODE_PRIVATE).getLong("remainingTime", MainActivity.TIMER_DURATION)

        // Build and display the foreground notification
        val notificationBuilder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notification.Builder(applicationContext, CHANNEL_ID)
            else  // Deprecation warning (API 26)
                Notification.Builder(applicationContext)

        notificationBuilder.setContentTitle("Foreground")
        notificationBuilder.setContentText("Time's Up! is running")
        notificationBuilder.setSmallIcon(R.drawable.ic_launcher_foreground)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    inner class HourglassBinder : Binder() {
        val service: HourglassBoundService
            get() = this@HourglassBoundService
    }

    /**
     * Sets the remaining time for the countdown timer.
     * @param time The remaining time in milliseconds.
     */
    fun setRemainingTime(time:Long){
        remainingTime = time
    }

    /**
     * Sets the value indicating if the first portrait call has been made.
     * @param boolValue The boolean value to set the variable.
     */
    fun setFirstPortraitCall(boolValue: Boolean){
        isFirstPortraitCall = boolValue
    }

    /**
     * Sets the value indicating if the first reverse call has been made.
     * @param boolValue The boolean value to set the variable.
     */
    fun setFirstReverseCall(boolValue: Boolean){
        isFirstReverseCall = boolValue
    }

    /**
     * Starts the countdown timer.
     */
    fun startTimer(){
        countDownTimer = object : CountDownTimer(remainingTime, MainActivity.COUNT_DOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTime = millisUntilFinished
                Log.e("Service", "onTick $remainingTime")
            }

            /**
             * Manages consequences after time's over.
             */
            override fun onFinish() {
                remainingTime = MainActivity.TIMER_DURATION
                //Generate notification: time's up!
                buildNotification()

            }
        }.start()
    }

    /**
     * Stops the countdown timer.
     */
    fun stopTimer(){
        countDownTimer?.cancel()
    }

    /**
     * Creates a notification channel for the service.
     */
    fun createNotificationChannel() {
        // 0nly on API level 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notificationName)
            val descriptionText = getString(R.string.descriptionText)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds and displays the time's up notification.
     */
    fun buildNotification(){
        val notificationBuilder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notification.Builder(applicationContext, CHANNEL_ID)
            else  // Deprecation warning
                Notification.Builder(applicationContext)

        notificationBuilder.setSmallIcon(R.drawable.ic_launcher_foreground)
        notificationBuilder.setContentTitle("Time's up!")

        // Show the notification
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * Saves the service preferences to shared preferences.
     */
    fun setPreferences(){
        val preferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putLong("remainingTime", remainingTime)
        editor.putBoolean("isFirstPortraitCall", isFirstPortraitCall)
        editor.putBoolean("isFirstReverseCall", isFirstReverseCall)
        editor.apply()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        //Application has been closed
        setPreferences()
        stopTimer()
        return super.onUnbind(intent)
    }

   companion object{
       private const val CHANNEL_ID = "timesUpChannel"
       private const val NOTIFICATION_ID = 1
   }
}