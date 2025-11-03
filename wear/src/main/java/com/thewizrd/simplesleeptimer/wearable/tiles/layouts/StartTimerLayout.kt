@file:OptIn(ProtoLayoutExperimental::class)
@file:kotlin.OptIn(ExperimentalHorologistApi::class)
@file:Suppress("FunctionName")

package com.thewizrd.simplesleeptimer.wearable.tiles.layouts

import android.content.Context
import androidx.annotation.OptIn
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import androidx.wear.protolayout.material3.ButtonGroupDefaults.DEFAULT_SPACER_BETWEEN_BUTTON_GROUPS
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.tooling.preview.TilePreviewData
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.ui.theme.wearTileColorScheme
import com.thewizrd.simplesleeptimer.ui.tiles.tools.WearPreviewDevices
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer
import com.thewizrd.simplesleeptimer.wearable.tiles.TimerState
import com.thewizrd.simplesleeptimer.wearable.tiles.TimerTileDuration
import com.thewizrd.simplesleeptimer.wearable.tiles.TimerTileState

fun StartTimerLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    state: TimerState
): LayoutElement =
    materialScope(context, deviceParameters, defaultColorScheme = wearTileColorScheme) {
        primaryLayout(
            titleSlot = {
                text(text = context.getString(R.string.title_sleeptimer).layoutString)
            },
            mainSlot = {
                Column.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .addContent(
                        buttonGroup {
                            buttonGroupItem {
                                TimerButton(context, state, TimerTileDuration.DURATION_5)
                            }
                            buttonGroupItem {
                                TimerButton(context, state, TimerTileDuration.DURATION_10)
                            }
                            buttonGroupItem {
                                TimerButton(context, state, TimerTileDuration.DURATION_15)
                            }
                        }
                    )
                    .addContent(DEFAULT_SPACER_BETWEEN_BUTTON_GROUPS)
                    .addContent(
                        buttonGroup {
                            buttonGroupItem {
                                TimerButton(context, state, TimerTileDuration.DURATION_20)
                            }
                            buttonGroupItem {
                                TimerButton(context, state, TimerTileDuration.DURATION_30)
                            }
                        }
                    )
                    .build()
            },
            bottomSlot = {
                textEdgeButton(
                    onClick = clickable(getTapAction(context, state.isLocalTimer)),
                    labelContent = {
                        text(
                            text = if (state.isLocalTimer) {
                                context.getString(R.string.action_open)
                            } else {
                                context.getString(R.string.label_remote)
                            }.layoutString
                        )
                    }
                )
            }
        )
    }

@WearPreviewDevices
private fun StartLocalTimerLayoutPreview(context: Context): TilePreviewData {
    val state = TimerTileState(
        isLocalTimer = true,
        timerModel = TimerModel().apply {
            timerLengthInMins = 5
            isRunning = false
        }
    )
    val renderer = SleepTimerTileRenderer(context, debugResourceMode = true)

    return TilePreviewData(
        onTileRequest = { renderer.renderTimeline(state, it) },
        onTileResourceRequest = { renderer.produceRequestedResources(Unit, it) }
    )
}