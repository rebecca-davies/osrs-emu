package emu.transport.pipeline.outbound

import emu.transport.prot.Prot

/** One encoded message body retained with the metadata needed to frame it exactly once. */
class EncodedMessageBody internal constructor(
    internal val prot: Prot,
    internal val bytes: ByteArray,
)
