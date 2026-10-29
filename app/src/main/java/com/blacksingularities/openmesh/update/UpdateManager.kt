package com.blacksingularities.openmesh.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.blacksingularities.openmesh.BuildConfig
import com.blacksingularities.openmesh.R
import com.blacksingularities.openmesh.mesh.MeshService
import com.blacksingularities.openmesh.mesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aktualizacje z GitHub Releases: sprawdzenie najnowszego wydania, pobranie APK i instalacja
 * przez PackageInstaller. Od Androida 12, gdy aplikacja sama zainstalowała poprzednią wersję,
 * system aktualizuje ją bez dodatkowego potwierdzenia.
 */
object UpdateManager {
    val API = "https://redzikowski.com/api/github/repos/" + BuildConfig.UPDATE_REPO + "/releases/latest"
    const val ACTION_STATUS = "com.blacksingularities.openmesh.UPDATE_STATUS"
    private const val RESTART_KEY = "update.restart"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 4711

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Czy jest połączenie z internetem (w sieci mesh zwykle go nie ma – wtedy nie próbujemy). */
    fun online(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OpenMesh/" + BuildConfig.VERSION_NAME)
        }
        try {
            if (conn.responseCode == 404) error("Brak opublikowanych wydań")
            if (conn.responseCode != 200) error("Serwer odpowiedział kodem " + conn.responseCode)
            parseRelease(conn.inputStream.bufferedReader().use { it.readText() }, ".apk")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(context: Context, info: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "OpenMesh-${info.version}.apk")
        val part = File(dir, target.name + ".part")
        val conn = (URL(info.assetUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OpenMesh/" + BuildConfig.VERSION_NAME)
        }
        try {
            if (conn.responseCode !in 200..299) error("Pobieranie nie powiodło się (" + conn.responseCode + ")")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0 && read - lastReport > 128 * 1024) {
                            lastReport = read
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (info.sizeBytes > 0 && part.length() != info.sizeBytes) error("Plik pobrany niekompletnie – spróbuj ponownie")
            if (!part.renameTo(target)) error("Nie można zapisać pliku aktualizacji")
            onProgress(1f)
            target
        } finally {
            conn.disconnect()
        }
    }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** Cicha aktualizacja jest możliwa od Androida 12, gdy bieżącą wersję zainstalowała sama aplikacja. */
    fun canInstallSilently(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31 || !canInstall(context)) return false
        val installer = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull()
        return installer == context.packageName
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    suspend fun install(context: Context, file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        context.getSharedPreferences("openmesh.update", Context.MODE_PRIVATE).edit()
            .putLong(RESTART_KEY, System.currentTimeMillis()).apply()
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    /** Po aktualizacji: ponowne uruchomienie aplikacji i sieci (z tła Android może zablokować otwarcie – wtedy powiadomienie). */
    fun relaunchAfterUpdate(context: Context) {
        val s = context.mesh.settings.value
        if (s.onboarded && s.meshEnabled) MeshService.start(context)
        val prefs = context.getSharedPreferences("openmesh.update", Context.MODE_PRIVATE)
        val requested = prefs.getLong(RESTART_KEY, 0L)
        if (requested == 0L || System.currentTimeMillis() - requested > 30 * 60_000L) return
        prefs.edit().remove(RESTART_KEY).apply()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
        runCatching { context.startActivity(launch) }
        notifyUpdated(context, launch)
    }

    private fun notifyUpdated(context: Context, launch: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Aktualizacje", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mesh)
            .setContentTitle("OpenMesh zaktualizowany do " + BuildConfig.VERSION_NAME)
            .setContentText("Dotknij, aby otworzyć aplikację")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    fun clearUpdatedNotification(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
    }
}

/** Status instalacji; gdy system wymaga potwierdzenia – pokazuje jego okno. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateManager.relaunchAfterUpdate(context)
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "kod $status"
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    Toast.makeText(context, "Aktualizacja nieudana: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/** Nowa wersja zainstalowana – system wysyła to do zaktualizowanej aplikacji. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateManager.relaunchAfterUpdate(context)
    }
}
