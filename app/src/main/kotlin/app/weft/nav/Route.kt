package app.weft.nav

/** Every screen of design v3. Only the ones built so far are listed. */
sealed class Route(val key: String) {
    data object Onboard : Route("onboard")
    data class Pin(val mode: PinMode, val purpose: PinPurpose = PinPurpose.Own) : Route("pin-$mode-$purpose")
    data object Chats : Route("chats")
    data object Add : Route("add")
    data object Security : Route("security")
    data object Profile : Route("profile")
    data class Conversation(val chatId: String) : Route("convo-$chatId")
    data class GroupChat(val chatId: String, val name: String) : Route("group-$chatId")
    data object NewGroup : Route("newgroup")
    data object Wipe : Route("wipe")
    data object Scan : Route("scan")
}

/** Choose and Repeat are one screen: Choose moves on to Repeat by itself. */
enum class PinMode { Choose, Repeat, Enter }

/** What the code being chosen is for. */
enum class PinPurpose { Own, Duress, Panic }

/** The four tab-bar screens, in bar order. */
val TABS: List<Route> = listOf(Route.Chats, Route.Add, Route.Security, Route.Profile)
