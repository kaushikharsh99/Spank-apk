package com.example.spank

import android.app.*
import android.content.*
import android.hardware.*
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.IOException
import kotlin.math.*

class SpankService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var soundPool: SoundPool
    
    // State
    private val sounds = mutableMapOf<String, Int>()
    private val trackers = mutableMapOf<String, SlapTracker>()
    private var currentMode = "pain"
    private var sensitivity = 15f
    private var volumeScaling = true
    private var lastTriggerTime = 0L
    private val cooldownMs = 750L

    // Binder for UI communication
    inner class LocalBinder : Binder() {
        fun getService(): SpankService = this@SpankService
    }
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(audioAttrs).build()

        loadSounds()
        startForeground(1, createNotification())
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)
    }

    private fun loadSounds() {
        val modes = listOf("pain", "sexy", "halo")
        modes.forEach { mode ->
            try {
                val files = assets.list("audio/$mode")?.sorted() ?: emptyList()
                val soundIds = mutableListOf<Int>()
                files.forEach { fileName ->
                    val assetDescriptor = assets.openFd("audio/$mode/$fileName")
                    val soundId = soundPool.load(assetDescriptor, 1)
                    soundIds.add(soundId)
                }
                trackers[mode] = SlapTracker(soundIds, cooldownMs, mode == "sexy")
                // Store sound IDs for each mode
                soundIds.forEachIndexed { index, id ->
                    sounds["$mode/$index"] = id
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val magnitude = sqrt(event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2))
        val now = System.currentTimeMillis()

        if (magnitude > sensitivity && (now - lastTriggerTime) > cooldownMs) {
            lastTriggerTime = now
            playSlap(magnitude)
        }
    }

    private fun playSlap(amplitude: Float) {
        val tracker = trackers[currentMode] ?: return
        val index = tracker.record(System.currentTimeMillis())
        val soundId = sounds["$currentMode/$index"] ?: return
        
        var vol = 1.0f
        if (volumeScaling) {
            // Map 15-40 m/s^2 to 0.3-1.0 volume
            vol = ((amplitude - 15f) / 25f).coerceIn(0.3f, 1.0f)
        }
        
        soundPool.play(soundId, vol, vol, 1, 0, 1f)
    }

    // Config updates from UI
    fun updateConfig(mode: String, sens: Float, volScale: Boolean) {
        currentMode = mode
        sensitivity = sens
        volumeScaling = volScale
    }

    private fun createNotification(): Notification {
        val channelId = "spank_service"
        val channel = NotificationChannel(channelId, "Spank Detection", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Spank Detection Active")
            .setContentText("Monitoring for slaps...")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .build()
    }

    override fun onBind(intent: Intent?) = binder
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Inner class for Escalation Logic
    class SlapTracker(private val soundIds: List<Int>, private val cooldown: Long, private val isEscalation: Boolean) {
        private var score = 0.0
        private var lastTime = 0L
        private val halfLife = 30000.0 // 30s
        private val scale: Double

        init {
            val ssMax = 1.0 / (1.0 - 0.5.pow(cooldown / halfLife))
            scale = (ssMax - 1) / ln((soundIds.size + 1).toDouble())
        }

        fun record(now: Long): Int {
            if (!isEscalation) return (0 until soundIds.size).random()
            
            if (lastTime != 0L) {
                score *= 0.5.pow((now - lastTime) / halfLife)
            }
            score += 1.0
            lastTime = now
            
            val idx = (soundIds.size * (1.0 - exp(-(score - 1) / scale))).toInt()
            return idx.coerceIn(0, soundIds.size - 1)
        }
    }
}
