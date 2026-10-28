package com.blacksingularities.openmesh

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.blacksingularities.openmesh.mesh.MeshCore
import com.blacksingularities.openmesh.mesh.Notifier

class OpenMeshApp : Application() {
    val core: MeshCore by lazy { MeshCore(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        // Widoczność aplikacji decyduje o powiadomieniach (nie pokazujemy ich dla otwartej rozmowy).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                core.appVisible = true
            }

            override fun onStop(owner: LifecycleOwner) {
                core.appVisible = false
            }
        })
    }
}

val Context.mesh: MeshCore get() = (applicationContext as OpenMeshApp).core
