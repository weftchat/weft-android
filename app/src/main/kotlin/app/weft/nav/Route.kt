package app.weft.nav

/** Every screen of design v3. Only the ones built so far are listed. */
sealed class Route(val key: String) {
    data object Onboard : Route("onboard")
    data class Pin(val mode: PinMode) : Route("pin-$mode")
}

enum class PinMode { Choose, Repeat, Enter }
