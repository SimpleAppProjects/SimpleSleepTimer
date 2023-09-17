package com.thewizrd.simplesleeptimer.wearable.tiles

import android.content.Context
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.images.drawableResToImageResource
import com.google.android.horologist.tiles.render.SingleTileLayoutRenderer
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.wearable.tiles.layouts.StartTimerLayout
import com.thewizrd.simplesleeptimer.wearable.tiles.layouts.TimerProgressLayout
import com.thewizrd.simplesleeptimer.wearable.tiles.layouts.WearConnectionStatusLayout
import com.thewizrd.simplesleeptimer.wearable.tiles.layouts.getTapAction

@OptIn(ExperimentalHorologistApi::class)
class SleepTimerTileRenderer(context: Context, debugResourceMode: Boolean = false) :
    SingleTileLayoutRenderer<TimerState, Unit>(context, debugResourceMode) {
    companion object {
        internal const val ID_5MIN = "id_5m"
        internal const val ID_10MIN = "id_10m"
        internal const val ID_15MIN = "id_15m"
        internal const val ID_20MIN = "id_20m"
        internal const val ID_30MIN = "id_30m"

        // Resource IDs
        internal const val ID_LOCAL_TIMER = "id_local_timer_ico"
        internal const val ID_REMOTE_TIMER = "id_remote_timer_ico"
    }

    private lateinit var state: TimerState

    override fun renderTile(
        state: TimerState,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        this.state = state

        return LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        Clickable.Builder()
                            .setId(if (state.isLocalTimer) ID_LOCAL_TIMER else ID_REMOTE_TIMER)
                            .setOnClick(getTapAction(context, state.isLocalTimer))
                            .build()
                    )
                    .build()
            )
            .setHeight(expand())
            .setWidth(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(
                if (state is RemoteTimerTileState) {
                    if (state.connectionStatus == WearConnectionStatus.CONNECTED) {
                        renderNormalTile(state, deviceParameters)
                    } else {
                        renderTileForConnectionStatus(
                            state.connectionStatus,
                            state,
                            deviceParameters
                        )
                    }
                } else {
                    renderNormalTile(state, deviceParameters)
                }
            )
            .build()
    }

    private fun renderNormalTile(
        state: TimerState,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        return if (state.timerModel.isRunning) {
            TimerProgressLayout(
                context = context,
                deviceParameters,
                state
            )
        } else {
            StartTimerLayout(
                context = context,
                deviceParameters,
                state
            )
        }
    }

    private fun renderTileForConnectionStatus(
        connectionStatus: WearConnectionStatus,
        state: TimerState,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        return WearConnectionStatusLayout(context, deviceParameters, connectionStatus)
    }

    override fun ResourceBuilders.Resources.Builder.produceRequestedResources(
        resourceState: Unit,
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        resourceIds: MutableList<String>
    ) {
        val resources = mapOf(
            ID_LOCAL_TIMER to R.drawable.ic_hourglass_empty,
            ID_REMOTE_TIMER to R.drawable.ic_baseline_settings_remote_24
        )

        (resourceIds.takeIf { it.isNotEmpty() } ?: resources.keys).forEach { key ->
            resources[key]?.let { resId ->
                addIdToImageMapping(key, drawableResToImageResource(resId))
            }
        }
    }

    override fun getResourcesVersionForTileState(state: TimerState): String {
        return "isLocalTimer=${state.isLocalTimer}"
    }

    override val freshnessIntervalMillis: Long
        get() = if (state.isLocalTimer) {
            60000
        } else {
            super.freshnessIntervalMillis
        }
}

internal enum class TimerTileDuration {
    DURATION_5,
    DURATION_10,
    DURATION_15,
    DURATION_20,
    DURATION_30,
}