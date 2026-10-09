package com.roami.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** 摇一摇：加速度突变连着来两下就算。回环境选择页用，和截图里那家一样。 */
class ShakeDetector(ctx: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var last = 0L
    private var count = 0

    fun start() { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    fun stop() { sm.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
        if (g < 2.7f) return
        val now = System.currentTimeMillis()
        if (now - last > 500) count = 0 else if (now - last < 120) return
        last = now
        if (++count >= 2) { count = 0; last = 0; onShake() }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
