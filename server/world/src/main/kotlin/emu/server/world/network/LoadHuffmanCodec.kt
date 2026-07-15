package emu.server.world.network

import emu.cache.container.Container
import emu.cache.store.Store
import emu.compression.HuffmanCodec

/** Loads the revision-pinned Huffman table from its cache archive and group. */
fun loadHuffmanCodec(store: Store): HuffmanCodec {
    val container = requireNotNull(store.read(BINARY_ARCHIVE, HUFFMAN_GROUP)) { "cache huffman group is missing" }
    return HuffmanCodec(Container.decode(container).data)
}

private const val BINARY_ARCHIVE = 10
private const val HUFFMAN_GROUP = 1
