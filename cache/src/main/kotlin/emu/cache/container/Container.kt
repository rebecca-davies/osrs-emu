package emu.cache.container

import emu.buffer.JagexBuffer
import emu.crypto.Xtea

/**
 * Decompressed JS5 payload and the envelope metadata needed to re-encode it.
 */
class Container(
    val data: ByteArray,
    val compression: Js5Compression,
    val revision: Int = NO_REVISION,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Container) return false
        return compression == other.compression && revision == other.revision && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + compression.hashCode()
        result = 31 * result + revision
        return result
    }

    override fun toString(): String = "Container(compression=$compression, revision=$revision, size=${data.size})"

    companion object {
        /** Sentinel for an absent version trailer. */
        const val NO_REVISION: Int = -1

        /**
         * Decodes the JS5 envelope: `[compression u8][compressedLength i32][(uncompressedLength
         * i32)][payload][(version u16/i32)]`. XTEA (skipped when [key] is [XteaKey.isZero]) covers
         * the payload from offset 5 through `compressedLength` (NONE) or `compressedLength + 4`
         * (BZIP2/GZIP) bytes — the latter range includes the uncompressed-length int.
         */
        fun decode(bytes: ByteArray, key: XteaKey = XteaKey.ZERO): Container {
            val buf = JagexBuffer(bytes)
            val compression = Js5Compression.fromId(buf.readUByte())
            val compressedLength = buf.readInt()
            require(compressedLength >= 0) { "Invalid compressed length: $compressedLength" }

            val data: ByteArray = when (compression) {
                Js5Compression.NONE -> {
                    val encrypted = buf.readBytes(compressedLength)
                    decrypt(encrypted, key)
                }

                Js5Compression.BZIP2, Js5Compression.GZIP -> {
                    val encrypted = buf.readBytes(compressedLength + 4)
                    val decrypted = decrypt(encrypted, key)
                    val inner = JagexBuffer(decrypted)
                    val uncompressedLength = inner.readInt()
                    val payload = inner.readBytes(decrypted.size - 4)
                    val decompressed = if (compression == Js5Compression.BZIP2) {
                        Js5Compressor.bunzip2(payload)
                    } else {
                        Js5Compressor.gunzip(payload)
                    }
                    check(decompressed.size == uncompressedLength) {
                        "Decompressed size mismatch: expected $uncompressedLength, got ${decompressed.size}"
                    }
                    decompressed
                }
            }

            val revision = when {
                buf.readableBytes() >= 4 -> buf.readInt()
                buf.readableBytes() >= 2 -> buf.readUShort()
                else -> NO_REVISION
            }

            return Container(data, compression, revision)
        }

        /**
         * Encodes [data] under [compression] (mirroring `Container.compress`'s write order),
         * applying XTEA with [key] over the same region [decode] reads it from, and appending the
         * 2-byte version trailer when [revision] is present.
         */
        fun encode(
            compression: Js5Compression,
            data: ByteArray,
            key: XteaKey = XteaKey.ZERO,
            revision: Int = NO_REVISION,
        ): ByteArray {
            val compressedPayload: ByteArray
            val length: Int
            when (compression) {
                Js5Compression.NONE -> {
                    compressedPayload = data
                    length = compressedPayload.size
                }

                Js5Compression.BZIP2 -> {
                    compressedPayload = intToBytes(data.size) + Js5Compressor.bzip2(data)
                    length = compressedPayload.size - 4
                }

                Js5Compression.GZIP -> {
                    compressedPayload = intToBytes(data.size) + Js5Compressor.gzip(data)
                    length = compressedPayload.size - 4
                }
            }

            val encrypted = encrypt(compressedPayload, key)
            val hasRevision = revision != NO_REVISION

            val out = JagexBuffer.alloc(1 + 4 + encrypted.size + if (hasRevision) 2 else 0)
            out.writeByte(compression.id)
            out.writeInt(length)
            out.writeBytes(encrypted)
            if (hasRevision) out.writeShort(revision)
            return out.array
        }

        private fun decrypt(bytes: ByteArray, key: XteaKey): ByteArray =
            if (key.isZero) bytes else Xtea.decrypt(bytes, key.toIntArray())

        private fun encrypt(bytes: ByteArray, key: XteaKey): ByteArray =
            if (key.isZero) bytes else Xtea.encrypt(bytes, key.toIntArray())

        private fun intToBytes(value: Int): ByteArray = byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )
    }
}
