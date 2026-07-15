package emu.server.session

/** Reason a world reservation was rejected. */
enum class ReservationRejection {
    DUPLICATE,
    CAPACITY,
    UNAVAILABLE,
}
