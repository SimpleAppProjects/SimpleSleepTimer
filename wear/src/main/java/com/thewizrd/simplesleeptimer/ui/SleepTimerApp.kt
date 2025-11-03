@file:OptIn(ExperimentalHorologistApi::class)

package com.thewizrd.simplesleeptimer.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplesleeptimer.PhoneSyncActivity
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.ui.components.AnimatedLoadingContent
import com.thewizrd.simplesleeptimer.ui.components.ConfirmationOverlay
import com.thewizrd.simplesleeptimer.ui.theme.WearAppTheme
import com.thewizrd.simplesleeptimer.ui.theme.activityViewModel
import com.thewizrd.simplesleeptimer.ui.theme.findActivity
import com.thewizrd.simplesleeptimer.updates.InAppUpdateManager
import com.thewizrd.simplesleeptimer.utils.ErrorMessage
import com.thewizrd.simplesleeptimer.viewmodels.ConfirmationData
import com.thewizrd.simplesleeptimer.viewmodels.ConfirmationViewModel
import com.thewizrd.simplesleeptimer.viewmodels.TimerViewModel
import com.thewizrd.simplesleeptimer.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

@Composable
fun SleepTimerApp(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val timerModel = activityViewModel<TimerViewModel>()

    val uiState by timerModel.uiState.collectAsState()

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

    var stateRefreshed by remember { mutableStateOf(false) }

    val inAppUpdateMgr = remember(context) {
        InAppUpdateManager.create(context)
    }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showAppUpdateConfirmation by remember { mutableStateOf(false) }

    WearAppTheme {
        AppScaffold {
            AnimatedLoadingContent(
                empty = !uiState.isRunning,
                loading = uiState.isLoading,
                enter = fadeIn(animationSpec = tween(250)) + slideInHorizontally(
                    animationSpec = tween(
                        500
                    )
                ),
                exit = fadeOut(animationSpec = tween(250)) + slideOutHorizontally(
                    animationSpec = tween(
                        500
                    )
                ),
                emptyContent = {
                    StartTimerScreen(timerModel = timerModel)
                }
            ) {
                TimerInProgressScreen(timerModel = timerModel)
            }
        }

        AlertDialog(
            visible = showUpdateDialog,
            onDismissRequest = {
                Settings.setLastUpdateCheckTime(Instant.now())
                showUpdateDialog = false
            },
            icon = {
                Icon(
                    painter = rememberVectorPainter(image = Icons.Default.Info),
                    contentDescription = stringResource(R.string.label_info)
                )
            },
            title = {},
            text = {
                Text(text = stringResource(id = R.string.message_wearappupdate_available))
            }
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                Button(
                    label = {
                        Text(text = stringResource(id = R.string.action_update))
                    },
                    onClick = {
                        runCatching {
                            // Open store on device
                            activity.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                    .setData(WearableHelper.getPlayStoreURI())
                            )
                        }
                        showUpdateDialog = false
                    }
                )
            }
            if (inAppUpdateMgr.updatePriority <= 3) {
                item {
                    FilledTonalButton(
                        label = {
                            Text(text = stringResource(id = android.R.string.cancel))
                        },
                        onClick = {
                            Settings.setLastUpdateCheckTime(Instant.now())
                            showUpdateDialog = false
                        }
                    )
                }
            }
        }

        AlertDialog(
            visible = showAppUpdateConfirmation,
            onDismissRequest = {
                Settings.setLastUpdateCheckTime(Instant.now())
                showAppUpdateConfirmation = false
            },
            icon = {
                var startAnim by remember { mutableStateOf(false) }

                Icon(
                    modifier = Modifier.size(36.dp),
                    painter = rememberAnimatedVectorPainter(
                        animatedImageVector = AnimatedImageVector.animatedVectorResource(id = R.drawable.open_on_phone_animation),
                        atEnd = startAnim
                    ),
                    contentDescription = null
                )

                LaunchedEffect(showAppUpdateConfirmation) {
                    delay(250)
                    startAnim = true
                }
            },
            title = {},
            text = {
                Text(text = stringResource(id = R.string.message_phoneappupdate_available))
            },
            edgeButton = {
                AlertDialogDefaults.EdgeButton(
                    onClick = {
                        Settings.setLastUpdateCheckTime(Instant.now())
                        showAppUpdateConfirmation = false
                    }
                )
            }
        )

        ConfirmationOverlay(
            confirmationData = confirmationData,
            onTimeout = { confirmationViewModel.clearFlow() },
        )

        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycleScope.launch {
                timerModel.eventFlow.collect { event ->
                    when (event.eventType) {
                        WearableListenerViewModel.ACTION_UPDATECONNECTIONSTATUS -> {
                            if (uiState.isLocalTimer) return@collect

                            val connectionStatus = WearConnectionStatus.valueOf(
                                event.data.getInt(
                                    WearableListenerViewModel.EXTRA_CONNECTIONSTATUS,
                                    0
                                )
                            )

                            when (connectionStatus) {
                                WearConnectionStatus.DISCONNECTED -> {
                                    activity.startActivity(
                                        Intent(
                                            activity,
                                            PhoneSyncActivity::class.java
                                        )
                                    )
                                    activity.finishAffinity()
                                }

                                WearConnectionStatus.CONNECTING -> {}
                                WearConnectionStatus.APPNOTINSTALLED -> {
                                    // Open store on remote device
                                    timerModel.openPlayStore(activity)

                                    // Navigate
                                    activity.startActivity(
                                        Intent(
                                            activity,
                                            PhoneSyncActivity::class.java
                                        )
                                    )
                                    activity.finishAffinity()
                                }

                                WearConnectionStatus.CONNECTED -> {}
                            }
                        }

                        WearableListenerViewModel.ACTION_SHOWCONFIRMATION -> {
                            val jsonData =
                                event.data.getString(WearableListenerViewModel.EXTRA_EVENTDATA)

                            JSONParser.deserializer(jsonData, ConfirmationData::class.java)?.let {
                                confirmationViewModel.showConfirmation(it)
                            }
                        }
                    }
                }
            }

            lifecycleOwner.lifecycleScope.launch {
                timerModel.errorMessagesFlow.collect { error ->
                    when (error) {
                        is ErrorMessage.String -> {
                            Toast.makeText(activity, error.message, Toast.LENGTH_SHORT).show()
                        }

                        is ErrorMessage.Resource -> {
                            Toast.makeText(activity, error.stringId, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            lifecycleOwner.lifecycleScope.launch {
                if (Duration.between(
                        Settings.getLastUpdateCheckTime(),
                        Instant.now()
                    ) >= Duration.ofDays(1)
                ) {
                    // Check phone version
                    runCatching {
                        val phoneVersionCode = withTimeoutOrNull(15000) {
                            timerModel.requestPhoneAppVersion()
                        }

                        phoneVersionCode?.let {
                            showAppUpdateConfirmation = !WearableHelper.isAppUpToDate(it)
                            if (showAppUpdateConfirmation) {
                                timerModel.openPlayStore(activity, false)
                            }
                        }
                    }
                }
            }

            lifecycleOwner.lifecycleScope.launch {
                if (Duration.between(
                        Settings.getLastUpdateCheckTime(),
                        Instant.now()
                    ) >= Duration.ofDays(1)
                ) {
                    // Check phone version
                    runCatching {
                        showUpdateDialog =
                            inAppUpdateMgr.checkIfUpdateAvailable() && inAppUpdateMgr.updatePriority > 3
                    }.onFailure {
                        Logger.writeLine(Log.ERROR, it)
                    }
                }
            }
        }

        LifecycleResumeEffect(uiState.isLocalTimer, lifecycleOwner = lifecycleOwner) {
            if (!uiState.isLocalTimer && !stateRefreshed) {
                timerModel.refreshStatus()
                stateRefreshed = true
            }

            onPauseOrDispose {
                stateRefreshed = false
            }
        }
    }
}