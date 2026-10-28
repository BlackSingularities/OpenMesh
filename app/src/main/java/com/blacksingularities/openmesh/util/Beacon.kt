package com.blacksingularities.openmesh.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.blacksingularities.openmesh.mesh.BeaconPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Latarka alarmowa: miga tylną diodą (i opcjonalnie ekranem) wzorem widocznym z dużej odległości.
 * Działa bez sieci – to ostatnia warstwa sygnalizacji, gdy nie ma już nikogo w zasięgu mesh.
 */
object Beacon {
    /** Czas w milisekundach: światło włączone / wyłączone, naprzemiennie. */
    private val patterns = mapOf(
        // Międzynarodowy sygnał SOS alfabetem Morse'a: ··· ––– ··· i przerwa.
        BeaconPattern.SOS to longArrayOf(
            200, 200, 200, 200, 200, 600,
            600, 200, 600, 200, 600, 600,
            200, 200, 200, 200, 200, 2400,
        ),
        // Sygnał alpejski: sześć mignięć na minutę (norma ratownictwa górskiego).
        BeaconPattern.SLOW to longArrayOf(500, 9500),
        BeaconPattern.FAST to longArrayOf(120, 380),
        BeaconPattern.STEADY to longArrayOf(1_000, 0),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    /** Stan światła w bieżącej chwili – ekran migający w rytm latarki. */
    private val _lit = MutableStateFlow(false)
    val lit: StateFlow<Boolean> = _lit

    fun hasTorch(ctx: Context): Boolean = torchId(ctx) != null

    fun toggle(ctx: Context, pattern: BeaconPattern) {
        if (_running.value) stop(ctx) else start(ctx, pattern)
    }

    fun start(ctx: Context, pattern: BeaconPattern) {
        val app = ctx.applicationContext
        job?.cancel()
        _running.value = true
        val steps = patterns[pattern] ?: patterns.getValue(BeaconPattern.SOS)
        job = scope.launch {
            var i = 0
            try {
                while (isActive) {
                    val on = i % 2 == 0
                    val ms = steps[i % steps.size]
                    if (ms > 0) {
                        torch(app, on)
                        _lit.value = on
                        delay(ms)
                    }
                    i++
                }
            } finally {
                torch(app, false)
                _lit.value = false
            }
        }
    }

    fun stop(ctx: Context) {
        job?.cancel()
        job = null
        _running.value = false
        _lit.value = false
        torch(ctx.applicationContext, false)
    }

    private fun torch(ctx: Context, on: Boolean) {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return
        val id = torchId(ctx) ?: return
        runCatching { cm.setTorchMode(id, on) }
    }

    private fun torchId(ctx: Context): String? {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return null
        return runCatching {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    fun label(pattern: BeaconPattern): String = when (pattern) {
        BeaconPattern.SOS -> "SOS alfabetem Morse'a"
        BeaconPattern.SLOW -> "Sygnał alpejski (6/min)"
        BeaconPattern.FAST -> "Szybkie mignięcia"
        BeaconPattern.STEADY -> "Światło ciągłe"
    }

    fun description(pattern: BeaconPattern): String = when (pattern) {
        BeaconPattern.SOS -> "··· ––– ···  Rozpoznawany na całym świecie jako wezwanie pomocy."
        BeaconPattern.SLOW -> "Sześć mignięć na minutę – tak wzywa się pomocy w górach. Odpowiedź ratowników to trzy mignięcia na minutę."
        BeaconPattern.FAST -> "Dobrze widoczne w ruchu i z powietrza; zużywa najwięcej prądu."
        BeaconPattern.STEADY -> "Stałe światło do oznaczenia miejsca, np. wejścia do namiotu albo biwaku."
    }
}
