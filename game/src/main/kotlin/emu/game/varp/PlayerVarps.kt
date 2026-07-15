package emu.game.varp

/**
 * Live account varps with full login synchronization, sparse client updates, and save-point
 * tracking for permanent values. Performs no database or network I/O.
 */
class PlayerVarps(
    private val catalog: VarpCatalog,
    savedValues: Map<Int, Int> = emptyMap(),
) {
    private val values = HashMap<Int, Int>()
    private val persistedBaseline = HashMap<Int, Int>()
    private val pendingClient = LinkedHashMap<Int, Int>()
    private var clientSynchronized = false

    init {
        for (type in catalog.types) {
            val value =
                if (type.scope == VarpScope.PERMANENT) savedValues[type.id] ?: 0
                else 0
            values[type.id] = value
            if (type.scope == VarpScope.PERMANENT) persistedBaseline[type.id] = value
        }
    }

    operator fun get(type: VarpType): Int {
        require(catalog[type.id] == type) { "varp ${type.id} is not in this catalog" }
        return values.getValue(type.id)
    }

    operator fun set(type: VarpType, value: Int) {
        require(catalog[type.id] == type) { "varp ${type.id} is not in this catalog" }
        val previous = values.put(type.id, value)
        if (!clientSynchronized || type.transmit == VarpTransmit.NEVER) return
        if (type.transmit == VarpTransmit.ALWAYS || previous != value) pendingClient[type.id] = value
    }

    operator fun get(type: VarbitType): Int {
        val base = this[type.baseVar]
        val width = type.bits.count()
        val mask = if (width == 32) -1 else (1 shl width) - 1
        return (base ushr type.bits.first) and mask
    }

    operator fun set(type: VarbitType, value: Int) {
        require(value.toLong() in 0..type.maxValue) {
            "varbit ${type.id} value $value is outside 0..${type.maxValue}"
        }
        val width = type.bits.count()
        val unshiftedMask = if (width == 32) -1 else (1 shl width) - 1
        val mask = unshiftedMask shl type.bits.first
        this[type.baseVar] = (this[type.baseVar] and mask.inv()) or ((value shl type.bits.first) and mask)
    }

    /** Complete, deterministic login state sent after VARP_RESET. */
    fun loginSync(): List<VarpValue> =
        catalog.types
            .asSequence()
            .filter { it.transmit != VarpTransmit.NEVER }
            .map { VarpValue(it.id, values.getValue(it.id)) }
            .toList()

    /** Completes login synchronization and enables sparse client updates. */
    fun markClientSynchronized() {
        pendingClient.clear()
        clientSynchronized = true
    }

    fun drainClientUpdates(): List<VarpValue> =
        pendingClient.map { VarpValue(it.key, it.value) }.also { pendingClient.clear() }

    /** Sparse permanent values whose effective value differs from the last database load. */
    fun dirtyPersistentValues(): Map<Int, Int> =
        catalog.types
            .asSequence()
            .filter { it.scope == VarpScope.PERMANENT }
            .filter { values.getValue(it.id) != persistedBaseline.getValue(it.id) }
            .associate { it.id to values.getValue(it.id) }
}
