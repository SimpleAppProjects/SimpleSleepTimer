package com.thewizrd.simplesleeptimer.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.composables.TimePicker
import com.google.android.horologist.compose.rotaryinput.onRotaryInputAccumulatedWithFocus
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.TimerStringFormatter
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation.*
import com.thewizrd.simplesleeptimer.viewmodels.TimerViewModel
import java.time.Duration
import java.time.LocalTime
import java.util.Locale
import kotlin.math.sign
import kotlin.math.sqrt

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun StartTimerScreen(
    timerModel: TimerViewModel
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val state by timerModel.uiState.collectAsState()
    var showPickerDialog by remember {
        mutableStateOf(false)
    }

    val timerDuration = remember(state) {
        Duration.ofMillis(state.timerLengthInMs)
    }
    val containerWidth = LocalConfiguration.current.screenWidthDp
    val isLarge = containerWidth > 192

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryInputAccumulatedWithFocus(
                isLowRes = false,
                onValueChange = {
                    if (it.sign > 0) {
                        timerModel.requestTimerOp(ADD_1M)
                    } else {
                        timerModel.requestTimerOp(MINUS_1M)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isPreview) {
            TimeText()
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(TopEdgePadding() + 12.dp))
            if (!state.isLocalTimer) {
                Icon(
                    modifier = Modifier
                        .requiredSize(20.dp)
                        .clickable {
                            timerModel.requestTimerOp(OPEN_MUSIC)
                        },
                    painter = painterResource(id = R.drawable.ic_music_note),
                    contentDescription = ""
                )
            }
            Box(
                modifier = Modifier
                    .weight(fill = true, weight = 1f)
                    .align(Alignment.CenterHorizontally)
            ) {
                val titleTextStyle = if (isLarge) {
                    MaterialTheme.typography.display3
                } else {
                    MaterialTheme.typography.title1
                }
                var textStyle by remember {
                    mutableStateOf(titleTextStyle)
                }
                var readyToDrawText by remember { mutableStateOf(false) }

                Text(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .align(Alignment.Center)
                        .drawWithContent {
                            if (readyToDrawText) drawContent()
                        }
                        .clickable(role = Role.Button) {
                            showPickerDialog = true
                        },
                    text = durationToTimerStartString(context, timerDuration),
                    color = MaterialTheme.colors.primary,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    onTextLayout = {
                        if (it.didOverflowWidth || it.didOverflowHeight) {
                            textStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.9)
                        } else {
                            readyToDrawText = true
                        }
                    }
                )
            }
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(vertical = 4.dp),
            ) {
                if (isLarge) {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable(role = Role.Button) {
                                timerModel.requestTimerOp(MINUS_5M)
                            }
                            .align(Alignment.CenterVertically),
                        text = stringResource(id = R.string.label_btn_minus5min),
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.caption2
                    )
                }
                Text(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(role = Role.Button) {
                            timerModel.requestTimerOp(MINUS_1M)
                        }
                        .align(Alignment.CenterVertically),
                    text = stringResource(id = R.string.label_btn_minus1min),
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.caption2
                )
                Icon(
                    modifier = Modifier
                        .requiredSize(ButtonDefaults.SmallIconSize)
                        .align(Alignment.CenterVertically)
                        .clickable {
                            timerModel.updateTimerState(timerLengthInMs = TimerModel.DEFAULT_TIME_MIN * DateUtils.MINUTE_IN_MILLIS)
                        },
                    painter = painterResource(id = R.drawable.ic_baseline_restart_alt_24),
                    contentDescription = "",
                    tint = MaterialTheme.colors.onBackground
                )
                Text(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(role = Role.Button) {
                            timerModel.requestTimerOp(ADD_1M)
                        }
                        .align(Alignment.CenterVertically),
                    text = stringResource(id = R.string.label_btn_plus1min),
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.caption2
                )
                if (isLarge) {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable(role = Role.Button) {
                                timerModel.requestTimerOp(ADD_5M)
                            }
                            .align(Alignment.CenterVertically),
                        text = stringResource(id = R.string.label_btn_plus5min),
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.caption2
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = TopEdgePadding())
                    .weight(fill = true, weight = 1.5f),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !timerDuration.isZero,
                    enter = scaleIn(animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)),
                    exit = scaleOut(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250))
                ) {
                    Button(
                        modifier = Modifier
                            .requiredSize(
                                if (isLarge) ButtonDefaults.DefaultButtonSize else ButtonDefaults.SmallButtonSize
                            )
                            .align(Alignment.Center),
                        colors = ButtonDefaults.primaryButtonColors(
                            backgroundColor = MaterialTheme.colors.secondary
                        ),
                        onClick = {
                            timerModel.requestTimerOp(START)
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(
                                if (isLarge) ButtonDefaults.DefaultIconSize else ButtonDefaults.SmallIconSize
                            ),
                            painter = painterResource(id = R.drawable.ic_play_arrow),
                            contentDescription = "Start"
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = timerDuration.isZero,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Spacer(
                        modifier = Modifier.requiredSize(
                            if (isLarge) ButtonDefaults.DefaultButtonSize else ButtonDefaults.SmallButtonSize
                        )
                    )
                }
            }
        }
    }

    Dialog(
        showDialog = showPickerDialog,
        onDismissRequest = { showPickerDialog = false }
    ) {
        TimePicker(
            modifier = Modifier.fillMaxSize(),
            time = LocalTime.of(timerDuration.toHoursPart(), timerDuration.toMinutesPart()),
            showSeconds = false,
            onTimeConfirm = {
                val duration = Duration.ofNanos(it.toNanoOfDay())

                timerModel.updateTimerState(
                    timerLengthInMs = duration.toMillis()
                )

                showPickerDialog = false
            }
        )
    }
}

