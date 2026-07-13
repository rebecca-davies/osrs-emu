package emu.crypto

// JS5 response obfuscation. After the handshake the client may send control opcode 4 with a single
// key byte; the server must XOR every subsequent outgoing byte with that key. A key of 0 means
// plaintext (the client's initial/urgent connection typically never sets a key). The key is a
// constant for the life of the connection, so nextInt() returns it unchanged on every call.
class Js5XorCipher(@Volatile var key: Int = 0) : StreamCipher {
    override fun nextInt(): Int = key
}
