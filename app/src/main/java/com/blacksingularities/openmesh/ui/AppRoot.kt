package com.blacksingularities.openmesh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openmesh.ui.screens.BeaconScreen
import com.blacksingularities.openmesh.ui.screens.ChatScreen
import com.blacksingularities.openmesh.ui.screens.HomeScreen
import com.blacksingularities.openmesh.ui.screens.MapScreen
import com.blacksingularities.openmesh.ui.screens.OnboardingScreen
import com.blacksingularities.openmesh.ui.screens.SearchScreen
import com.blacksingularities.openmesh.ui.screens.Sheets
import com.blacksingularities.openmesh.ui.theme.Palette
import com.blacksingularities.openmesh.ui.theme.Pill
import com.blacksingularities.openmesh.ui.theme.panel

@Composable
fun AppRoot(vm: MeshViewModel) {
    val canGoBack = vm.sheet != null ||
        (vm.screen != Screen.Main && vm.screen != Screen.Onboarding) ||
        (vm.screen == Screen.Main && vm.tab != Tab.CHATS)
    BackHandler(enabled = canGoBack) {
        vm.back()
    }
    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                val forward = targetState !is Screen.Main
                if (initialState is Screen.Onboarding) {
                    fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                } else {
                    (slideInHorizontally(tween(260)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(180)))
                }
            },
            contentKey = { it::class },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Onboarding -> OnboardingScreen(vm)
                Screen.Main -> HomeScreen(vm)
                is Screen.Chat -> ChatScreen(vm, s.convo)
                Screen.Search -> SearchScreen(vm)
                Screen.Map -> MapScreen(vm)
                Screen.Beacon -> BeaconScreen(vm)
            }
        }
        Sheets(vm)
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 96.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = vm.notice != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Text(
                    vm.notice ?: "",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp).panel(Pill, Palette.CardHigher).padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}