@Composable
fun TimerInProgressScreen(
    timerModel: TimerViewModel
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val state by timerModel.uiState.collectAsState()

    val remainingDuration = remember(state) {
        Duration.ofMillis(state.remainingTimeInMs)
    }
    val containerWidth = LocalConfiguration.current.screenWidthDp
    val isLarge = containerWidth > 192

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            startAngle = 292f,
            endAngle = 246f,
            progress = state.getTimerProgress(),
        )
        if (isPreview) {
            TimeText()
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(TopEdgePadding()))
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val show1Min = remainingDuration.toMinutes() < (TimerModel.MAX_TIME_IN_MINS - 1)
                val show5Min = remainingDuration.toMinutes() < (TimerModel.MAX_TIME_IN_MINS - 5)

                AnimatedVisibility(visible = show1Min) {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable(role = Role.Button) {
                                timerModel.requestTimerOp(EXTEND_1M)
                            },
                        text = stringResource(id = R.string.label_btn_plus1min),
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.caption2
                    )
                }
                AnimatedVisibility(visible = show5Min) {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable(role = Role.Button) {
                                timerModel.requestTimerOp(EXTEND_5M)
                            },
                        text = stringResource(id = R.string.label_btn_plus5min),
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.caption2
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
            ) {
                val titleTextStyle = if (isLarge) {
                    MaterialTheme.typography.display3
                } else {
                    MaterialTheme.typography.title1
                }
                var textStyle by remember {
                    mutableStateOf(titleTextStyle)
                }
                var readyToDraw by remember { mutableStateOf(false) }

                Text(
                    modifier = Modifier
                        .wrapContentHeight()
                        .padding(horizontal = 12.dp)
                        .align(Alignment.CenterVertically)
                        .drawWithContent {
                            if (readyToDraw) drawContent()
                        },
                    text = durationToTimerProgressString(context, remainingDuration),
                    color = MaterialTheme.colors.primary,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = {
                        if (it.didOverflowWidth) {
                            textStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.9)
                        } else {
                            readyToDraw = true
                        }
                    }
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = TopEdgePadding())
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    modifier = Modifier.requiredSize(
                        if (isLarge) ButtonDefaults.LargeButtonSize else ButtonDefaults.DefaultButtonSize
                    ),
                    colors = ButtonDefaults.primaryButtonColors(
                        backgroundColor = MaterialTheme.colors.secondary
                    ),
                    onClick = {
                        timerModel.requestTimerOp(STOP)
                    }
                ) {
                    Icon(
                        modifier = Modifier.size(
                            if (isLarge) ButtonDefaults.LargeIconSize else ButtonDefaults.DefaultIconSize
                        ),
                        painter = painterResource(id = R.drawable.ic_stop),
                        contentDescription = "Stop"
                    )
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewStartTimerScreen() {
    val timerModel = remember {
        TimerViewModel().apply {
            updateTimerState(TimerModel())
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        StartTimerScreen(timerModel)
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewStartRemoteTimerScreen() {
    val timerModel = remember {
        TimerViewModel().apply {
            updateTimerState(isLocalTimer = false)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        StartTimerScreen(timerModel)
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewTimerInProgressScreen() {
    val timerModel = remember {
        TimerViewModel().apply {
            updateTimerState(TimerModel())
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        TimerInProgressScreen(timerModel)
    }
}

@Composable
private fun TopEdgePadding(): Dp {
    val isRound = LocalConfiguration.current.isScreenRound
    var inset: Dp = 12.dp

    if (isRound) {
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        val screenWidthDp = LocalConfiguration.current.smallestScreenWidthDp
        val maxSquareEdge = (sqrt(((screenHeightDp * screenWidthDp) / 2).toDouble()))
        inset = Dp(((screenHeightDp - maxSquareEdge) / 4).toFloat())
    }

    return inset
}

private fun durationToTimerStartString(context: Context, duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()

    return if (hours > 0) {
        context.getString(R.string.timer_progress_hours_minutes, hours, minutes)
    } else {
        TimerStringFormatter.getNumberFormattedQuantityString(
            context, R.plurals.minutes_short, minutes
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