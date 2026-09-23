package ohi.andre.consolelauncher.managers.status

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
        visibility = INVISIBLE
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
        ringPaint.strokeCap = Paint.Cap.ROUND
        ringPaint.color = withAlpha(accent, 0.20f)
        canvas.drawArc(arcBounds, -90f, 360f, false, ringPaint)

        ringPaint.color = accent
        canvas.drawArc(
            arcBounds,
            -90f,
            360f * (usedPercent.coerceIn(0, 100) / 100f),
            false,
            ringPaint,
        )

        textPaint.color = accent
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textPaint.textSize = sp(7.5f)
        canvas.drawText("STORAGE", cx, cy - dp(14f), textPaint)

        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = sp(18f)
        canvas.drawText("${usedPercent.coerceIn(0, 100)}%", cx, cy + dp(6f), textPaint)

        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textPaint.textSize = sp(7.5f)
        canvas.drawText("USED", cx, cy + dp(20f), textPaint)
    }

    private fun refreshStorage() {
        val storage = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull() ?: return
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
        val monitorView = findMonitorTextView(root) ?: return
        val textLayout = monitorView.layout ?: return

        val lineIndex = monitorView.text
            ?.toString()
            ?.lines()
            ?.indexOfFirst { it.startsWith("MEM FREE") }
            ?: -1
        if (lineIndex < 0 || lineIndex >= textLayout.lineCount) {
            return
        }

        val rootLocation = IntArray(2)
        val monitorLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        monitorView.getLocationOnScreen(monitorLocation)

        val lineTop = textLayout.getLineTop(lineIndex)
        val targetY = monitorLocation[1] - rootLocation[1] + lineTop - dp(8f).toInt()
        translationY = targetY.coerceAtLeast(0).toFloat()
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
    }
}
