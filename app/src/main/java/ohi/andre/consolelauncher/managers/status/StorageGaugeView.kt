package ohi.andre.consolelauncher.managers.status

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Environment
import android.os.StatFs
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import java.util.Locale
import kotlin.math.min

class StorageGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcBounds = RectF()

    private var usedPercent = 0
    private var availableBytes = 0L
    private var totalBytes = 0L

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStorage()
            alignToSystemMonitor()
            postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        visibility = VISIBLE
        post {
            refreshStorage()
            alignToSystemMonitor()
        }
        removeCallbacks(refreshRunnable)
        postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refreshRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val accent = runCatching {
            XMLPrefsManager.getColor(Theme.ascii_text_color)
        }.getOrDefault(Color.rgb(255, 32, 32))

        val stroke = dp(5f)
        val radius = (min(width, height) / 2f) - stroke
        val cx = width / 2f
        val cy = height / 2f

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = stroke
        ringPaint.strokeCap = Paint.Cap.BUTT
        val usedSlices = ((usedPercent.coerceIn(0, 100) + SLICE_PERCENT / 2) / SLICE_PERCENT)
            .coerceIn(0, SLICE_COUNT)
        val sliceSweep = 360f / SLICE_COUNT
        val sliceGap = 1.5f
        for (slice in 0 until SLICE_COUNT) {
            ringPaint.color = if (slice < usedSlices) accent else FREE_COLOR
            canvas.drawArc(
                arcBounds,
                -90f + slice * sliceSweep + sliceGap / 2f,
                sliceSweep - sliceGap,
                false,
                ringPaint,
            )
        }

        textPaint.color = accent
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(17f)
        canvas.drawText("${usedPercent}%", cx, cy - dp(2f), textPaint)
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textPaint.textSize = sp(7f)
        canvas.drawText("USED", cx, cy + dp(10f), textPaint)
        textPaint.color = FREE_COLOR
        canvas.drawText("${100 - usedPercent}% FREE", cx, cy + dp(21f), textPaint)
        textPaint.color = accent
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(6f)
        canvas.drawText("STORAGE", cx, cy + dp(32f), textPaint)
    }

    private fun refreshStorage() {
        val storage = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull() ?: return
        totalBytes = storage.totalBytes.coerceAtLeast(0L)
        availableBytes = storage.availableBytes.coerceIn(0L, totalBytes)
        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
        usedPercent = SystemMonitorFormatter.percent(usedBytes, totalBytes)

        val freeGiB = formatGiB(availableBytes)
        val totalGiB = formatGiB(totalBytes)
        contentDescription = "Storage ${usedPercent}% used, $freeGiB GiB free of $totalGiB GiB"
        invalidate()
    }

    private fun alignToSystemMonitor() {
        val root = parent as? ViewGroup ?: return
        root.clipChildren = false
        root.clipToPadding = false
        y = dp(114f)
        translationX = -dp(5f)
        visibility = VISIBLE
    }

    private fun findMonitorTextView(view: View): TextView? {
        if (view is TextView && view.text?.contains("SYSTEM MONITOR / LIVE") == true) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findMonitorTextView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun withAlpha(color: Int, fraction: Float): Int {
        val alpha = (255 * fraction.coerceIn(0f, 1f)).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    private fun formatGiB(bytes: Long): String =
        String.format(Locale.US, "%.2f", bytes / GIB.toDouble())

    companion object {
        private const val REFRESH_INTERVAL_MS = 2_000L
        private const val GIB = 1024L * 1024L * 1024L
        private const val SLICE_PERCENT = 2
        private const val SLICE_COUNT = 100 / SLICE_PERCENT
        private val FREE_COLOR = Color.rgb(150, 180, 195)
    }
}
