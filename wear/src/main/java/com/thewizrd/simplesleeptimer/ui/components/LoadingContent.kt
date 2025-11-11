package com.thewizrd.simplesleeptimer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.CircularProgressIndicator

@Composable
fun LoadingContent(
    empty: Boolean,
    emptyContent: @Composable () -> Unit,
    loading: Boolean,
    loadingContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    },
    content: @Composable () -> Unit
) {
    if (loading) {
        loadingContent()
    } else if (empty) {
        emptyContent()
    } else {
        content()
    }
}

@Composable
fun AnimatedLoadingContent(
    empty: Boolean,
    emptyContent: @Composable () -> Unit,
    loading: Boolean,
    loadingContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    },
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    content: @Composable () -> Unit
) {
    if (loading) {
        loadingContent()
    }

    AnimatedVisibility(
        visible = !loading && empty,
        enter = enter,
        exit = exit
    ) {
        emptyContent()
    }

    AnimatedVisibility(
        visible = !loading && !empty,
        enter = enter,
        exit = exit
    ) {
        content()
    }
}