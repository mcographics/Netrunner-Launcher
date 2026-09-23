package ohi.andre.consolelauncher.managers.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMonitorFormatterTest {
    @Test
    fun cpuTimesAndDeltaProduceBusyPercent() {
        val previous = SystemMonitorFormatter.parseCpuTimes("cpu  100 0 50 800 50 0 0 0")!!
        val current = SystemMonitorFormatter.parseCpuTimes("cpu  150 0 70 860 60 0 0 0")!!

        assertEquals(50, SystemMonitorFormatter.cpuBusyPercent(previous, current))
    }

    @Test
    fun invalidCpuDataIsRejected() {
        assertNull(SystemMonitorFormatter.parseCpuTimes("cpu not-a-counter"))
        assertNull(
            SystemMonitorFormatter.cpuBusyPercent(
                SystemMonitorFormatter.CpuTimes(100, 50),
                SystemMonitorFormatter.CpuTimes(100, 50),
            ),
        )
    }

    @Test
    fun frequencyActivityIsNormalizedBetweenIdleAndMaximum() {
        assertEquals(0, SystemMonitorFormatter.frequencyActivityPercent(400, 400, 1_200))
        assertEquals(50, SystemMonitorFormatter.frequencyActivityPercent(800, 400, 1_200))
        assertEquals(100, SystemMonitorFormatter.frequencyActivityPercent(1_400, 400, 1_200))
    }

    @Test
    fun gpuFormatsAreParsedAndClamped() {
        assertEquals(37, SystemMonitorFormatter.gpuBusyPercent("37 %"))
        assertEquals(100, SystemMonitorFormatter.gpuBusyPercent("240 %"))
        assertEquals(25, SystemMonitorFormatter.gpuBusyRatio("25 100"))
        assertNull(SystemMonitorFormatter.gpuBusyRatio("0 0"))
    }

    @Test
    fun activityBarHasStableWidthAndRepresentsUsage() {
        assertEquals("░░░░░░░░", SystemMonitorFormatter.activityBar(0, 8))
        assertEquals("████░░░░", SystemMonitorFormatter.activityBar(50, 8))
        assertEquals("████████", SystemMonitorFormatter.activityBar(100, 8))
        assertEquals("········", SystemMonitorFormatter.activityBar(null, 8))
    }

    @Test
    fun panelContainsOneConsolidatedTenLineMonitorWithoutABottomRule() {
        val panel = SystemMonitorFormatter.formatPanel(
            SystemMonitorFormatter.Snapshot(
                network = "WIFI CONNECTED",
                ipAddress = "10.79.89.33",
                memoryAvailableBytes = 2L * 1024 * 1024 * 1024,
                memoryTotalBytes = 6L * 1024 * 1024 * 1024,
                storageAvailableBytes = 8L * 1024 * 1024 * 1024,
                storageTotalBytes = 128L * 1024 * 1024 * 1024,
                batteryPercent = 73,
                cpuPercent = 25,
                gpuPercent = 10,
                ramPercent = 67,
                socModel = "SM8250",
                gpuModel = "ADRENO 650",
            ),
        )

        val lines = panel.lines()
        assertEquals(10, lines.size)
        assertEquals(1, lines.count { it.startsWith("NET ") })
        assertEquals(1, lines.count { it.startsWith("IP ") })
        assertEquals(1, lines.count { it.startsWith("MEM FREE") })
        assertEquals(1, lines.count { it.startsWith("BATTERY") })
        assertTrue(lines.single { it.startsWith("BATTERY") }.contains("73%"))
        assertEquals(0, lines.count { it.startsWith("STORAGE") })
        assertEquals(1, lines.count { it.startsWith("CPU ") })
        assertEquals(1, lines.count { it.startsWith("GPU ") })
        assertEquals(1, lines.count { it.startsWith("RAM ") })
        assertTrue(lines.last().startsWith("HW "))
        assertFalse(lines.last().contains("─"))
        assertTrue(lines.all { it.length <= 40 })
        assertFalse(panel.contains("/home/kenneth"))
    }
}
