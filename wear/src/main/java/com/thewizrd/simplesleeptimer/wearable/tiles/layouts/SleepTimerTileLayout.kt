@file:Suppress("FunctionName")

package com.thewizrd.simplesleeptimer.wearable.tiles.layouts

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ActionBuilders.AndroidIntExtra
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.CONTENT_SCALE_MODE_FIT
import androidx.wear.protolayout.LayoutElementBuilders.ColorFilter
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.TypeBuilders.StringProp
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.LayoutDefaults.DEFAULT_VERTICAL_SPACER_HEIGHT
import androidx.wear.protolayout.material.layouts.MultiButtonLayout
import androidx.wear.protolayout.material.layouts.MultiButtonLayout.FIVE_BUTTON_DISTRIBUTION_BOTTOM_HEAVY
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.simplesleeptimer.BuildConfig
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_LOCAL_TIMER
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_REMOTE_TIMER
import com.thewizrd.simplesleeptimer.wearable.tiles.TimerState
import com.thewizrd.simplesleeptimer.wearable.tiles.TimerTileDuration
import java.time.Instant
import java.util.Locale

fun StartTimerLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    state: TimerState
): LayoutElementBuilders.LayoutElement {
    val isSmol = minOf(deviceParameters.screenHeightDp, deviceParameters.screenWidthDp) <= 192f

    return PrimaryLayout.Builder(deviceParameters)
        .setContent(
            MultiButtonLayout.Builder()
                .addButtonContent(TimerButton(context, state, TimerTileDuration.DURATION_5))
                .addButtonContent(TimerButton(context, state, TimerTileDuration.DURATION_10))
                .addButtonContent(TimerButton(context, state, TimerTileDuration.DURATION_15))
                .addButtonContent(TimerButton(context, state, TimerTileDuration.DURATION_20))
                .addButtonContent(TimerButton(context, state, TimerTileDuration.DURATION_30))
                .setFiveButtonDistribution(FIVE_BUTTON_DISTRIBUTION_BOTTOM_HEAVY)
                .build()
        )
        .setPrimaryChipContent(
            CompactChip.Builder(
                context,
                if (state.isLocalTimer) {
                    context.getString(R.string.action_open)
                } else {
                    context.getString(R.string.label_remote)
                },
                Clickable.Builder()
                    .setOnClick(getTapAction(context, state.isLocalTimer))
                    .build(),
                deviceParameters
            ).build()
        )
        .build()
}

fun TimerProgressLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    state: TimerState
): LayoutElementBuilders.LayoutElement {
    val timerLengthInMins = state.timerModel.timerLengthInMins
    val hours = timerLengthInMins / 60
    val minutes = timerLengthInMins - (hours * 60)

    return Column.Builder()
        .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        .setHeight(wrap())
        .setWidth(expand())
        .addContent(
            Image.Builder()
                .setResourceId(if (state.isLocalTimer) ID_LOCAL_TIMER else ID_REMOTE_TIMER)
                .setHeight(dp(24f))
                .setWidth(dp(24f))
                .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
                .setColorFilter(
                    ColorFilter.Builder()
                        .setTint(
                            ColorBuilders.argb(
                                ContextCompat.getColor(context, R.color.colorSecondary)
                            )
                        )
                        .build()
                )
                .build()
        )
        .addContent(Spacer.Builder().setHeight(DEFAULT_VERTICAL_SPACER_HEIGHT).build())
        .addContent(
            Column.Builder()
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .setHeight(wrap())
                .setWidth(expand())
                .addContent(
                    Text.Builder(
                        context,
                        StringProp.Builder("--")
                            .setDynamicValue(
                                getDynamicDurationText(state)
                            )
                            .build(),
                        StringLayoutConstraint.Builder("99 : 99 : 99")
                            .build()
                    )
                        .setMaxLines(1)
                        .setTypography(Typography.TYPOGRAPHY_TITLE3)
                        .setColor(
                            ColorBuilders.argb(Color.WHITE)
                        )
                        .build()
                )
                .build()
        )
        .addContent(
            Text.Builder(
                context,
                if (hours > 0) {
                    context.getString(R.string.label_tile_duration_hr_min, hours, minutes)
                } else {
                    context.getString(R.string.label_tile_duration_mins, minutes)
                }
            )
                .setMaxLines(1)
                .setTypography(Typography.TYPOGRAPHY_TITLE3)
                .setColor(
                    ColorBuilders.argb(
                        ContextCompat.getColor(context, R.color.colorSecondary)
                    )
                )
                .build()
        )
        .build()
}

