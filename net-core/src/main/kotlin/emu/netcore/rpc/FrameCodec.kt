package emu.netcore.rpc

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeInt

class FrameTooLargeException(len: Int, max: Int) :
    RuntimeException("frame length $len exceeds max $max")

suspend fun ByteWriteChannel.writeFrame(payload: ByteArray) {
    writeInt(payload.size)
    writeFully(payload)
    flush()
}

suspend fun ByteReadChannel.readFrame(maxLen: Int = 1 shl 20): ByteArray {
    val len = readInt()
    if (len < 0 || len > maxLen) throw FrameTooLargeException(len, maxLen)
    val buf = ByteArray(len)
    readFully(buf)
    return buf
}
