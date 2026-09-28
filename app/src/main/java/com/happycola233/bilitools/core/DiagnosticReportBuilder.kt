package com.happycola233.bilitools.core

import com.happycola233.bilitools.data.AppSettings
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

data class DiagnosticTaskSnapshot(
    val id: Long,
    val summary: String,
    val fields: Map<String, String>,
    val groupFields: Map<String, String>,
)

data class DiagnosticReportSnapshot(
    val summary: Map<String, String>,
    val account: Map<String, String>,
    val environment: Map<String, String>,
    val exits: List<DiagnosticExit>,
    val tasks: List<DiagnosticTaskSnapshot>,
    val settings: AppSettings,
    val logs: List<DiagnosticLogSnapshot>,
    val focus: DiagnosticTaskSnapshot? = null,
)

data class DiagnosticReport(
    val text: String,
    val preview: String,
    val logLineCount: Int,
    val logTimeRange: String,
)

/** 不读磁盘、不查系统、不联网。生成全文和预览共用同一份快照与脱敏策略。 */
object DiagnosticReportBuilder {
    fun build(snapshot: DiagnosticReportSnapshot, description: String = "", redactor: LogRedactor = LogRedactor()): DiagnosticReport {
        val orderedLogs = snapshot.logs.sortedWith(compareBy<DiagnosticLogSnapshot> { it.lastModifiedAtMillis }.thenBy { it.name })
        val logText = orderedLogs.joinToString("") { it.content }
        val lines = logText.lineSequence().filter { it.isNotBlank() }.toList()
        val problems = lines.filter { LOG_PROBLEM.containsMatchIn(it) }.takeLast(20).reversed()
        fun sections(includeEvents: Boolean) = redactor.redact(buildString {
            appendLine("# BiliTools diagnostic report")
            if (description.isNotBlank()) section("Problem description") { appendLine(description.trim()) }
            section("Summary") { fields(snapshot.summary) }
            section("Account") { fields(snapshot.account) }
            section("Environment") { fields(snapshot.environment) }
            section("Recent problems") {
                appendLine("Process exits:")
                if (snapshot.exits.isEmpty()) appendLine("(none available)")
                snapshot.exits.take(5).forEach { exit ->
                    appendLine(exit.summary())
                    exit.trace?.let { appendLine(it) }
                }
                appendLine("Warnings and errors (newest first):")
                if (problems.isEmpty()) appendLine("(none)")
                problems.forEach(::appendLine)
                appendLine("Downloads (failed, paused, active; up to 20):")
                if (snapshot.tasks.isEmpty()) appendLine("(none)")
                snapshot.tasks.take(20).forEach { appendLine(it.summary) }
            }
            snapshot.focus?.let { task ->
                section("Focus") {
                    appendLine("Task:")
                    fields(task.fields)
                    appendLine("Group:")
                    fields(task.groupFields)
                    appendLine("Task events:")
                    val marker = Regex("\\btaskId=${task.id}(?![0-9])")
                    val events = lines.filter { marker.containsMatchIn(it) }
                    if (includeEvents) events.forEach(::appendLine) else appendLine("${events.size} lines (included in the report file)")
                }
            }
            section("Settings") {
                val changed = settingsDifferences(snapshot.settings)
                if (changed.isEmpty()) appendLine("(all defaults)") else fields(changed)
            }
        })
        // 文件名、用户描述、外部异常等没有固定长度；预览始终有界，全文仍完整保存在文件中。
        val preview = sections(includeEvents = false).let { text ->
            if (text.length <= 64 * 1024) text else text.take(64 * 1024) + "\n[Preview truncated; see the report file]"
        }
        val range = orderedLogs.let { logs ->
            if (logs.isEmpty()) "—" else {
                val first = logs.first()
                val start = first.name.removePrefix("run-").substringBefore('-').toLongOrNull() ?: first.lastModifiedAtMillis
                "${java.time.Instant.ofEpochMilli(start)} – ${java.time.Instant.ofEpochMilli(logs.last().lastModifiedAtMillis)}"
            }
        }
        return DiagnosticReport(
            text = sections(includeEvents = true) + "\n[Log]\n" + redactor.redact(logText),
            preview = preview,
            logLineCount = lines.size,
            logTimeRange = range,
        )
    }

    fun settingsDifferences(settings: AppSettings): Map<String, String> {
        val defaults = fieldsOf(AppSettings())
        return fieldsOf(settings).filter { (key, value) -> defaults[key] != value }
    }

    /** 只遍历 data class 构造参数，自动覆盖新增设置；不输出计算属性和伴生对象。 */
    fun fieldsOf(value: Any): Map<String, String> = buildMap {
        fun visit(prefix: String, item: Any?) {
            if (item != null && item::class.isData) {
                val names = item::class.primaryConstructor!!.parameters.map { it.name }.toSet()
                item::class.memberProperties.filter { it.name in names }.sortedBy { it.name }.forEach { property ->
                    visit(if (prefix.isEmpty()) property.name else "$prefix.${property.name}", property.getter.call(item))
                }
            } else put(prefix, item?.toString() ?: "null")
        }
        visit("", value)
    }

    private fun StringBuilder.section(name: String, body: StringBuilder.() -> Unit) {
        appendLine()
        appendLine("[$name]")
        body()
    }

    private fun StringBuilder.fields(fields: Map<String, String>) = fields.forEach { (key, value) -> appendLine("$key=$value") }
    private val LOG_PROBLEM = Regex("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} [WE] ")
}
