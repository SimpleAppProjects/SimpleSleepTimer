@file:Suppress("FunctionName")

package com.thewizrd.simplesleeptimer.wearable.tiles.layouts

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ActionBuilders.AndroidIntExtra
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.material3.ButtonDefaults.filledVariantButtonColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.simplesleeptimer.BuildConfig
import com.thewizrd.simplesleeptimer.wearable.tiles.TimerState
import com.thewizrd.simplesleeptimer.wearable.tiles.TimerTileDuration
import java.time.Instant
import java.util.Locale

internal fun MaterialScope.TimerButton(
    context: Context,
    state: TimerState,
    duration: TimerTileDuration
) = textButton(
    width = weight(1f),
    onClick = clickable(
        action = getLaunchTimerAction(
            context, state.isLocalTimer, when (duration) {
                TimerTileDuration.DURATION_5 -> 5
                TimerTileDuration.DURATION_10 -> 10
                TimerTileDuration.DURATION_15 -> 15
                TimerTileDuration.DURATION_20 -> 20
                TimerTileDuration.DURATION_30 -> 30
            }
        )
    ),
    labelContent = {
        text(
            text = String.format(
                Locale.ROOT, "%02d", when (duration) {
                    TimerTileDuration.DURATION_5 -> 5
                    TimerTileDuration.DURATION_10 -> 10
                    TimerTileDuration.DURATION_15 -> 15
                    TimerTileDuration.DURATION_20 -> 20
                    TimerTileDuration.DURATION_30 -> 30
                }
            ).layoutString
        )
    },
    colors = filledVariantButtonColors()
)

internal fun getDynamicDurationText(state: TimerState): DynamicString {
    val durationUntilEnd =
        DynamicBuilders.DynamicInstant.withSecondsPrecision(
            Instant.ofEpochMilli(
                state.timerModel?.endTimeInMs ?: 0
            )
        )
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