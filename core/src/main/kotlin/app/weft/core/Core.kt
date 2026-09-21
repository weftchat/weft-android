package app.weft.core

// Thin bindings to the SimpleX chat core. Names and signatures must match weft-jni.c exactly.
// All values cross the boundary as JSON strings; parsing lives in ChatController (Phase 1).

/** Starts the Haskell runtime. Call once per process, before anything else. */
external fun initHS()

/** Opens (creating or migrating) the encrypted store. Returns [result JSON string, controller handle as Long]. */
external fun chatMigrateInit(dbPath: String, dbKey: String, confirm: String): Array<Any>

external fun chatCloseStore(ctrl: Long): String

external fun chatSendCmdRetry(ctrl: Long, cmd: String, retryNum: Int): String

/** Blocks up to [waitMicros] µs for the next event; returns an empty string on timeout. */
external fun chatRecvMsgWait(ctrl: Long, waitMicros: Int): String

external fun chatParseUri(uri: String, safe: Int): String

external fun chatParseServer(server: String): String

external fun chatValidName(name: String): String

/** Loads the native libraries. `weft-jni` links libsimplex.so and libsupport.so, so they load with it. */
object NativeCore {
    @Volatile private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        System.loadLibrary("weft-jni")
        initHS()
        loaded = true
    }
}
