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

class HourglassBoundService : Service() {

    private val binder : IBinder = HourglassBinder()
    private var countDownTimer: CountDownTimer? = null
    private var remainingTime: Long = MainActivity.TIMER_DURATION
    private var isFirstPortraitCall : Boolean = false
    private var isFirstReverseCall : Boolean = true

    override fun onBind(intent: Intent): IBinder {
        Log.e("HourglassService", "onBind called")
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        remainingTime = getSharedPreferences("MyPreferences", MODE_PRIVATE).getLong("remainingTime", MainActivity.TIMER_DURATION)

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

    fun setRemainingTime(time:Long){
        remainingTime = time
    }

    fun setFirstPortraitCall(boolValue: Boolean){
        isFirstPortraitCall = boolValue
    }

    fun setFirstReverseCall(boolValue: Boolean){
        isFirstReverseCall = boolValue
    }

    fun startTimer(){
        countDownTimer = object : CountDownTimer(remainingTime, MainActivity.COUNT_DOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTime = millisUntilFinished
                Log.e("Service", "onTick $remainingTime")
            }

            override fun onFinish() {
                remainingTime = MainActivity.TIMER_DURATION
                //Generate notification: time's up!
                buildNotification()
            }
        }.start()
    }

    fun stopTimer(){
        countDownTimer?.cancel()
    }

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

    fun setPreferences(){
        val preferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        val editor = preferences.edit()
        Log.e("HourglassService", "setPreferences called with $remainingTime")
        editor.putLong("remainingTime", remainingTime)
        editor.putBoolean("isFirstPortraitCall", isFirstPortraitCall)
        editor.putBoolean("isFirstReverseCall", isFirstReverseCall)
        editor.apply()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.e("HourglassService", "onUnbind called")
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