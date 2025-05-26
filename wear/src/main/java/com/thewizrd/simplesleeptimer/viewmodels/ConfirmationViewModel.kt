package com.thewizrd.simplesleeptimer.viewmodels

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.material.dialog.DialogDefaults
import com.thewizrd.simplesleeptimer.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class ConfirmationViewModel : ViewModel() {
    private val _confirmationEventsFlow = MutableStateFlow<ConfirmationData?>(null)

    val confirmationEventsFlow = _confirmationEventsFlow
        .distinctUntilChanged(areEquivalent = { old, new -> old == new })
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    fun showConfirmation(data: ConfirmationData) {
        _confirmationEventsFlow.update { data }
    }

    fun showSuccess(message: String? = null) {
        _confirmationEventsFlow.update {
            ConfirmationData.success(message)
        }
    }

    fun showFailure(message: String? = null) {
        _confirmationEventsFlow.update {
            ConfirmationData.failure(message)
        }
    }

    fun showOpenOnPhone(message: String? = null) {
        _confirmationEventsFlow.update {
            ConfirmationData.openOnPhone(message)
        }
    }

    fun clearFlow() {
        _confirmationEventsFlow.update { null }
    }
}

data class ConfirmationData(
    val title: String? = null,
    @DrawableRes val iconResId: Int? = R.drawable.ws_full_sad,
    @DrawableRes val animatedVectorResId: Int? = null,
    val durationMs: Long = DialogDefaults.ShortDurationMillis
) {
    companion object {
        fun success(message: String? = null) = ConfirmationData(
            animatedVectorResId = R.drawable.confirmation_animation,
            title = message
        )

        fun failure(message: String? = null) = ConfirmationData(
            animatedVectorResId = R.drawable.failure_animation,
            title = message
        )

        fun openOnPhone(message: String? = null) = ConfirmationData(
            animatedVectorResId = R.drawable.open_on_phone_animation,
            title = message
        )
    }
}