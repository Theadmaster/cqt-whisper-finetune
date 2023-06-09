package com.yeyupiaoling.whisper

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun decodeWaveFile(file: File): FloatArray {
    val baos = ByteArrayOutputStream()
    file.inputStream().use { it.copyTo(baos) }
    val buffer = ByteBuffer.wrap(baos.toByteArray())
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    val channel = buffer.getShort(22).toInt()
    buffer.position(44)
    val shortBuffer = buffer.asShortBuffer()
    val shortArray = ShortArray(shortBuffer.limit())
    shortBuffer.get(shortArray)
    return FloatArray(shortArray.size / channel) { index ->
        when (channel) {
            1 -> (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
            else -> ((shortArray[2 * index] + shortArray[2 * index + 1]) / 32767.0f / 2.0f).coerceIn(
                -1f..1f
            )
        }
    }
}


@Throws(IOException::class)
fun writeWAVHeader(
    fos: FileOutputStream, totalAudioLen: Long, totalDataLen: Long,
    sampleRate: Int, channels: Int, bitRate: Int
) {
    val byteRate = bitRate.toLong() * channels * sampleRate / 8
    val header = ByteArray(44)
    header[0] = 'R'.code.toByte() // RIFF/WAVE header
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    header[4] = (totalDataLen and 0xffL).toByte()
    header[5] = (totalDataLen shr 8 and 0xffL).toByte()
    header[6] = (totalDataLen shr 16 and 0xffL).toByte()
    header[7] = (totalDataLen shr 24 and 0xffL).toByte()
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte() // 'fmt ' chunk
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16 // 4 bytes: size of 'fmt ' chunk
    header[17] = 0
    header[18] = 0
    header[19] = 0
    header[20] = 1 // format = 1
    header[21] = 0
    header[22] = channels.toByte()
    header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = (sampleRate shr 8 and 0xff).toByte()
    header[26] = (sampleRate shr 16 and 0xff).toByte()
    header[27] = (sampleRate shr 24 and 0xff).toByte()
    header[28] = (byteRate and 0xffL).toByte()
    header[29] = (byteRate shr 8 and 0xffL).toByte()
    header[30] = (byteRate shr 16 and 0xffL).toByte()
    header[31] = (byteRate shr 24 and 0xffL).toByte()
    header[32] = (channels * bitRate / 8).toByte()
    header[33] = 0
    header[34] = bitRate.toByte()
    header[35] = 0
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    header[40] = (totalAudioLen and 0xffL).toByte()
    header[41] = (totalAudioLen shr 8 and 0xffL).toByte()
    header[42] = (totalAudioLen shr 16 and 0xffL).toByte()
    header[43] = (totalAudioLen shr 24 and 0xffL).toByte()
    fos.write(header, 0, 44)
}


fun encodeWaveFile(file: File, data: ShortArray) {
    file.outputStream().use {
        it.write(headerBytes(data.size * 2))
        val buffer = ByteBuffer.allocate(data.size * 2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(data)
        val bytes = ByteArray(buffer.limit())
        buffer.get(bytes)
        it.write(bytes)
    }
}

private fun headerBytes(totalLength: Int): ByteArray {
    require(totalLength >= 44)
    ByteBuffer.allocate(44).apply {
        order(ByteOrder.LITTLE_ENDIAN)

        put('R'.code.toByte())
        put('I'.code.toByte())
        put('F'.code.toByte())
        put('F'.code.toByte())

        putInt(totalLength - 8)

        put('W'.code.toByte())
        put('A'.code.toByte())
        put('V'.code.toByte())
        put('E'.code.toByte())

        put('f'.code.toByte())
        put('m'.code.toByte())
        put('t'.code.toByte())
        put(' '.code.toByte())

        putInt(16)
        putShort(1.toShort())
        putShort(1.toShort())
        putInt(16000)
        putInt(32000)
        putShort(2.toShort())
        putShort(16.toShort())

        put('d'.code.toByte())
        put('a'.code.toByte())
        put('t'.code.toByte())
        put('a'.code.toByte())

        putInt(totalLength - 44)
        position(0)
    }.also {
        val bytes = ByteArray(it.limit())
        it.get(bytes)
        return bytes
    }
}

fun getPathFromURI(context: Context, uri: Uri): String? {
    val result: String?
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    if (cursor == null) {
        result = uri.path
    } else {
        cursor.moveToFirst()
        val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
        result = cursor.getString(idx)
        cursor.close()
    }
    return result
}
