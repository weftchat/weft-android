package app.weft

import android.app.Application
import app.weft.data.AutoLock
import app.weft.data.CoreChats
import app.weft.data.CoreProfile
import app.weft.data.WeftSession

/** Process start: the session and the repositories that follow the core. */
class WeftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WeftSession.init(this)
        CoreProfile.start(this)
        CoreChats.start()
        AutoLock.install(this)
    }
}
