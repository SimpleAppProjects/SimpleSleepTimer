package com.thewizrd.simplesleeptimer.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonDefaults
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.ripple
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.compose.rotaryinput.accumulatedBehavior
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.TimerStringFormatter
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.ui.components.MusicPlayersDialog
import com.thewizrd.simplesleeptimer.ui.components.SleepTimePickerDialog
import com.thewizrd.simplesleeptimer.ui.compose.tools.WearPreviewDevices
import com.thewizrd.simplesleeptimer.ui.utils.rememberFocusRequester
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation.ADD_1M
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation.ADD_5M
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation.EXTEND_1M
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation.EXTEND_5M
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation.MINUS_1M
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation.MINUS_5M
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation.START
import com.thewizrd.simplesleeptimer.viewmodels.TimerUiState
import com.thewizrd.simplesleeptimer.viewmodels.TimerViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.sign
import com.thewizrd.shared_resources.R as sharedRes

@Composable
fun StartTimerScreen(
    timerModel: TimerViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val state by timerModel.uiState.collectAsState()
    var showPickerDialog by remember {
        mutableStateOf(false)
    }
    var showMusicPlayerDialog by remember {
        mutableStateOf(false)
    }
    var showAlarmPermissionDialog by remember { mutableStateOf(false) }

    val timerDuration = remember(state) {
        Duration.ofMillis(state.timerLengthInMs)
    }

    LaunchedEffect(lifecycleOwner) {
        timerModel.updateTimerState(
            timerLengthInMs = TimeUnit.MINUTES.toMillis(Settings.getLastTimeSet().toLong())
        )
    }

    StartTimerScreen(
        state = state,
        timerDuration = timerDuration,
        onTimerOperation = {
            timerModel.requestTimerOp(it)
        },
        onShowMusicPlayerDialog = {
            showMusicPlayerDialog = true
        },
        onShowPickerDialog = {
            showPickerDialog = true
        },
        resetTimerState = {
            timerModel.updateTimerState(timerLengthInMs = TimerModel.DEFAULT_TIME_MIN * DateUtils.MINUTE_IN_MILLIS)
        }
    )

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            timerModel.eventFlow.collect { event ->
                when (event.eventType) {
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM -> {
                        showAlarmPermissionDialog = true
                    }
                }
            }
        }
    }

    SleepTimePickerDialog(
        showDialog = showPickerDialog,
        onDismissRequest = { showPickerDialog = false },
        timerDuration = timerDuration,
        onTimeConfirm = {
            timerModel.updateTimerState(
                timerLengthInMs = it.toMillis()
            )
        }
    )

    if (!isPreview) {
        AlertDialog(
            visible = showAlarmPermissionDialog,
            onDismissRequest = { showAlarmPermissionDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = stringResource(R.string.label_info)
                )
            },
            title = {},
            text = {
                Text(text = stringResource(sharedRes.string.message_alarms_permission))
            },
            edgeButton = {
                AlertDialogDefaults.EdgeButton(
                    onClick = {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                showAlarmPermissionDialog = false
                            }
                        }.onFailure {
                            Logger.error("SleepTimerActivity", it, "Error")
                        }
                    },
                    content = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(R.string.label_settings))
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.label_settings)
                            )
                        }
                    }
                )
            }
        )

        MusicPlayersDialog(
            showDialog = showMusicPlayerDialog,
            onDismissRequest = { showMusicPlayerDialog = false }
        )
    }
}

