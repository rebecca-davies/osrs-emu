package emu.transport.prot

data class Prot(val opcode: Int, val size: Int) {
    companion object {
        const val VAR_BYTE = -1
        const val VAR_SHORT = -2
    }
}
