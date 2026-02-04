package com.happycola233.bilitools.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DanmakuElem(
    val progressMs: Long,
    val mode: Int,
    val fontSize: Int,
    val color: Int,
    val ctime: Long,
    val pool: Int,
    val midHash: String,
    val idStr: String,
    val content: String,
)

object DanmakuParser {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private const val ASS_PLAY_RES_X = 1920
    private const val ASS_PLAY_RES_Y = 1080
    private const val ASS_BASE_FONT_SIZE = 36
    private const val ASS_LINE_PADDING = 4
    private const val ASS_SCROLL_DURATION = 8.0
    private const val ASS_STATIC_DURATION = 5.0

    fun parse(input: ByteArray): List<DanmakuElem> {
        val reader = ProtoReader(input)
        val list = mutableListOf<DanmakuElem>()
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val field = tag ushr 3
            val wire = tag and 0x7
            if (field == 1 && wire == 2) {
                val length = reader.readLength()
                val limit = reader.position + length
                val elemReader = reader.slice(limit)
                list.add(parseElem(elemReader))
                reader.position = limit
            } else {
                reader.skipField(tag)
            }
        }
        return list
    }

    fun toXml(
        elems: List<DanmakuElem>,
        dateFilter: String? = null,
        hourFilter: Int? = null,
    ): String {
        val filtered = elems.filter { elem ->
            if (dateFilter == null && hourFilter == null) return@filter true
            val instant = Instant.ofEpochSecond(elem.ctime)
            val time = instant.atZone(zoneId)
            if (dateFilter != null) {
                val date = LocalDate.parse(dateFilter)
                if (time.toLocalDate() != date) return@filter false
            }
            if (hourFilter != null && time.hour != hourFilter) return@filter false
            true
        }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.append("<i>")
        for (elem in filtered) {
            val p = listOf(
                elem.progressMs / 1000.0,
                elem.mode,
                elem.fontSize,
                elem.color,
                elem.ctime,
                elem.pool,
                elem.midHash,
                elem.idStr,
            ).joinToString(",")
            sb.append("<d p=\"")
                .append(p)
                .append("\">")
                .append(escapeXml(elem.content))
                .append("</d>")
        }
        sb.append("</i>")
        return sb.toString()
    }

    fun toAss(elems: List<DanmakuElem>): String {
        if (elems.isEmpty()) return buildAssHeader()
        val sorted = elems.sortedBy { it.progressMs }
        val rowHeight = ASS_BASE_FONT_SIZE + ASS_LINE_PADDING
        val rowCount = (ASS_PLAY_RES_Y / rowHeight).coerceAtLeast(1)
        val scrollRows = DoubleArray(rowCount)
        val topRows = DoubleArray(rowCount)
        val bottomRows = DoubleArray(rowCount)
        val sb = StringBuilder()
        sb.append(buildAssHeader())
        sorted.forEach { elem ->
            val start = elem.progressMs / 1000.0
            val fontSize = if (elem.fontSize > 0) elem.fontSize else ASS_BASE_FONT_SIZE
            val cleanText = escapeAss(stripControls(elem.content))
            if (cleanText.isBlank()) return@forEach
            val color = assColor(elem.color)
            val mode = elem.mode
            val duration = if (mode == 4 || mode == 5) ASS_STATIC_DURATION else ASS_SCROLL_DURATION
            val end = start + duration
            val (rowIndex, yPos) = when (mode) {
                4 -> {
                    val row = pickRow(bottomRows, start)
                    row to (ASS_PLAY_RES_Y - (rowHeight * (row + 1)))
                }
                5 -> {
                    val row = pickRow(topRows, start)
                    row to (rowHeight * row)
                }
                else -> {
                    val row = pickRow(scrollRows, start)
                    row to (rowHeight * row)
                }
            }
            when (mode) {
                4 -> bottomRows[rowIndex] = end
                5 -> topRows[rowIndex] = end
                else -> scrollRows[rowIndex] = end
            }
            val tags = when (mode) {
                4 -> "{\\an2\\pos(${ASS_PLAY_RES_X / 2},$yPos)\\fs$fontSize\\c$color}"
                5 -> "{\\an8\\pos(${ASS_PLAY_RES_X / 2},$yPos)\\fs$fontSize\\c$color}"
                6 -> {
                    val width = estimateTextWidth(cleanText, fontSize)
                    val startX = -width
                    val endX = ASS_PLAY_RES_X.toDouble()
                    "{\\an7\\move($startX,$yPos,$endX,$yPos)\\fs$fontSize\\c$color}"
                }
                else -> {
                    val width = estimateTextWidth(cleanText, fontSize)
                    val startX = ASS_PLAY_RES_X.toDouble()
                    val endX = -width
                    "{\\an7\\move($startX,$yPos,$endX,$yPos)\\fs$fontSize\\c$color}"
                }
            }
            sb.append("Dialogue: 0,")
                .append(formatAssTime(start))
                .append(',')
                .append(formatAssTime(end))
                .append(",Default,,0,0,0,,")
                .append(tags)
                .append(cleanText)
                .append('\n')
        }
        return sb.toString()
    }

    private fun parseElem(reader: ProtoReader): DanmakuElem {
        var progress = 0L
        var mode = 0
        var fontSize = 0
        var color = 0
        var ctime = 0L
        var pool = 0
        var midHash = ""
        var idStr = ""
        var content = ""
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val field = tag ushr 3
            when (field) {
                1 -> reader.readVarint().also { /* id, ignore */ }
                2 -> progress = reader.readVarint()
                3 -> mode = reader.readVarint().toInt()
                4 -> fontSize = reader.readVarint().toInt()
                5 -> color = reader.readVarint().toInt()
                6 -> midHash = reader.readString()
                7 -> content = reader.readString()
                8 -> ctime = reader.readVarint()
                9 -> reader.readString().also { /* action, ignore */ }
                10 -> pool = reader.readVarint().toInt()
                11 -> idStr = reader.readString()
                else -> reader.skipField(tag)
            }
        }
        return DanmakuElem(
            progressMs = progress,
            mode = mode,
            fontSize = fontSize,
            color = color,
            ctime = ctime,
            pool = pool,
            midHash = midHash,
            idStr = idStr,
            content = content,
        )
    }

    private fun escapeXml(text: String): String {
        val cleaned = text.filter { ch ->
            if (ch == '\t' || ch == '\n' || ch == '\r') return@filter true
            val code = ch.code
            code >= 0x20 && (code < 0x7F || code > 0x9F)
        }
        return buildString(cleaned.length) {
            for (c in cleaned) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }
    }

    private fun stripControls(text: String): String {
        return text.filter { ch ->
            if (ch == '\t' || ch == '\n' || ch == '\r') return@filter true
            val code = ch.code
            code >= 0x20 && (code < 0x7F || code > 0x9F)
        }
    }

    private fun buildAssHeader(): String {
        return buildString {
            append("[Script Info]\n")
            append("; Script generated by BiliTools\n")
            append("ScriptType: v4.00+\n")
            append("PlayResX: $ASS_PLAY_RES_X\n")
            append("PlayResY: $ASS_PLAY_RES_Y\n")
            append("ScaledBorderAndShadow: yes\n")
            append("\n")
            append("[V4+ Styles]\n")
            append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
            append("Style: Default,Arial,$ASS_BASE_FONT_SIZE,&H00FFFFFF,&H00FFFFFF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,1,0,7,10,10,10,1\n")
            append("\n")
            append("[Events]\n")
            append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        }
    }

    private fun formatAssTime(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0)
        val hours = total.toInt() / 3600
        val minutes = (total.toInt() % 3600) / 60
        val secs = total.toInt() % 60
        val centis = ((total - total.toInt()) * 100).toInt().coerceIn(0, 99)
        return String.format("%d:%02d:%02d.%02d", hours, minutes, secs, centis)
    }

    private fun escapeAss(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("\r\n", "\\N")
            .replace("\n", "\\N")
    }

    private fun assColor(color: Int): String {
        val rgb = color and 0xFFFFFF
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return String.format("&H00%02X%02X%02X&", b, g, r)
    }

    private fun estimateTextWidth(text: String, fontSize: Int): Double {
        val base = text.codePointCount(0, text.length)
        return base * fontSize * 0.6
    }

    private fun pickRow(rows: DoubleArray, start: Double): Int {
        var bestIndex = 0
        var bestEnd = Double.MAX_VALUE
        for (i in rows.indices) {
            val end = rows[i]
            if (end <= start) {
                return i
            }
            if (end < bestEnd) {
                bestEnd = end
                bestIndex = i
            }
        }
        return bestIndex
    }
}

private class ProtoReader(
    private val data: ByteArray,
    start: Int = 0,
    private val limit: Int = data.size,
) {
    var position: Int = start

    fun isAtEnd(): Boolean = position >= limit

    fun slice(newLimit: Int): ProtoReader {
        return ProtoReader(data, position, newLimit)
    }

    fun readTag(): Int {
        if (isAtEnd()) return 0
        return readVarint().toInt()
    }

    fun readLength(): Int = readVarint().toInt()

    fun readString(): String {
        val length = readLength()
        val end = (position + length).coerceAtMost(limit)
        val text = String(data, position, end - position, Charsets.UTF_8)
        position = end
        return text
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64 && position < limit) {
            val b = data[position++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result
    }

    fun skipField(tag: Int) {
        when (tag and 0x7) {
            0 -> readVarint()
            1 -> position = (position + 8).coerceAtMost(limit)
            2 -> position = (position + readLength()).coerceAtMost(limit)
            5 -> position = (position + 4).coerceAtMost(limit)
            else -> position = limit
        }
    }
}
