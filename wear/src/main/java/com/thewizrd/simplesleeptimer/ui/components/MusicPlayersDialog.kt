package com.thewizrd.simplesleeptimer.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.dialog.Dialog
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.ui.theme.activityViewModel
import com.thewizrd.simplesleeptimer.ui.theme.findActivity
import com.thewizrd.simplesleeptimer.viewmodels.MusicPlayersViewModel
import com.thewizrd.simplesleeptimer.viewmodels.SelectedPlayerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.tasks.await

@Composable
fun MusicPlayersDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit
) {
    val activityContext = LocalContext.current.findActivity()
    val musicPlayersViewModel = viewModel<MusicPlayersViewModel>()
    val selectedPlayerViewModel = activityViewModel<SelectedPlayerViewModel>(activityContext)

    MusicPlayersDialog(
        showDialog,
        onDismissRequest,
        activityContext,
        musicPlayersViewModel,
        selectedPlayerViewModel
    )
}

@Composable
private fun MusicPlayersDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    context: Context,
    musicPlayersViewModel: MusicPlayersViewModel,
    selectedPlayerViewModel: SelectedPlayerViewModel
) {
    val uiState by musicPlayersViewModel.uiState.collectAsState()
    val selectedPlayer by selectedPlayerViewModel.selectedPlayer.collectAsState()

    Dialog(
        modifier = Modifier.background(MaterialTheme.colors.surface),
        showDialog = showDialog,
        onDismissRequest = onDismissRequest
    ) {
        AnimatedVisibility(
            visible = !uiState.isLoading && uiState.players.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ScalingLazyColumn {
                item {
                    ListHeader {
                        Text(
                            text = stringResource(id = R.string.select_player_pause_prompt),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                items(uiState.players) { player ->
                    val isChecked = selectedPlayer.key != null && selectedPlayer.key == player.key

                    ToggleChip(
                        modifier = Modifier.fillMaxWidth(),
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectedPlayerViewModel.updateSelectedPlayer(player.key)
                            } else {
                                selectedPlayerViewModel.updateSelectedPlayer(null)
                            }

                            onDismissRequest.invoke()
                        },
                        label = {
                            Text(text = player.appLabel ?: "")
                        },
                        appIcon = {
                            player.bitmapIcon?.let {
                                Icon(
                                    modifier = Modifier.requiredSize(ToggleChipDefaults.IconSize),
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = player.appLabel ?: "",
                                    tint = Color.Unspecified
                                )
                            }
                        },
                        toggleControl = {
                            Row {
                                Icon(
                                    imageVector = ToggleChipDefaults.radioIcon(isChecked),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(ToggleChipDefaults.IconSize)
                                        .clip(CircleShape)
                                )
                            }
                        })
                }
            }
        }

        AnimatedVisibility(
            visible = !uiState.isLoading && uiState.players.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(4.dp),
                    text = stringResource(id = R.string.error_nomusicplayers),
                    textAlign = TextAlign.Center
                )
                CompactChip(
                    label = {
                        Text(text = stringResource(id = R.string.action_retry))
                    },
                    icon = {
                        Icon(
                            modifier = Modifier.requiredSize(ChipDefaults.SmallIconSize),
                            painter = painterResource(id = R.drawable.ic_baseline_refresh_24),
                            contentDescription = stringResource(id = R.string.action_retry)
                        )
                    },
                    onClick = {
                        musicPlayersViewModel.loadMusicPlayers()
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        LaunchedEffect(showDialog) {
            selectedPlayerViewModel.selectedPlayer.collectLatest { s ->
                runCatching {
                    val mapRequest =
                        PutDataMapRequest.create(SleepTimerHelper.SleepTimerAudioPlayerPath)
                    mapRequest.dataMap.putString(SleepTimerHelper.KEY_SELECTEDPLAYER, s.key ?: "")
                    Wearable.getDataClient(context).putDataItem(
                        mapRequest.asPutDataRequest()
                    ).await()
                }.onFailure {
                    Log.e("MusicPlayerFragment", "Error", it)
                }
            }
        }

        LaunchedEffect(showDialog) {
            musicPlayersViewModel.loadMusicPlayers()
        }

        LaunchedEffect(showDialog) {
            selectedPlayerViewModel.getSelectedPlayerData()
        }

        LaunchedEffect(uiState.players) {
            // Update selected player from list
            val playerPref = selectedPlayer.key
            val playerFound = uiState.players.any { it.key == playerPref }

            if (!playerFound) {
                selectedPlayerViewModel.updateSelectedPlayer(null)
            }
        }
    }
}