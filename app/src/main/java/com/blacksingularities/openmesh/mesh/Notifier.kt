package com.blacksingularities.openmesh.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.blacksingularities.openmesh.MainActivity
import com.blacksingularities.openmesh.R
import com.blacksingularities.openmesh.util.Geo
import com.blacksingularities.openmesh.util.Permissions

/** Powiadomienia: stała usługa sieci, nowe wiadomości i alarm SOS. */
object Notifier {
    const val CH_SERVICE = "mesh.service"
    const val CH_MESSAGES = "mesh.messages"
    const val CH_SOS = "mesh.sos"
    const val SERVICE_ID = 1
    private const val SOS_BASE = 50_000
    const val EXTRA_CONVO = "convo"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Sieć w tle", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Stałe powiadomienie, gdy OpenMesh utrzymuje połączenia"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MESSAGES, "Wiadomości", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Nowe wiadomości z kanałów i rozmów prywatnych"
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SOS, "Alarm SOS", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Wezwania pomocy od osób w pobliżu"
                enableVibration(true)
                vibrationPattern = SOS_PATTERN
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun openIntent(ctx: Context, convo: String?, code: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (convo != null) putExtra(EXTRA_CONVO, convo) }
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun service(ctx: Context, links: Int, nearby: Int, state: LinkState): Notification {
        val text = when (state) {
            is LinkState.Error -> state.message
            LinkState.Starting -> "Uruchamianie radia…"
            LinkState.Off -> "Sieć zatrzymana"
            LinkState.On -> when {
                links > 0 -> "Połączono: $links " + plural(links, "urządzenie", "urządzenia", "urządzeń")
                nearby > 0 -> "Łączenie z urządzeniami w pobliżu ($nearby)…"
                else -> "Szukanie urządzeń w pobliżu…"
            }
        }
        val stop = PendingIntent.getBroadcast(
            ctx,
            1,
            Intent(ctx, ServiceActionReceiver::class.java).setAction(ServiceActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_mesh)
            .setContentTitle("OpenMesh aktywny")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openIntent(ctx, null, 0))
            .addAction(Notification.Action.Builder(null, "Zatrzymaj sieć", stop).build())
            .build()
    }

    fun message(ctx: Context, m: Message, title: String, vibrate: Boolean) {
        if (!Permissions.notificationsGranted(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val body = when {
            m.hasLocation && m.text.isBlank() -> "📍 Udostępniono pozycję"
            m.hasLocation -> "📍 " + m.text
            m.isImage -> "📷 Zdjęcie"
            m.hasAttachment -> "📎 " + (m.attName ?: "Plik")
            else -> m.text
        }
        val n = Notification.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_mesh)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setGroup(m.convo)
            .setWhen(m.ts)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(openIntent(ctx, m.convo, m.convo.hashCode()))
            .build()
        runCatching { nm.notify(m.convo.hashCode(), n) }
        if (vibrate) vibrate(ctx, longArrayOf(0, 60, 80, 60))
    }

    fun sos(ctx: Context, m: Message, alarm: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val where = if (m.lat != null && m.lon != null) "\n📍 " + Geo.formatCoords(m.lat, m.lon) else "\nBrak pozycji"
        val body = m.text + where
        if (Permissions.notificationsGranted(ctx)) {
            val n = Notification.Builder(ctx, CH_SOS)
                .setSmallIcon(R.drawable.ic_stat_mesh)
                .setContentTitle("🆘 SOS od " + m.fromName)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setColor(0xFFFF3B47.toInt())
                .setAutoCancel(true)
                .setContentIntent(openIntent(ctx, m.convo, SOS_BASE + m.id.hashCode()))
                .setFullScreenIntent(openIntent(ctx, m.convo, SOS_BASE + 1 + m.id.hashCode()), true)
                .build()
            runCatching { nm.notify(SOS_BASE + (m.id.hashCode() and 0xFFF), n) }
        }
        if (alarm) vibrate(ctx, SOS_PATTERN)
    }

    fun cancelConversation(ctx: Context, convo: String) {
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.cancel(convo.hashCode()) }
    }

    private val SOS_PATTERN = longArrayOf(0, 400, 200, 400, 200, 400, 600, 900, 300, 900, 300, 900, 600, 400, 200, 400, 200, 400)

    fun vibrate(ctx: Context, pattern: LongArray) {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            n == 1 -> one
            mod10 in 2..4 && mod100 !in 12..14 -> few
            else -> many
        }
    }
}