@Composable
private fun StartTimerScreen(
    modifier: Modifier = Modifier,
    state: TimerUiState,
    timerDuration: Duration,
    onTimerOperation: (TimerOperation) -> Unit = {},
    onShowMusicPlayerDialog: () -> Unit = {},
    onShowPickerDialog: () -> Unit = {},
    resetTimerState: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val configuration = LocalConfiguration.current
    val isLargeHeight = configuration.screenHeightDp >= 225f

    val topSectionBottomPadding = remember(configuration) {
        if (isLargeHeight) {
            6.dp
        } else {
            2.dp
        }
    }
    val topSectionMinimumHeight = remember(configuration) {
        if (!isLargeHeight) {
            60.dp
        } else {
            Dp.Unspecified
        }
    }
    val middleSectionMinimumHeight = remember(configuration) {
        if (isLargeHeight) {
            52.dp
        } else {
            42.dp
        }
    }

    val buttonWidth = if (isLargeHeight) 44.dp else 40.dp
    val buttonHeight = if (isLargeHeight) 32.dp else 28.dp

    ScreenScaffold(
        modifier = modifier
            .fillMaxSize()
            .requestFocusOnHierarchyActive()
            .rotaryScrollable(
                focusRequester = rememberFocusRequester(),
                behavior = accumulatedBehavior {
                    if (it.sign > 0) {
                        onTimerOperation(ADD_1M)
                    } else {
                        onTimerOperation(MINUS_1M)
                    }
                }
            )
    ) { contentPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val startPadding = remember(layoutDirection, contentPadding) {
            contentPadding.calculateStartPadding(layoutDirection)
        }
        val endPadding = remember(layoutDirection, contentPadding) {
            contentPadding.calculateEndPadding(layoutDirection)
        }
        val topPadding = remember(contentPadding) {
            contentPadding.calculateTopPadding()
        }

        if (isPreview) {
            TimeText()
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = topSectionMinimumHeight)
                    .padding(
                        top = topPadding,
                        bottom = topSectionBottomPadding,
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    if (!state.isLocalTimer) {
                        FilledTonalIconButton(
                            modifier = Modifier
                                .width(buttonWidth)
                                .height(buttonHeight),
                            onClick = onShowMusicPlayerDialog,
                            content = {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = stringResource(id = sharedRes.string.title_audioplayer)
                                )
                            }
                        )
                    }
                    FilledTonalIconButton(
                        modifier = Modifier
                            .width(buttonWidth)
                            .height(buttonHeight),
                        onClick = resetTimerState,
                        content = {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = stringResource(id = sharedRes.string.action_reset),
                            )
                        }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = middleSectionMinimumHeight),
                contentAlignment = Alignment.Center,
            ) {
                val shape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .background(
                            Color.Transparent,
                            shape
                        )
                        .clip(shape)
                        .clickable(
                            role = Role.Button,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true)
                        ) {
                            onShowPickerDialog()
                        }
                        .padding(4.dp),
                ) {
                    Text(
                        text = durationToTimerStartString(context, timerDuration),
                        color = MaterialTheme.colorScheme.primary,
                        style = if (isLargeHeight) {
                            MaterialTheme.typography.numeralSmall
                        } else {
                            MaterialTheme.typography.numeralExtraSmall
                        },
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = (maxHeight * 0.012f))
                        .fillMaxSize(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.width(startPadding))
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLargeHeight) {
                            TextButton(
                                modifier = Modifier.height(buttonHeight),
                                onClick = {
                                    onTimerOperation(MINUS_5M)
                                },
                                content = {
                                    Text(text = stringResource(id = sharedRes.string.label_btn_minus5min))
                                },
                                colors = TextButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        TextButton(
                            modifier = Modifier.height(buttonHeight),
                            onClick = {
                                onTimerOperation(MINUS_1M)
                            },
                            content = {
                                Text(text = stringResource(id = sharedRes.string.label_btn_minus1min))
                            },
                            colors = TextButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(IconButtonDefaults.DefaultButtonSize)
                            .padding(bottom = CircularProgressIndicatorDefaults.largeStrokeWidth + 8.dp)
                            .align(Alignment.Bottom)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            visible = !timerDuration.isZero,
                            enter = scaleIn(animationSpec = tween(500)) + fadeIn(
                                animationSpec = tween(
                                    500
                                )
                            ),
                            exit = scaleOut(animationSpec = tween(250)) + fadeOut(
                                animationSpec = tween(
                                    250
                                )
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                FilledIconButton(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    onClick = {
                                        Settings.setLastTimeSet(state.getTimerLengthInMins())
                                        onTimerOperation(START)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(id = sharedRes.string.label_start)
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLargeHeight) {
                            TextButton(
                                modifier = Modifier.height(buttonHeight),
                                onClick = {
                                    onTimerOperation(ADD_5M)
                                },
                                content = {
                                    Text(text = stringResource(id = sharedRes.string.label_btn_plus5min))
                                },
                                colors = TextButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        TextButton(
                            modifier = Modifier.height(buttonHeight),
                            onClick = {
                                onTimerOperation(ADD_1M)
                            },
                            content = {
                                Text(text = stringResource(id = sharedRes.string.label_btn_plus1min))
                            },
                            colors = TextButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(endPadding))
                }
            }
        }
    }
}

@Composable
fun TimerInProgressScreen(
    timerModel: TimerViewModel
) {
    val state by timerModel.uiState.collectAsState()

    TimerInProgressScreen(
        state = state,
        onTimerOperation = {
            timerModel.requestTimerOp(it)
        }
    )
}

@Composable
private fun TimerInProgressScreen(
    modifier: Modifier = Modifier,
    state: TimerUiState,
    onTimerOperation: (TimerOperation) -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenWidthDp = configuration.screenWidthDp.dp
    val isLargeHeight = configuration.screenHeightDp >= 225f

    val screenDiff = remember(configuration) {
        screenHeightDp - screenWidthDp
    }

    val topSectionTopPadding = remember(configuration) {
        configuration.getScreenHeightInDpFromPercentage(
            if (isLargeHeight) {
                13.2f
            } else {
                12f
            },
        )
    }
    val topSectionBottomPadding = remember(configuration) {
        if (isLargeHeight) {
            6.dp
        } else {
            2.dp
        }
    }
    val topSectionMinimumHeight = remember(configuration) {
        if (!isLargeHeight) {
            60.dp
        } else {
            Dp.Unspecified
        }
    }
    val middleSectionMinimumHeight = remember(configuration) {
        if (isLargeHeight) {
            52.dp
        } else {
            42.dp
        }
    }

    val buttonHeight = if (isLargeHeight) 32.dp else 28.dp

    val remainingDuration = remember(state) {
        Duration.ofMillis(state.remainingTimeInMs)
    }

    ScreenScaffold(
        modifier = modifier.fillMaxSize()
    ) { contentPadding ->
        if (isPreview) {
            TimeText()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = screenDiff / 2,
                    bottom = screenDiff / 2
                ),
        ) {
            val currentProgress by rememberUpdatedState(state.getTimerProgress())

            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CircularProgressIndicatorDefaults.FullScreenPadding),
                startAngle = 292f,
                endAngle = 246f,
                progress = { currentProgress },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = screenDiff / 2,
                    bottom = screenDiff / 2
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = topSectionMinimumHeight)
                    .padding(
                        top = topSectionTopPadding,
                        bottom = topSectionBottomPadding,
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    val show1Min = remember(remainingDuration) {
                        remainingDuration.toMinutes() < (TimerModel.MAX_TIME_IN_MINS - 1)
                    }
                    val show5Min = remember(remainingDuration) {
                        remainingDuration.toMinutes() < (TimerModel.MAX_TIME_IN_MINS - 5)
                    }

                    AnimatedVisibility(visible = show1Min) {
                        TextButton(
                            modifier = Modifier.height(buttonHeight),
                            onClick = {
                                onTimerOperation(EXTEND_1M)
                            },
                            content = {
                                Text(text = stringResource(id = sharedRes.string.label_btn_plus1min))
                            },
                            colors = TextButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    AnimatedVisibility(visible = show5Min) {
                        TextButton(
                            modifier = Modifier.height(buttonHeight),
                            onClick = {
                                onTimerOperation(EXTEND_5M)
                            },
                            content = {
                                Text(text = stringResource(id = sharedRes.string.label_btn_plus5min))
                            },
                            colors = TextButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = middleSectionMinimumHeight),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.padding(4.dp),
                ) {
                    Text(
                        text = durationToTimerProgressString(context, remainingDuration),
                        color = MaterialTheme.colorScheme.primary,
                        style = if (isLargeHeight) {
                            MaterialTheme.typography.numeralSmall
                        } else {
                            MaterialTheme.typography.numeralExtraSmall
                        },
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = CircularProgressIndicatorDefaults.largeStrokeWidth + 8.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Box(
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        FilledIconButton(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onClick = {
                                onTimerOperation(TimerOperation.STOP)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = stringResource(id = sharedRes.string.label_stop)
                            )
                        }
                    }
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewStartTimerScreen() {
    val state = remember {
        TimerUiState(isLocalTimer = true, timerLengthInMs = TimeUnit.HOURS.toMillis(23))
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        StartTimerScreen(
            state = state,
            timerDuration = remember { Duration.ofHours(23) }
        )
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewStartRemoteTimerScreen() {
    val state = remember {
        TimerUiState(isLocalTimer = false)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        StartTimerScreen(
            state = state,
            timerDuration = remember { Duration.ofMinutes(15) }
        )
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewTimerInProgressScreen() {
    val state = remember {
        TimerUiState(isLocalTimer = false, remainingTimeInMs = TimeUnit.MINUTES.toMillis(7))
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        TimerInProgressScreen(
            state = state
        )
    }
}

private fun durationToTimerStartString(context: Context, duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()

    return if (hours > 0) {
        context.getString(sharedRes.string.timer_progress_hours_minutes, hours, minutes)
    } else {
        TimerStringFormatter.getNumberFormattedQuantityString(
            context, sharedRes.plurals.minutes_short, minutes
        )
    }
}

private fun durationToTimerProgressString(context: Context, duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    val seconds = duration.toSecondsPart()

    return if (hours > 0) {
        String.format(Locale.ROOT, "%02d : %02d : %02d", hours, minutes, seconds)
    } else if (minutes > 0) {
        String.format(Locale.ROOT, "%02d : %02d", minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d", seconds)
    }
}

private fun Configuration.getScreenHeightInDpFromPercentage(
    percent: Float
): Dp {
    return ceil(screenHeightDp * percent / 100f).dp
}