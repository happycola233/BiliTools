package com.happycola233.bilitools.core

import com.happycola233.bilitools.data.AppSettings
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.reflect.full.primaryConstructor

class DiagnosticReportBuilderTest {
    private fun snapshot(): DiagnosticReportSnapshot {
        val task = DiagnosticTaskSnapshot(42, "taskId=42 contentId=BV1test type=AudioVideo status=Failed failure=FailureMerge quality=120 codec=Hevc audio=30280 bytes=1048576/2097152", mapOf("id" to "42", "status" to "Failed"), mapOf("id" to "7", "relativePath" to "Download/BiliTools/Example"))
        return DiagnosticReportSnapshot(
            summary = linkedMapOf("version" to "3.0-test (14)", "debuggable" to "true", "device" to "Example Test device", "android" to "15 (SDK 35)", "buildDisplay" to "TEST", "installedAbi" to "arm64", "language" to "zh-Hans", "timezone" to "Asia/Shanghai"),
            account = mapOf("loggedIn" to "true", "vipStatus" to "1", "vipType" to "2"),
            environment = mapOf("network" to "wifi", "validated" to "true", "vpn" to "false", "internalFreeBytes" to "104857600", "backgroundRestricted" to "false", "ignoringBatteryOptimizations" to "false"),
            exits = listOf(DiagnosticExit(1_800_000_000_000, "LOW_MEMORY(3)", "Memory pressure", 400)),
            tasks = listOf(task), settings = AppSettings(convertVideoToMp4 = true),
            logs = listOf(DiagnosticLogSnapshot("sample.log", 0, 1_800_000_000_000, """
                === Session 2026-09-28T10:00+08:00 pid=123 version=3.0/14 ===
                10:00:01.001 D Network [worker] method=GET url=https://api.bilibili.com/x/player/wbi/playurl?aid=123&qn=127&fnval=4048&w_rid=test-secret&csrf=test-secret status=200 code=-352
                10:00:01.002 W ParseViewModel [main] [stream] failed contentId=BV1test code=-352
                10:00:02.001 I DownloadRepository [main] created taskId=420
                10:00:03.001 E MediaProcessingEngine [worker] [native] operation=Media merge returnCode=1 lastOutput=No space left on device
                No space left on device
                10:00:03.002 W DownloadRepository [worker] taskId=42 status=Failed failure=FailureMerge
            """.trimIndent() + "\n")),
            focus = task,
        )
    }

    @Test fun ordersSectionsFiltersFocusAndOmitsLogFromPreview() {
        val report = DiagnosticReportBuilder.build(snapshot(), "Merge failed. Cookie: test-secret", LogRedactor())
        val headings = listOf("[Problem description]", "[Summary]", "[Account]", "[Environment]", "[Recent problems]", "[Focus]", "[Settings]", "[Log]")
        assertEquals(headings, headings.sortedBy { report.text.indexOf(it) })
        assertTrue(headings.all { report.text.contains(it) })
        val focus = report.text.substringAfter("[Focus]").substringBefore("[Settings]")
        assertTrue(focus.contains("taskId=42 "))
        assertFalse(focus.contains("taskId=420"))
        assertFalse(report.preview.contains("[Log]"))
        assertFalse(report.preview.contains("method=GET"))
        assertFalse(report.text.contains("test-secret"))
        assertTrue(report.text.contains("convertVideoToMp4=true"))
        assertFalse(report.text.contains("liquidBarGlass"))
        val output = File("../.tmp/diagnostic-report-sample.txt")
        output.parentFile!!.mkdirs()
        output.writeText(report.text)
    }

    @Test fun settingsComparisonCoversEveryConstructorPropertyIncludingFutureAdditions() {
        assertTrue(DiagnosticReportBuilder.settingsDifferences(AppSettings()).isEmpty())
        val allProperties = AppSettings::class.primaryConstructor!!.parameters.map { it.name }.toSet()
        val includedProperties = DiagnosticReportBuilder.fieldsOf(AppSettings()).keys.map { it.substringBefore('.') }.toSet()
        assertEquals(allProperties, includedProperties)
        val changed = DiagnosticReportBuilder.settingsDifferences(AppSettings(naming = AppSettings().naming.copy(showSinglePageNumber = true)))
        assertEquals(mapOf("naming.showSinglePageNumber" to "true"), changed)
    }

    @Test fun previewRemainsBoundedWithAnOversizedExternalError() {
        val report = DiagnosticReportBuilder.build(snapshot().copy(environment = mapOf("external" to "x".repeat(100_000))))
        assertTrue(report.preview.length < 66_000)
        assertTrue(report.text.contains("x".repeat(100_000)))
    }

    @Test fun allSectionsAreRedactedIncludingDescriptionSettingsTaskAndExit() {
        val task = snapshot().focus!!.copy(fields = mapOf("url" to "https://test.bilivideo.com/a/test.m4s?oi=3221225985&access_token=test-credential"))
        val report = DiagnosticReportBuilder.build(snapshot().copy(
            summary = mapOf("self" to "987654321"),
            exits = listOf(DiagnosticExit(1, "CRASH", "Authorization: Bearer test-credential", 1)),
            focus = task,
            settings = AppSettings(ignoredUpdateVersion = "SESSDATA=test-credential"),
        ), "csrf=test-credential DedeUserID=987654321 ip=192.0.2.1", LogRedactor { "987654321" })
        listOf("987654321", "test-credential", "3221225985", "192.0.2.1").forEach { assertFalse(it, report.text.contains(it)) }
    }
}
