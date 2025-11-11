package com.thewizrd.simplesleeptimer.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.TimePickerType
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import java.time.Duration
import java.time.LocalTime

@Composable
fun SleepTimePickerDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    timerDuration: Duration,
    onTimeConfirm: (Duration) -> Unit
) {
    Dialog(
        visible = showDialog,
        onDismissRequest = onDismissRequest
    ) {
        TimePicker(
            modifier = Modifier.fillMaxSize(),
            initialTime = LocalTime.of(timerDuration.toHoursPart(), timerDuration.toMinutesPart()),
            timePickerType = TimePickerType.HoursMinutes24H,
            onTimePicked = {
                val duration = Duration.ofNanos(it.toNanoOfDay())
                onTimeConfirm.invoke(duration)
                onDismissRequest.invoke()
            }
        )
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun PreviewSleepTimePickerDialog() {
    SleepTimePickerDialog(
        showDialog = true,
        onDismissRequest = { },
        timerDuration = Duration.ofHours(2),
        onTimeConfirm = { },
    )
}