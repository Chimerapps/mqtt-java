package com.chimerapps.mqtt

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.EOFException
import java.io.IOException

internal object StreamDataUtils {

    private const val MULTIPLIER_MAX = 2097152L //128*128*128

    @Throws(EOFException::class)
    fun readByte(buffer: BufferedSource): Byte {
        return buffer.readByte()
    }

    @Throws(EOFException::class)
    fun readShort(buffer: BufferedSource): Int {
        val msb = (buffer.readByte().toInt() shl 8) and 0xFF00
        val lsb = buffer.readByte().toInt() and 0xFF
        return (msb + lsb)
    }

    @Throws(EOFException::class)
    fun readString(buffer: BufferedSource): String? {
        val stringLength = buffer.readShort().toLong()
        return if (stringLength == 0L) "" else buffer.readString(stringLength, Charsets.UTF_8)
    }

    @Throws(IOException::class)
    fun readVariableLength(buffer: BufferedSource): Long {
        var value = 0L
        var multiplier = 1L
        do {
            val byte = buffer.readByte().toInt()
            value += (byte and 127) * multiplier
            multiplier *= 128L
            if (multiplier > MULTIPLIER_MAX)
                throw IOException("Invalid variable length")
        } while ((byte and 128) != 0)
        return value
    }

    fun encodeVariableLength(buffer: BufferedSink, value: Long) {
        var encoding = value
        do {
            var byteToEncode = encoding % 128L

            encoding /= 128L
            if (encoding > 0) {
                byteToEncode = byteToEncode or 128L
            }
            buffer.writeByte(byteToEncode.toInt())
        } while (encoding > 0)
    }

}

internal fun ByteArray.wrap(): BufferedSource {
    val bufferSource = this
    return object : Source {

        private var offset = 0

        override fun close() = Unit

        override fun timeout(): Timeout = Timeout.NONE

        override fun read(sink: Buffer, byteCount: Long): Long {
            val start = offset
            val end = kotlin.math.min(size.toLong(), offset + byteCount).toInt()
            val toWrite = end - start
            if (toWrite == 0)
                return 0

            sink.write(bufferSource, start, toWrite)
            offset += toWrite
            return toWrite.toLong()
        }
    }.buffer()
}