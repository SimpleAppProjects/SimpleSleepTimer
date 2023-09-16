package com.thewizrd.simplesleeptimer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import com.thewizrd.simplesleeptimer.ui.theme.WearAppTheme
import com.thewizrd.simplesleeptimer.ui.theme.activityViewModel
import com.thewizrd.simplesleeptimer.viewmodels.TimerViewModel

@Composable
fun SleepTimerApp(
    modifier: Modifier = Modifier
) {
    val timerModel = activityViewModel<TimerViewModel>()
    val uiState by timerModel.uiState.collectAsState()

    WearAppTheme {
        Scaffold(
            modifier = modifier.background(MaterialTheme.colors.background),
            timeText = {
                TimeText()
            },
            vignette = null
        ) {
            AnimatedVisibility(
                visible = uiState.isRunning && !uiState.isLoading,
                enter = fadeIn(animationSpec = tween(250)) + slideInVertically(
                    animationSpec = tween(
                        500
                    )
                ),
                exit = fadeOut(animationSpec = tween(250)) + slideOutVertically(
                    animationSpec = tween(
                        500
                    )
                ),
            ) {
                TimerInProgressScreen(timerModel = timerModel)
            }

            AnimatedVisibility(
                visible = !uiState.isRunning && !uiState.isLoading,
                enter = fadeIn(animationSpec = tween(250)) + slideInVertically(
                    animationSpec = tween(
                        500
                    )
                ),
                exit = fadeOut(animationSpec = tween(250)) + slideOutVertically(
                    animationSpec = tween(
                        500
                    )
                ),
            ) {
                StartTimerScreen(timerModel = timerModel)
            }

            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}