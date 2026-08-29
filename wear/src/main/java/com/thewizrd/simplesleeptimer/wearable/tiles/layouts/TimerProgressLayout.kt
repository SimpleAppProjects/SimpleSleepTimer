@file:Suppress("FunctionName")

package com.thewizrd.simplesleeptimer.wearable.tiles.layouts

import android.content.Context
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.CardDefaults.filledVariantCardColors
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconDataCard
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.protolayout.types.layoutString
import androidx.wear.protolayout.types.stringLayoutConstraint
import androidx.wear.tiles.tooling.preview.TilePreviewData
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.ui.theme.wearTileColorScheme
import com.thewizrd.simplesleeptimer.ui.tiles.tools.WearPreviewDevices
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_LOCAL_TIMER
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_REMOTE_TIMER
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_STOP
import com.thewizrd.simplesleeptimer.wearable.tiles.TimerState
import com.thewizrd.simplesleeptimer.wearable.tiles.TimerTileState
import java.time.Duration
import java.time.Instant
import java.util.Locale
import com.thewizrd.shared_resources.R as sharedRes


fun TimerProgressLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    state: TimerState
): LayoutElementBuilders.LayoutElement =
    materialScope(context, deviceParameters, defaultColorScheme = wearTileColorScheme) {
        val timerLengthInMins = state.timerModel?.timerLengthInMins ?: 0
        val hours = timerLengthInMins / 60
        val minutes = timerLengthInMins - (hours * 60)

        val durationUntilEnd = Duration.between(
            Instant.ofEpochMilli(state.timerModel?.endTimeInMs ?: 0),
            Instant.now()
        )

        primaryLayout(
            titleSlot = if (deviceConfiguration.isLargeHeight()) {
                {
                    text(text = context.getString(sharedRes.string.title_sleeptimer).layoutString)
                }
            } else null,
            mainSlot = {
                iconDataCard(
                    width = expand(),
                    height = expand(),
                    onClick = clickable(getTapAction(context, state.isLocalTimer)),
                    title = {
                        text(
                            text = LayoutString(
                                staticValue = "" + if (durationUntilEnd.toHoursPart() > 0) {
                                    String.format(
                                        Locale.ROOT,
                                        "%02d : ",
                                        durationUntilEnd.toHoursPart()
                                    )
                                } else {
                                    ""
                                } + if (durationUntilEnd.toMinutesPart() > 0) {
                                    String.format(
                                        Locale.ROOT,
                                        "%02d : ",
                                        durationUntilEnd.toMinutesPart()
                                    )
                                } else {
                                    ""
                                } + String.format(
                                    Locale.ROOT,
                                    "%02d",
                                    durationUntilEnd.toSecondsPart()
                                ),
                                dynamicValue = getDynamicDurationText(state),
                                layoutConstraint = stringLayoutConstraint("99 : 99 : 99")
                            )
                        )
                    },
                    content = {
                        text(
                            text = if (hours > 0) {
                                context.getString(
                                    R.string.label_tile_duration_hr_min,
                                    hours,
                                    minutes
                                )
                            } else {
                                context.getString(R.string.label_tile_duration_mins, minutes)
                            }.layoutString
                        )
                    },
                    secondaryIcon = {
                        icon(
                            width = if (deviceConfiguration.isLargeHeight()) dp(24f) else dp(20f),
                            height = if (deviceConfiguration.isLargeHeight()) dp(24f) else dp(20f),
                            protoLayoutResourceId = if (state.isLocalTimer) ID_LOCAL_TIMER else ID_REMOTE_TIMER
                        )
                    },
                    colors = filledVariantCardColors()
                )
            },
            bottomSlot = {
                textEdgeButton(
                    onClick = clickable(id = ID_STOP),
                    labelContent = {
                        text(text = context.getString(sharedRes.string.label_stop).layoutString)
                    }
                )
            }
        )
    }

@WearPreviewDevices
private fun ProgressLocalTimerLayoutPreview(context: Context): TilePreviewData {
    val state = TimerTileState(
        isLocalTimer = true,
        timerModel = TimerModel().apply {
            timerLengthInMins = 5
            isRunning = true
            startTimer()
        }
    )
    val renderer = SleepTimerTileRenderer(context, debugResourceMode = true)

    return TilePreviewData(
        onTileRequest = { renderer.renderTimeline(state, it) },
        onTileResourceRequest = { renderer.produceRequestedResources(Unit, it) }
    )
}

@WearPreviewDevices
private fun ProgressRemoteTimerLayoutPreview(context: Context): TilePreviewData {
    val state = TimerTileState(
        isLocalTimer = false,
        timerModel = TimerModel().apply {
            timerLengthInMins = 65
            isRunning = true
            startTimer()
        }
    )
    val renderer = SleepTimerTileRenderer(context, debugResourceMode = true)

    return TilePreviewData(
        onTileRequest = { renderer.renderTimeline(state, it) },
        onTileResourceRequest = { renderer.produceRequestedResources(Unit, it) }
    )
}