@file:Suppress("FunctionName")

package com.thewizrd.simplesleeptimer.wearable.tiles.layouts

import android.content.Context
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.material3.CardDefaults.filledTonalCardColors
import androidx.wear.protolayout.material3.DataCardStyle
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textDataCard
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.tooling.preview.TilePreviewData
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.ui.theme.wearTileColorScheme
import com.thewizrd.simplesleeptimer.ui.tiles.tools.WearPreviewDevices
import com.thewizrd.simplesleeptimer.wearable.tiles.RemoteTimerTileState
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer
import com.thewizrd.shared_resources.R as sharedRes


internal fun WearConnectionStatusLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    connectionStatus: WearConnectionStatus
) = materialScope(context, deviceParameters, defaultColorScheme = wearTileColorScheme) {
    primaryLayout(
        titleSlot = {
            text(text = context.getString(sharedRes.string.title_sleeptimer).layoutString)
        },
        mainSlot = {
            textDataCard(
                onClick = clickable(
                    action = getTapAction(context, false)
                ),
                width = expand(),
                height = expand(),
                title = {
                    text(
                        text = when (connectionStatus) {
                            WearConnectionStatus.DISCONNECTED -> context.getString(R.string.status_disconnected)
                            WearConnectionStatus.CONNECTING -> context.getString(R.string.status_connecting)
                            WearConnectionStatus.APPNOTINSTALLED -> context.getString(R.string.error_sleeptimer_notinstalled)
                            WearConnectionStatus.CONNECTED -> context.getString(R.string.status_connected)
                        }.layoutString,
                        typography = BODY_MEDIUM,
                        maxLines = 3
                    )
                },
                colors = filledTonalCardColors(),
                style = DataCardStyle.smallDataCardStyle()
            )
        },
        bottomSlot = {
            textEdgeButton(
                onClick = clickable(
                    action = getTapAction(context, false)
                ),
                labelContent = {
                    text(
                        text = if (connectionStatus == WearConnectionStatus.CONNECTING) {
                            context.getString(R.string.action_retry)
                        } else {
                            context.getString(R.string.action_open)
                        }.layoutString
                    )
                }
            )
        }
    )
}

@WearPreviewDevices
private fun DisconnectedRemoteTimerLayoutPreview(context: Context): TilePreviewData {
    val state = RemoteTimerTileState(
        connectionStatus = WearConnectionStatus.DISCONNECTED,
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

@WearPreviewDevices
private fun ConnectingRemoteTimerLayoutPreview(context: Context): TilePreviewData {
    val state = RemoteTimerTileState(
        connectionStatus = WearConnectionStatus.CONNECTING,
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