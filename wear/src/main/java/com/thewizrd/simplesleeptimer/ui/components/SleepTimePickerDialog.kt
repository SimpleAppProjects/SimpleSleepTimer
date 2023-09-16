package com.thewizrd.simplesleeptimer.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.composables.TimePicker
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
        showDialog = showDialog,
        onDismissRequest = onDismissRequest
    ) {
        TimePicker(
            modifier = Modifier.fillMaxSize(),
            time = LocalTime.of(timerDuration.toHoursPart(), timerDuration.toMinutesPart()),
            showSeconds = false,
            onTimeConfirm = {
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