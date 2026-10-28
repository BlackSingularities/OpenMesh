package com.blacksingularities.openmesh.ui

import com.blacksingularities.openmesh.mesh.Notifier.plural
import com.blacksingularities.openmesh.mesh.Peer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val pl = Locale("pl", "PL")

fun clock(ts: Long): String = SimpleDateFormat("HH:mm", pl).format(Date(ts))

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

fun isSameDay(a: Long, b: Long) = sameDay(a, b)

/** Godzina dla dzisiejszych, „wczoraj” albo data dla starszych – lista rozmów. */
fun shortWhen(ts: Long, now: Long = System.currentTimeMillis()): String = when {
    sameDay(ts, now) -> clock(ts)
    sameDay(ts, now - 86_400_000L) -> "wczoraj"
    else -> SimpleDateFormat("d MMM", pl).format(Date(ts))
}

/** Nagłówek dnia w rozmowie. */
fun dayHeader(ts: Long, now: Long = System.currentTimeMillis()): String = when {
    sameDay(ts, now) -> "Dzisiaj"
    sameDay(ts, now - 86_400_000L) -> "Wczoraj"
    else -> SimpleDateFormat("EEEE, d MMMM", pl).format(Date(ts)).replaceFirstChar { it.uppercase() }
}

fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
    val s = ((now - ts) / 1000).coerceAtLeast(0)
    return when {
        s < 45 -> "przed chwilą"
        s < 3600 -> "${s / 60} min temu"
        s < 86_400 -> (s / 3600).toInt().let { "$it " + plural(it, "godz.", "godz.", "godz.") + " temu" }
        else -> (s / 86_400).toInt().let { "$it " + plural(it, "dzień", "dni", "dni") + " temu" }
    }
}

fun hopsLabel(hops: Int): String = when (hops) {
    0 -> "bezpośrednio"
    else -> "przez $hops " + plural(hops, "węzeł", "węzły", "węzłów")
}

const val ONLINE_WINDOW = 3 * 60_000L

fun Peer.isOnline(now: Long = System.currentTimeMillis()) = now - lastSeen < ONLINE_WINDOW