fun WearConnectionStatusLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    connectionStatus: WearConnectionStatus
) = PrimaryLayout.Builder(deviceParameters)
    .setContent(
        Text.Builder(
            context,
            when (connectionStatus) {
                WearConnectionStatus.DISCONNECTED -> context.getString(R.string.status_disconnected)
                WearConnectionStatus.CONNECTING -> context.getString(R.string.status_connecting)
                WearConnectionStatus.APPNOTINSTALLED -> context.getString(R.string.error_sleeptimer_notinstalled)
                WearConnectionStatus.CONNECTED -> context.getString(R.string.status_connected)
            }
        )
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(
                ColorBuilders.argb(
                    ContextCompat.getColor(context, R.color.colorSecondary)
                )
            )
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .setMaxLines(1)
            .build()
    )
    .setPrimaryChipContent(
        CompactChip.Builder(
            context,
            if (connectionStatus == WearConnectionStatus.CONNECTING) {
                context.getString(R.string.action_retry)
            } else {
                context.getString(R.string.action_open)
            },
            Clickable.Builder()
                .setOnClick(
                    if (connectionStatus == WearConnectionStatus.CONNECTING) {
                        ActionBuilders.LoadAction.Builder().build()
                    } else {
                        getTapAction(context, false)
                    }
                )
                .build(),
            deviceParameters
        )
            .build()
    )
    .build()

private fun TimerButton(
    context: Context,
    state: TimerState,
    duration: TimerTileDuration
) = Button.Builder(
    context,
    Clickable.Builder()
        .setOnClick(
            getLaunchTimerAction(
                context, state.isLocalTimer, when (duration) {
                    TimerTileDuration.DURATION_5 -> 5
                    TimerTileDuration.DURATION_10 -> 10
                    TimerTileDuration.DURATION_15 -> 15
                    TimerTileDuration.DURATION_20 -> 20
                    TimerTileDuration.DURATION_30 -> 30
                }
            )
        )
        .build()
)
    .setTextContent(
        String.format(
            Locale.ROOT, "%02d", when (duration) {
                TimerTileDuration.DURATION_5 -> 5
                TimerTileDuration.DURATION_10 -> 10
                TimerTileDuration.DURATION_15 -> 15
                TimerTileDuration.DURATION_20 -> 20
                TimerTileDuration.DURATION_30 -> 30
            }
        ), Typography.TYPOGRAPHY_TITLE3
    )
    .setButtonColors(
        ButtonColors.secondaryButtonColors(
            Colors(
                ContextCompat.getColor(context, R.color.colorSecondary),
                Color.BLACK,
                ContextCompat.getColor(context, R.color.md_theme_dark_surface),
                ContextCompat.getColor(context, R.color.colorSecondary)
            )
        )
    )
    .build()

private fun getDynamicDurationText(state: TimerState): DynamicString {
    val durationUntilEnd =
        DynamicBuilders.DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(state.timerModel.endTimeInMs))
            .durationUntil(
                DynamicBuilders.DynamicInstant.platformTimeWithSecondsPrecision()
            )

    return DynamicString.constant("")
        .concat(
            DynamicString.onCondition(durationUntilEnd.hoursPart.gt(0))
                .use(
                    durationUntilEnd.hoursPart.format(
                        DynamicBuilders.DynamicInt32.IntFormatter.Builder()
                            .setMinIntegerDigits(2)
                            .build()
                    ).concat(DynamicString.constant(" : "))
                )
                .elseUse("")
        )
        .concat(
            DynamicString.onCondition(durationUntilEnd.minutesPart.gt(0))
                .use(
                    durationUntilEnd.minutesPart.format(
                        DynamicBuilders.DynamicInt32.IntFormatter.Builder()
                            .setMinIntegerDigits(2)
                            .build()
                    ).concat(DynamicString.constant(" : "))
                )
                .elseUse("")
        )
        .concat(
            DynamicString.onCondition(durationUntilEnd.minutesPart.gt(0))
                .use(
                    durationUntilEnd.secondsPart.format(
                        DynamicBuilders.DynamicInt32.IntFormatter.Builder()
                            .setMinIntegerDigits(2)
                            .build()
                    )
                )
                .elseUse(
                    durationUntilEnd.secondsPart.format()
                )
        )
}

private fun getLaunchActivityName(isLocalTimer: Boolean): String = if (isLocalTimer) {
    ".LaunchLocalActivity"
} else {
    ".LaunchActivity"
}

internal fun getTapAction(context: Context, isLocalTimer: Boolean): ActionBuilders.Action {
    return ActionBuilders.launchAction(
        ComponentName(context.packageName, context.packageName.run {
            if (BuildConfig.DEBUG) removeSuffix(".debug") else this
        } + getLaunchActivityName(isLocalTimer))
    )
}

internal fun getLaunchTimerAction(
    context: Context,
    isLocalTimer: Boolean,
    timeInMins: Int
): ActionBuilders.Action {
    return ActionBuilders.launchAction(
        ComponentName(context.packageName, context.packageName.run {
            if (BuildConfig.DEBUG) removeSuffix(".debug") else this
        } + getLaunchActivityName(isLocalTimer)),
        mutableMapOf<String?, ActionBuilders.AndroidExtra?>().apply {
            put(
                BaseTimerService.EXTRA_TIME_IN_MINS,
                AndroidIntExtra.Builder().setValue(timeInMins).build()
            )
        }
    )
}