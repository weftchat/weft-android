package app.weft.data

/**
 * How the core reaches relays (BRIEF, Phase 3). The core keeps this setting in memory only, so it is
 * applied every time the core starts, before `/_start`.
 *
 * Private routing, always: a message goes to the contact's relay through another relay, so that
 * relay never sees this phone's IP address. When forwarding fails the core may send directly only
 * if the IP address is protected (Tor on); otherwise it keeps retrying.
 */
object CoreNetwork {
    suspend fun apply() {
        val cfg = WeftSession.cmd("/network").getJSONObject("networkConfig")
            .put("smpProxyMode", "always")
            .put("smpProxyFallback", "allowProtected")
        WeftSession.cmd("/_network $cfg")
    }
}
