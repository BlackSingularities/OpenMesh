package com.blacksingularities.openmesh

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.blacksingularities.openmesh.mesh.Notifier
import com.blacksingularities.openmesh.ui.AppRoot
import com.blacksingularities.openmesh.ui.MeshViewModel
import com.blacksingularities.openmesh.ui.theme.OpenMeshTheme
import com.blacksingularities.openmesh.update.UpdateManager

class MainActivity : ComponentActivity() {
    private val vm: MeshViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        UpdateManager.clearUpdatedNotification(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            OpenMeshTheme { AppRoot(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifier.EXTRA_CONVO)?.let { if (mesh.settings.value.onboarded) vm.open(it) }
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { vm.sharedText = it.take(2000) }
        }
    }
}
