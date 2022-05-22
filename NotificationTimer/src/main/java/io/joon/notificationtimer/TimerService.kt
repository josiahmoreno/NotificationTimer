package io.joon.notificationtimer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import java.time.Duration


enum class TimerState { STOPPED, PAUSED, RUNNING, TERMINATED }

class TimerService: Service() {

    companion object {
        var state = TimerState.TERMINATED
    }

    private lateinit var timer: CountDownTimer

    private val foreGroundId = 55
    private var secondsRemaining: Long = 0
    private var setTime:Long = 0
    private lateinit var showTime: String

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                "PLAY" -> {
                    playTimer(
                        intent.getLongExtra("setTime", 0L),
                        intent.getBooleanExtra("forReplay", false),
                        intent.getBooleanExtra("vibrationEnabled", false),
                        intent.getLongExtra("vibrationDuration", 1000L),
                        intent.getBooleanExtra("beepEnabled", false),
                        intent.getIntExtra("beepDuration", 2000))
                }
                "PAUSE" -> pauseTimer()
                "STOP" -> stopTimer()
                "TERMINATE" -> terminateTimer()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        if (::timer.isInitialized) {
            timer.cancel()
            state = TimerState.TERMINATED
        }
        NotificationTimer.removeNotification()
        stopSelf()
    }

    private fun playTimer(setTime: Long, isReplay: Boolean, vibrationEnabled: Boolean, vibrationDuration: Long, beepEnabled: Boolean, beepDuration: Int) {

        if (!isReplay) {
            this.setTime = setTime
            secondsRemaining = setTime
            startForeground(foreGroundId, NotificationTimer.createNotification(this, setTime))
        }

        timer = object : CountDownTimer(secondsRemaining, 1000) {
            override fun onFinish() {
                state = TimerState.STOPPED
                //초기 세팅됬었던 카운트다운 시간값을 노티에 재세팅
                val minutesUntilFinished = setTime/1000 / 60
                val secondsInMinuteUntilFinished = ((setTime/1000) - minutesUntilFinished * 60)
                val secondsStr = secondsInMinuteUntilFinished.toString()
                val showTime = "$minutesUntilFinished : ${if (secondsStr.length == 2) secondsStr else "0$secondsStr"}"
                NotificationTimer.updateStopState(this@TimerService, showTime, true)
                //vibration
                if(vibrationEnabled){
                    val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                    // Vibrate for 500 milliseconds
                    // Vibrate for 500 milliseconds
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        //deprecated in API 26
                        v.vibrate(vibrationDuration)
                    }
                }
                if(beepEnabled)
                beep(beepDuration)

            }

            override fun onTick(millisUntilFinished: Long) {
                NotificationTimer.updateUntilFinished(millisUntilFinished + (1000-(millisUntilFinished%1000)) - 1000)
                secondsRemaining = millisUntilFinished
                updateCountdownUI()
            }
        }.start()

        state = TimerState.RUNNING
    }


    fun beep(duration: Int)
    {
        val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        toneG.startTone(ToneGenerator.TONE_DTMF_S, duration)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            toneG.release()
        }, (duration + 50).toLong())
    }


    private fun pauseTimer() {
        if (::timer.isInitialized) {
            timer.cancel()
            state = TimerState.PAUSED
            NotificationTimer.updatePauseState(this, showTime)
        }
    }

    private fun stopTimer() {
        if (::timer.isInitialized) {
            timer.cancel()
            state = TimerState.STOPPED
            val minutesUntilFinished = setTime/1000 / 60
            val secondsInMinuteUntilFinished = ((setTime/1000) - minutesUntilFinished * 60)
            val secondsStr = secondsInMinuteUntilFinished.toString()
            val showTime = "$minutesUntilFinished : ${if (secondsStr.length == 2) secondsStr else "0$secondsStr"}"
            NotificationTimer.updateStopState(this@TimerService, showTime)
        }
    }

    private fun terminateTimer() {
        if (::timer.isInitialized) {
            timer.cancel()
            state = TimerState.TERMINATED
            NotificationTimer.removeNotification()
            stopSelf()
        }
    }

    private fun updateCountdownUI() {
        val minutesUntilFinished = (secondsRemaining/1000) / 60
        val secondsInMinuteUntilFinished = ((secondsRemaining/1000) - minutesUntilFinished * 60)
        val secondsStr = secondsInMinuteUntilFinished.toString()
        showTime = "$minutesUntilFinished : ${if (secondsStr.length == 2) secondsStr else "0$secondsStr"}"

        NotificationTimer.updateTimeLeft(this, showTime)
    }

}