package com.blacksingularities.openmesh.mesh

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.blacksingularities.openmesh.mesh
import com.blacksingularities.openmesh.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Usługa pierwszoplanowa: utrzymuje radio i połączenia, gdy aplikacja jest w tle lub ekran wyłączony,
 * i pokazuje powiadomienia o wiadomościach oraz alarmach SOS.
 */
class MeshService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        val core = mesh
        startInForeground(Notifier.service(this, 0, 0, LinkState.Starting))
        core.startMesh()

        scope.launch {
            combine(core.transport.links, core.transport.nearby, core.transport.state) { l, n, s -> Triple(l.size, n, s) }
                .distinctUntilChanged()
                .collect { (links, nearby, state) ->
                    getSystemService(NotificationManager::class.java)
                        ?.notify(Notifier.SERVICE_ID, Notifier.service(this@MeshService, links, nearby, state))
                }
        }
        scope.launch {
            core.events.collect { e ->
                val s = core.settings.value
                when (e) {
                    is MeshEvent.Incoming -> {
                        if (s.notify && !(core.appVisible && core.activeConvo == e.message.convo)) {
                            Notifier.message(this@MeshService, e.message, e.title, s.vibrate && !core.appVisible)
                        }
                    }
                    is MeshEvent.Sos -> Notifier.sos(this@MeshService, e.message, s.sosAlarm)
                    is MeshEvent.Notice -> Unit
                }
            }
        }
        // Krótka blokada uśpienia CPU przy aktywnych połączeniach – przekazywanie pakietów przy wygaszonym ekranie.
        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenMesh:relay")
            .apply { setReferenceCounted(false) }
        scope.launch {
            core.transport.links.collect { links ->
                if (links.isNotEmpty() && core.settings.value.relay) {
                    wake?.acquire(60 * 60_000L)
                } else if (wake?.isHeld == true) {
                    wake?.release()
                }
            }
        }
    }

    private fun startInForeground(n: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        runCatching { ServiceCompat.startForeground(this, Notifier.SERVICE_ID, n, type) }
            .onFailure { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        mesh.startMesh()
        return START_STICKY
    }

    private fun stopEverything() {
        mesh.stopMesh()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        if (wake?.isHeld == true) wake?.release()
        mesh.stopMesh()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.blacksingularities.openmesh.STOP"

        fun start(ctx: Context) {
            if (!Permissions.meshGranted(ctx)) return
            runCatching { ctx.startForegroundService(Intent(ctx, MeshService::class.java)) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, MeshService::class.java).setAction(ACTION_STOP)) }
                .onFailure { ctx.mesh.stopMesh() }
        }
    }
}

/** Uruchomienie sieci po starcie telefonu i po aktualizacji aplikacji. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = context.mesh.settings.value
        if (s.onboarded && s.meshEnabled && s.autoStart) MeshService.start(context)
    }
}

/** Przycisk „Zatrzymaj sieć” w powiadomieniu. */
class ServiceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            context.mesh.updateSettings { it.copy(meshEnabled = false) }
            MeshService.stop(context)
        }
    }

    companion object {
        const val ACTION_STOP = "com.blacksingularities.openmesh.NOTIFICATION_STOP"
    }
}
