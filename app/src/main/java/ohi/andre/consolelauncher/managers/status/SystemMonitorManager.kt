package ohi.andre.consolelauncher.managers.status

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import ohi.andre.consolelauncher.UIManager
import java.io.File
import java.net.Inet4Address
import java.util.Locale

class SystemMonitorManager(
    context: Context,
    delay: Long,
    private val listener: StatusUpdateListener?,
) : StatusManager(context, delay) {
    private data class CpuFrequencySource(
        val current: File,
        val minimumKhz: Long,
        val maximumKhz: Long,
    )

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val cpuFrequencySources by lazy(::discoverCpuFrequencySources)
    private var previousCpuTimes: SystemMonitorFormatter.CpuTimes? = null
    private val gpuModel by lazy(::readGpuModel)
    private val socModel by lazy(::readSocModel)
    private var ipRevealGeneration = 0
    private val pendingIpTransitions = mutableListOf<Runnable>()
    private var lastSnapshot: SystemMonitorFormatter.Snapshot? = null

    override fun update() {
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)

        val storage = StatFs(Environment.getDataDirectory().absolutePath)
        val network = readNetwork()
        val gpuPercent = readGpuPercent()
        val ramUsed = (memory.totalMem - memory.availMem).coerceAtLeast(0L)

        val snapshot = SystemMonitorFormatter.Snapshot(
            network = network.first,
            ipAddress = network.second,
            memoryAvailableBytes = memory.availMem,
            memoryTotalBytes = memory.totalMem,
            storageAvailableBytes = storage.availableBytes,
            storageTotalBytes = storage.totalBytes,
            batteryPercent = readBatteryPercent(),
            cpuPercent = readCpuPercent(),
            gpuPercent = gpuPercent,
            ramPercent = SystemMonitorFormatter.percent(ramUsed, memory.totalMem),
            socModel = socModel,
            gpuModel = gpuModel,
        )

        lastSnapshot = snapshot
        publishIpGlitchSequence(snapshot)
    }

    private fun publishIpGlitchSequence(snapshot: SystemMonitorFormatter.Snapshot) {
        pendingIpTransitions.forEach(handler::removeCallbacks)
        pendingIpTransitions.clear()
        val generation = ++ipRevealGeneration
        val hasAddress = snapshot.ipAddress.isNotBlank() && snapshot.ipAddress != "--"

        listener?.onUpdate(
            UIManager.Label.ascii,
            SystemMonitorFormatter.formatPanel(
                snapshot,
                if (hasAddress) snapshot.ipAddress else REDACTED_IP,
            ),
        )
        if (!hasAddress) return

        val frames = listOf(
            GLITCH_FRAME_ONE,
            GLITCH_FRAME_TWO,
            GLITCH_FRAME_THREE,
            REDACTED_IP,
        )
        frames.forEachIndexed { index, frame ->
            val delayMs = IP_REVEAL_MS + IP_GLITCH_FRAME_MS * index
            lateinit var transition: Runnable
            transition = Runnable {
                if (generation == ipRevealGeneration) {
                    listener?.onUpdate(
                        UIManager.Label.ascii,
                        SystemMonitorFormatter.formatPanel(snapshot, frame),
                    )
                }
                pendingIpTransitions.remove(transition)
            }
            pendingIpTransitions.add(transition)
            handler.postDelayed(transition, delayMs)
        }
    }

    override fun stop() {
        ipRevealGeneration++
        pendingIpTransitions.forEach(handler::removeCallbacks)
        pendingIpTransitions.clear()
        lastSnapshot?.let { snapshot ->
            listener?.onUpdate(UIManager.Label.ascii, SystemMonitorFormatter.formatPanel(snapshot, REDACTED_IP))
        }
        super.stop()
    }

    private fun readNetwork(): Pair<String, String> {
        return runCatching {
            val activeNetwork = connectivityManager.activeNetwork ?: return "OFFLINE" to "--"
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return "OFFLINE" to "--"
            val label = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI CONNECTED"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE CONNECTED"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET CONNECTED"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN CONNECTED"
                else -> "CONNECTED"
            }
            val ip = connectivityManager.getLinkProperties(activeNetwork)
                ?.linkAddresses
                ?.asSequence()
                ?.map { it.address }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
                ?: "--"
            label to ip
        }.getOrElse { "OFFLINE" to "--" }
    }


    private fun readBatteryPercent(): Int? = runCatching {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return@runCatching null
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            null
        } else {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        }
    }.getOrNull()


    private fun readCpuPercent(): Int {
        readSystemCpuTimes()?.let { current ->
            val previous = previousCpuTimes
            previousCpuTimes = current
            if (previous != null) {
                SystemMonitorFormatter.cpuBusyPercent(previous, current)?.let { return it }
            }
        }

        val frequencyActivity = cpuFrequencySources.mapNotNull { source ->
            val currentKhz = source.current.readLongOrNull() ?: return@mapNotNull null
            SystemMonitorFormatter.frequencyActivityPercent(
                currentKhz = currentKhz,
                minimumKhz = source.minimumKhz,
                maximumKhz = source.maximumKhz,
            )
        }
        return if (frequencyActivity.isEmpty()) 0 else frequencyActivity.average().toInt().coerceIn(0, 100)
    }

    private fun readSystemCpuTimes(): SystemMonitorFormatter.CpuTimes? = runCatching {
        File("/proc/stat").useLines { lines ->
            lines.firstOrNull { it.startsWith("cpu ") }
                ?.let(SystemMonitorFormatter::parseCpuTimes)
        }
    }.getOrNull()

    private fun discoverCpuFrequencySources(): List<CpuFrequencySource> {
        return File("/sys/devices/system/cpu").listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("cpu\\d+")) }
            .sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { cpuDirectory ->
                val frequencyDirectory = File(cpuDirectory, "cpufreq")
                val current = File(frequencyDirectory, "scaling_cur_freq")
                val minimum = listOf("cpuinfo_min_freq", "scaling_min_freq")
                    .firstNotNullOfOrNull { File(frequencyDirectory, it).readLongOrNull() }
                val maximum = listOf("cpuinfo_max_freq", "scaling_max_freq")
                    .firstNotNullOfOrNull { File(frequencyDirectory, it).readLongOrNull() }
                if (!current.canRead() || minimum == null || maximum == null || maximum <= minimum) {
                    null
                } else {
                    CpuFrequencySource(current, minimum, maximum)
                }
            }
    }

    private fun readGpuPercent(): Int? {
        val gpuRoot = File("/sys/class/kgsl/kgsl-3d0")
        File(gpuRoot, "gpu_busy_percentage").readTextOrNull()
            ?.let(SystemMonitorFormatter::gpuBusyPercent)
            ?.let { return it }
        return File(gpuRoot, "gpubusy").readTextOrNull()
            ?.let(SystemMonitorFormatter::gpuBusyRatio)
    }

    private fun readGpuModel(): String {
        val raw = File("/sys/class/kgsl/kgsl-3d0/gpu_model").readTextOrNull()?.trim().orEmpty()
        val adreno = Regex("(?i)adreno\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)
        return if (adreno != null) "ADRENO $adreno" else raw.uppercase(Locale.US).ifBlank { "GPU" }
    }

    private fun readSocModel(): String {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
        return raw.uppercase(Locale.US).ifBlank { Build.HARDWARE.uppercase(Locale.US) }
    }

    private fun File.readLongOrNull(): Long? = readTextOrNull()?.trim()?.toLongOrNull()

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    private companion object {
        const val IP_REVEAL_MS = 300L
        const val IP_GLITCH_FRAME_MS = 35L
        const val REDACTED_IP = "REDACTED"
        const val GLITCH_FRAME_ONE = "▒▒.██.▒▒.██"
        const val GLITCH_FRAME_TWO = "██.▒▒.██.▒▒"
        const val GLITCH_FRAME_THREE = "▒#.█▒.##.█▒"
    }
}
