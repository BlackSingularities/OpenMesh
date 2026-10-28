package com.blacksingularities.openmesh.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class GeoPoint(val lat: Double, val lon: Double, val accuracy: Float? = null)

/** Pozycja z GPS (działa bez internetu), odległości i kierunki do innych węzłów. */
object Geo {
    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Bieżąca pozycja: świeża ostatnia znana (do 2 min) albo nowy odczyt, maksymalnie [timeoutMs]. */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context, timeoutMs: Long = 25_000): GeoPoint? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null
        val now = System.currentTimeMillis()
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && now - last.time < 2 * 60_000) return last.toPoint()

        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                providers.filter { it != LocationManager.PASSIVE_PROVIDER }.forEach {
                    runCatching { lm.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
        return (fresh ?: last)?.toPoint()
    }

    private fun Location.toPoint() = GeoPoint(latitude, longitude, if (hasAccuracy()) accuracy else null)

    fun distance(a: GeoPoint, b: GeoPoint): Float {
        val r = FloatArray(2)
        Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, r)
        return r[0]
    }

    fun bearing(a: GeoPoint, b: GeoPoint): Float {
        val r = FloatArray(2)
        Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, r)
        return (r[1] + 360f) % 360f
    }

    fun formatDistance(m: Float): String = when {
        m < 1000 -> "${m.roundToInt()} m"
        m < 10_000 -> String.format(Locale("pl"), "%.1f km", m / 1000f)
        else -> "${(m / 1000f).roundToInt()} km"
    }

    private val directions = listOf("pn.", "pn.-wsch.", "wsch.", "pd.-wsch.", "pd.", "pd.-zach.", "zach.", "pn.-zach.")

    fun direction(deg: Float): String = directions[(((deg + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]

    fun formatCoords(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)

    /** Zapis w stopniach, minutach i sekundach – wygodny do podania przez radio lub ratownikom. */
    fun formatDms(lat: Double, lon: Double): String {
        fun part(v: Double, pos: String, neg: String): String {
            val a = kotlin.math.abs(v)
            val d = a.toInt()
            val mFull = (a - d) * 60
            val m = mFull.toInt()
            val s = (mFull - m) * 60
            return String.format(Locale.US, "%d°%02d'%04.1f\"%s", d, m, s, if (v >= 0) pos else neg)
        }
        return part(lat, "N", "S") + " " + part(lon, "E", "W")
    }

    /** Odnośnik geo: – otwiera aplikację map, także offline (OsmAnd, Organic Maps, Mapy Google z pobraną mapą). */
    fun geoUri(lat: Double, lon: Double, label: String) =
        "geo:$lat,$lon?q=$lat,$lon(" + android.net.Uri.encode(label) + ")"
}
