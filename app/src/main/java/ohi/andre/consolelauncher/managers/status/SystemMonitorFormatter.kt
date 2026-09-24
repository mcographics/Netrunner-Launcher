package ohi.andre.consolelauncher.managers.status

import java.util.Locale

internal object SystemMonitorFormatter {
    data class CpuTimes(val total: Long, val idle: Long)

    data class Snapshot(
        val network: String,
        val ipAddress: String,
        val memoryAvailableBytes: Long,
        val memoryTotalBytes: Long,
        val storageAvailableBytes: Long,
        val storageTotalBytes: Long,
        val batteryPercent: Int?,
        val cpuPercent: Int,
        val gpuPercent: Int?,
        val ramPercent: Int,
        val socModel: String,
        val gpuModel: String,
    )

    fun parseCpuTimes(line: String): CpuTimes? {
        val fields = line.trim().split(Regex("\\s+"))
        if (fields.firstOrNull() != "cpu" || fields.size < 5) return null

        val counters = fields.drop(1).map { it.toLongOrNull() ?: return null }
        val total = counters.sum()
        val idle = counters.getOrElse(3) { 0L } + counters.getOrElse(4) { 0L }
        return CpuTimes(total = total, idle = idle)
    }

    fun cpuBusyPercent(previous: CpuTimes, current: CpuTimes): Int? {
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        if (totalDelta <= 0L || idleDelta < 0L) return null
        return percent(totalDelta - idleDelta, totalDelta)
    }

    fun frequencyActivityPercent(currentKhz: Long, minimumKhz: Long, maximumKhz: Long): Int? {
        if (maximumKhz <= minimumKhz || currentKhz <= 0L) return null
        return percent(currentKhz - minimumKhz, maximumKhz - minimumKhz)
    }

    fun gpuBusyPercent(value: String): Int? =
        Regex("\\d+").find(value)?.value?.toIntOrNull()?.coerceIn(0, 100)

    fun gpuBusyRatio(value: String): Int? {
        val counters = Regex("\\d+").findAll(value).mapNotNull { it.value.toLongOrNull() }.toList()
        if (counters.size < 2 || counters[1] <= 0L) return null
        return percent(counters[0], counters[1])
    }

    fun percent(part: Long, total: Long): Int {
        if (part <= 0L || total <= 0L) return 0
        return ((part.toDouble() / total.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
    }

    fun activityBar(percent: Int?, width: Int = 16): String {
        val safeWidth = width.coerceAtLeast(1)
        if (percent == null) return "·".repeat(safeWidth)
        val safePercent = percent.coerceIn(0, 100)
        val filled = ((safePercent * safeWidth) + 50) / 100
        return "█".repeat(filled) + "░".repeat(safeWidth - filled)
    }

    fun formatPanel(snapshot: Snapshot, displayedIpAddress: String = snapshot.ipAddress): String {
        val lines = listOf(
            "────────────────────────────────────────",
            "SYSTEM MONITOR / LIVE",
            "NET      ${snapshot.network.take(29)}",
            "IP       ${displayedIpAddress.take(29)}",
            "MEM FREE ${formatGiB(snapshot.memoryAvailableBytes)} / ${formatGiB(snapshot.memoryTotalBytes)} GIB",
            batteryMeterLine(snapshot.batteryPercent),
            meterLine("CPU", snapshot.cpuPercent),
            meterLine("GPU", snapshot.gpuPercent),
            meterLine("RAM", snapshot.ramPercent),
            "HW       ${snapshot.socModel} / ${snapshot.gpuModel}".take(40),
        )
        return lines.joinToString("\n")
    }

    private fun meterLine(label: String, value: Int?): String {
        val percentage = value?.coerceIn(0, 100)
        val percentageText = percentage?.let { String.format(Locale.US, "%3d%%", it) } ?: " --%"
        return "$label $percentageText  [${activityBar(percentage)}]"
    }

    private fun batteryMeterLine(value: Int?): String =
        "BATTERY   [${activityBar(value)}]"

    private fun formatGiB(bytes: Long): String =
        String.format(Locale.US, "%.2f", bytes.coerceAtLeast(0L) / GIB.toDouble())

    private const val GIB = 1024L * 1024L * 1024L
}
