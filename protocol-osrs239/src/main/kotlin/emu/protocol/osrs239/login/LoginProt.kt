package emu.protocol.osrs239.login

import emu.netcore.prot.Prot

// Login opcode/size table for OSRS rev 239.
// See docs/superpowers/research/2026-07-14-rev239-login-facts.md §1.
object LoginProt {
    val INIT = Prot(14, 0)                       // login init: single opcode byte, no payload
    val NEW_LOGIN = Prot(16, Prot.VAR_SHORT)      // new-login block: u16 length + RSA/XTEA payload
    val RECONNECT = Prot(18, Prot.VAR_SHORT)      // reconnect block: same framing as NEW_LOGIN

    // Server->client login replies (ServerSessionKey, LoginResponse) carry no wire opcode of their
    // own — the client's login state machine reads them positionally, not via opcode-framed
    // dispatch. OUTGOING is a registry-only sentinel so CodecRepositoryBuilder can key encoders by
    // message type; same pattern as Js5Prot.GROUP_RESPONSE. The gateway writes their encoded bytes
    // raw (see ProtocolStage's writeOpcode = false usage for JS5).
    val OUTGOING = Prot(-1, Prot.VAR_BYTE)
}
