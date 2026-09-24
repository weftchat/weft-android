package app.weft

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.weft.data.Background

/**
 * Keeps Weft's process alive so the core stays connected to the relays (BRIEF, Phase 3). It does
 * no work itself: the core, in the same process, receives. Started and stopped by [Background].
 */
class WeftService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notice = Background.runningNotice(this)
        if (Build.VERSION.SDK_INT >= 34) startForeground(Background.ID_RUNNING, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        else startForeground(Background.ID_RUNNING, notice)
        return START_NOT_STICKY // after a restart there is no key: nothing to keep alive
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
