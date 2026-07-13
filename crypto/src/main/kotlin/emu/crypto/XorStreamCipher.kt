package emu.crypto

// A byte-XOR keystream cipher: every call to nextInt() returns the same constant key, so callers
// XOR each outgoing byte with it. Generic and protocol-agnostic — named by algorithm like the other
// primitives in this module (Isaac/Xtea/Rsa). JS5-specific wiring (the fact that control opcode 4
// sets this connection's key) lives in the gateway/protocol layer, not here — see
// emu.gateway.js5.Js5Handler. A key of 0 is a no-op (plaintext).
class XorStreamCipher(@Volatile var key: Int = 0) : StreamCipher {
    override fun nextInt(): Int = key
}
