package com.happycola233.bilitools.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * 当前 FFmpeg 6 会保留 E-AC-3 音频包，却丢掉 dec3 中的 Atmos / JOC 扩展。
 * 仅为这个已验证的上游缺陷保留源配置；升级到包含 2025-06 movenc 修复的版本后可删除。
 *
 * FFmpeg 产物是普通 MP4。需要修复时将更新后的 moov 写到文件尾，原 moov 原位变成 free，
 * mdat、stco/co64 偏移和所有音视频包完全不动。只修改待发布的临时文件，不修改下载源文件。
 */
internal object Mp4DolbyConfiguration {
    fun preserve(source: File, output: File) {
        val sourceConfigs = readConfigurations(source)
        if (sourceConfigs.none(::hasAtmosExtension)) return
        RandomAccessFile(output, "rw").use { file ->
            val moov = boxes(file, 0, file.length()).firstOrNull { it.type == "moov" }
                ?: throw IOException("MP4 output has no moov box")
            val paths = configurationPaths(file, moov)
            // 显式转为 AAC 后不再有 E-AC-3；不能把旧编码配置放到新音轨中。
            if (paths.isEmpty()) return
            if (paths.size != sourceConfigs.size) throw IOException("E-AC-3 track count changed")
            val replacements = paths.zip(sourceConfigs).mapNotNull { (path, config) ->
                val box = path.last()
                if (!hasAtmosExtension(config) || config.contentEquals(payload(file, box))) null
                else Replacement(path, config)
            }
            if (replacements.isEmpty()) return
            if (moov.size > Int.MAX_VALUE) throw IOException("MP4 index is too large")
            val original = ByteArray(moov.size.toInt())
            file.seek(moov.offset)
            file.readFully(original)
            val rebuilt = rebuildMoov(original, moov, replacements)
            // 先完整写出新索引，再作废旧索引；调用方只会在整个后处理成功后保存这个副本。
            file.seek(file.length())
            file.write(rebuilt)
            file.seek(moov.offset + 4)
            file.writeBytes("free")
        }
    }

    internal fun readConfigurations(source: File): List<ByteArray> = RandomAccessFile(source, "r").use { file ->
        if (file.length() < 8) return@use emptyList()
        file.seek(4)
        val signature = ByteArray(4).also(file::readFully).toString(Charsets.ISO_8859_1)
        if (signature !in setOf("ftyp", "styp", "moov")) return@use emptyList()
        val moov = boxes(file, 0, file.length()).firstOrNull { it.type == "moov" } ?: return@use emptyList()
        configurationPaths(file, moov).map { payload(file, it.last()) }
    }

    private data class Box(val offset: Long, val size: Long, val headerSize: Int, val type: String) {
        val body: Long get() = offset + headerSize
        val end: Long get() = offset + size
    }

    private data class Replacement(val path: List<Box>, val payload: ByteArray) {
        val box: Box get() = path.last()
        val size: Int get() = 8 + payload.size
        val delta: Long get() = size - box.size
    }

    private fun boxes(file: RandomAccessFile, start: Long, end: Long): List<Box> = buildList {
        var position = start
        while (position < end) {
            if (end - position < 8) throw IOException("Truncated MP4 box header")
            file.seek(position)
            val shortSize = file.readInt().toLong() and 0xffffffffL
            val type = ByteArray(4).also(file::readFully).toString(Charsets.ISO_8859_1)
            val headerSize = if (shortSize == 1L) 16 else 8
            if (end - position < headerSize) throw IOException("Truncated extended MP4 box header")
            val size = when (shortSize) {
                0L -> end - position
                1L -> file.readLong()
                else -> shortSize
            }
            if (size < headerSize || size > end - position) throw IOException("Invalid MP4 box size")
            add(Box(position, size, headerSize, type))
            position += size
        }
    }

    private fun configurationPaths(file: RandomAccessFile, moov: Box): List<List<Box>> {
        val levels = listOf("trak", "mdia", "minf", "stbl", "stsd", "ec-3", "dec3")
        fun find(path: List<Box>, level: Int): List<List<Box>> {
            if (level == levels.size) return listOf(path)
            val parent = path.last()
            val prefix = when (parent.type) {
                "stsd" -> 8 // version / flags 与 entry_count
                "ec-3" -> {
                    if (parent.size < parent.headerSize + 28) throw IOException("Truncated E-AC-3 sample entry")
                    file.seek(parent.body + 8)
                    when (file.readUnsignedShort()) {
                        0 -> 28
                        1 -> 44
                        2 -> 64
                        else -> throw IOException("Unsupported audio sample entry version")
                    }
                }
                else -> 0
            }
            if (parent.body + prefix > parent.end) throw IOException("Truncated MP4 sample description")
            return boxes(file, parent.body + prefix, parent.end).filter { it.type == levels[level] }
                .flatMap { find(path + it, level + 1) }
        }
        return find(listOf(moov), 0)
    }

    private fun payload(file: RandomAccessFile, box: Box): ByteArray {
        // dec3 最多包含 8 个独立子流及少量扩展，不能按外部声明的任意大小分配内存。
        val size = box.size - box.headerSize
        if (size !in 5..1024) throw IOException("Invalid E-AC-3 configuration size")
        return ByteArray(size.toInt()).also {
            file.seek(box.body)
            file.readFully(it)
        }
    }

    internal fun hasAtmosExtension(payload: ByteArray): Boolean {
        var bit = 0
        fun read(count: Int): Int {
            if (bit + count > payload.size * 8) throw IOException("Truncated E-AC-3 configuration")
            var value = 0
            repeat(count) {
                value = (value shl 1) or ((payload[bit / 8].toInt() ushr (7 - bit % 8)) and 1)
                bit++
            }
            return value
        }
        read(13) // data_rate
        repeat(read(3) + 1) {
            read(19) // fscod, bsid, reserved, asvc, bsmod, acmod, lfeon, reserved
            val dependents = read(4)
            read(if (dependents == 0) 1 else 9)
        }
        if (payload.size * 8 - bit < 8) return false
        read(7)
        if (read(1) == 0) return false
        read(8) // complexity_index_type_a 必须存在，保留其原始值。
        return true
    }

    private fun rebuildMoov(original: ByteArray, moov: Box, replacements: List<Replacement>): ByteArray {
        val ordered = replacements.sortedBy { it.box.offset }
        val result = ByteArrayOutputStream()
        var copied = 0
        for (replacement in ordered) {
            val start = (replacement.box.offset - moov.offset).toInt()
            result.write(original, copied, start - copied)
            result.write(ByteBuffer.allocate(8).putInt(replacement.size).put("dec3".toByteArray()).array())
            result.write(replacement.payload)
            copied = (replacement.box.end - moov.offset).toInt()
        }
        result.write(original, copied, original.size - copied)
        val rebuilt = result.toByteArray()
        val ancestors = ordered.flatMap { it.path.dropLast(1) }.distinctBy { it.offset }
        for (ancestor in ancestors) {
            val precedingDelta = ordered.filter { it.box.end <= ancestor.offset }.sumOf { it.delta }
            val insideDelta = ordered.filter { it.box.offset > ancestor.offset && it.box.end <= ancestor.end }
                .sumOf { it.delta }
            val offset = (ancestor.offset - moov.offset + precedingDelta).toInt()
            val buffer = ByteBuffer.wrap(rebuilt)
            if (ancestor.headerSize == 16) buffer.putLong(offset + 8, ancestor.size + insideDelta)
            else buffer.putInt(offset, (ancestor.size + insideDelta).toInt())
        }
        return rebuilt
    }
}
