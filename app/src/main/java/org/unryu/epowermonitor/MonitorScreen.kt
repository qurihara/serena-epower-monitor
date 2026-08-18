package org.unryu.epowermonitor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Surfaceに速度・バッテリー残量などを自前描画する画面。
 * Car Hardware APIで実際に何が取れるか（実測）も兼ねる。
 */
class MonitorScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private var surfaceContainer: SurfaceContainer? = null
    private var visibleArea: Rect? = null

    // 実測データの入れ物
    private var model: Model? = null
    private var energyLevel: EnergyLevel? = null
    private var speed: Speed? = null
    private var mileage: Mileage? = null
    private var speedUpdateCount = 0
    private var energyUpdateCount = 0
    private var lastError: String? = null
    private var permissionState = "未要求"

    private val handler = Handler(Looper.getMainLooper())
    private val renderLoop = object : Runnable {
        override fun run() {
            drawFrame()
            handler.postDelayed(this, RENDER_INTERVAL_MS)
        }
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            surfaceContainer = container
            handler.removeCallbacks(renderLoop)
            handler.post(renderLoop)
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            handler.removeCallbacks(renderLoop)
            surfaceContainer = null
        }

        override fun onVisibleAreaChanged(area: Rect) {
            visibleArea = area
        }

        override fun onStableAreaChanged(area: Rect) = Unit
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
        startCarHardwareListeners()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        handler.removeCallbacks(renderLoop)
    }

    private fun startCarHardwareListeners() {
        val executor = ContextCompat.getMainExecutor(carContext)
        try {
            val carInfo = carContext.getCarService(CarHardwareManager::class.java).carInfo
            carInfo.fetchModel(executor) { model = it }
            carInfo.addSpeedListener(executor) {
                speed = it
                speedUpdateCount++
            }
            carInfo.addEnergyLevelListener(executor) {
                energyLevel = it
                energyUpdateCount++
            }
            carInfo.addMileageListener(executor) { mileage = it }
        } catch (e: Exception) {
            lastError = "CarHardware: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun requestCarPermissions() {
        permissionState = "要求中"
        try {
            carContext.requestPermissions(
                listOf(
                    "com.google.android.gms.permission.CAR_SPEED",
                    "com.google.android.gms.permission.CAR_FUEL",
                    "com.google.android.gms.permission.CAR_MILEAGE",
                )
            ) { granted, rejected ->
                permissionState = "許可 ${granted.size} 件 / 拒否 ${rejected.size} 件"
                // 許可が変わったのでリスナーを張り直す
                startCarHardwareListeners()
            }
        } catch (e: Exception) {
            permissionState = "エラー: ${e.message}"
        }
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("権限")
                    .setOnClickListener { requestCarPermissions() }
                    .build()
            )
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    // ---- 描画 ----

    private val bgPaint = Paint().apply { color = Color.rgb(10, 14, 22) }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 200, 255); textSize = 34f
    }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 140f; isFakeBoldText = true
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY; textSize = 36f
    }
    private val probePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 255, 180); textSize = 26f
    }
    private val gaugeBgPaint = Paint().apply { color = Color.rgb(40, 48, 60) }
    private val gaugeFgPaint = Paint().apply { color = Color.rgb(80, 220, 120) }
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.JAPAN)

    private fun drawFrame() {
        val container = surfaceContainer ?: return
        val surface = container.surface ?: return
        if (!surface.isValid) return
        val canvas: Canvas = try {
            surface.lockCanvas(null)
        } catch (e: Exception) {
            lastError = "lockCanvas: ${e.message}"
            return
        }
        try {
            render(canvas)
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
    }

    private fun render(canvas: Canvas) {
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), bgPaint)
        val area = visibleArea ?: Rect(0, 0, canvas.width, canvas.height)
        val left = area.left + 40f
        var y = area.top + 60f

        canvas.drawText("e-POWER モニター  ${timeFormat.format(Date())}", left, y, titlePaint)
        y += 40f

        // 速度の大表示（表示速度はm/s単位なのでkm/hに換算する）
        val speedKmh = speed?.displaySpeedMetersPerSecond?.valueOrNull()?.times(3.6)
        val speedText = speedKmh?.let { String.format(Locale.JAPAN, "%.0f", it) } ?: "--"
        y += 130f
        canvas.drawText(speedText, left, y, bigPaint)
        canvas.drawText("km/h", left + bigPaint.measureText(speedText) + 20f, y, unitPaint)

        // バッテリー残量ゲージ
        y += 60f
        val batteryPercent = energyLevel?.batteryPercent?.valueOrNull()
        val gaugeWidth = (area.width() - 80f).coerceAtLeast(200f)
        canvas.drawRect(left, y, left + gaugeWidth, y + 36f, gaugeBgPaint)
        if (batteryPercent != null) {
            canvas.drawRect(left, y, left + gaugeWidth * (batteryPercent / 100f), y + 36f, gaugeFgPaint)
        }
        y += 66f
        val batteryText = batteryPercent?.let { String.format(Locale.JAPAN, "駆動用バッテリー %.1f %%", it) }
            ?: "駆動用バッテリー --"
        canvas.drawText(batteryText, left, y, unitPaint)

        // 実測プローブ: 各項目の生の値とステータスを列挙する
        y += 50f
        probeLines().forEach { line ->
            canvas.drawText(line, left, y, probePaint)
            y += 32f
        }
    }

    private fun probeLines(): List<String> {
        val lines = mutableListOf<String>()
        lines += "-- Car Hardware API 実測 --"
        lines += "権限: $permissionState"
        lines += "model: ${model?.let { m ->
            "${m.manufacturer.valueOrNull()} ${m.name.valueOrNull()} ${m.year.valueOrNull()}" +
                " (${m.name.statusName()})"
        } ?: "未取得"}"
        lines += "speed.display: ${speed?.displaySpeedMetersPerSecond.describe()} m/s (更新${speedUpdateCount}回)"
        lines += "speed.raw: ${speed?.rawSpeedMetersPerSecond.describe()} m/s"
        lines += "energy.batteryPercent: ${energyLevel?.batteryPercent.describe()} % (更新${energyUpdateCount}回)"
        lines += "energy.fuelPercent: ${energyLevel?.fuelPercent.describe()} %"
        lines += "energy.rangeRemaining: ${energyLevel?.rangeRemainingMeters.describe()} m"
        lines += "mileage.odometer: ${mileage?.odometerMeters.describe()} m"
        lastError?.let { lines += "error: $it" }
        return lines
    }

    private fun <T> CarValue<T>?.valueOrNull(): T? =
        if (this != null && status == CarValue.STATUS_SUCCESS) value else null

    private fun CarValue<*>?.statusName(): String = when (this?.status) {
        null -> "null"
        CarValue.STATUS_SUCCESS -> "SUCCESS"
        CarValue.STATUS_UNAVAILABLE -> "UNAVAILABLE"
        CarValue.STATUS_UNKNOWN -> "UNKNOWN"
        CarValue.STATUS_UNIMPLEMENTED -> "UNIMPLEMENTED"
        else -> "status=$status"
    }

    private fun CarValue<*>?.describe(): String {
        if (this == null) return "未取得"
        val v = if (status == CarValue.STATUS_SUCCESS) value.toString() else "-"
        return "$v [${statusName()}]"
    }

    companion object {
        private const val RENDER_INTERVAL_MS = 100L
    }
}
