package com.thewizrd.simplesleeptimer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.ui.theme.activityViewModel
import com.thewizrd.simplesleeptimer.ui.theme.findActivity
import com.thewizrd.simplesleeptimer.viewmodels.MusicPlayersViewModel
import com.thewizrd.simplesleeptimer.viewmodels.SelectedPlayerViewModel
import kotlinx.coroutines.flow.collectLatest

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
        musicPlayersViewModel,
        selectedPlayerViewModel
    )
}

@Composable
private fun MusicPlayersDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    musicPlayersViewModel: MusicPlayersViewModel,
    selectedPlayerViewModel: SelectedPlayerViewModel
) {
    val uiState by musicPlayersViewModel.uiState.collectAsState()
    val selectedPlayer by selectedPlayerViewModel.selectedPlayer.collectAsState()

    Dialog(
        visible = showDialog,
        onDismissRequest = onDismissRequest
    ) {
        LoadingContent(
            empty = uiState.players.isEmpty(),
            loading = uiState.isLoading,
            emptyContent = {
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
                    CompactButton(
                        label = {
                            Text(text = stringResource(id = R.string.action_retry))
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(id = R.string.action_retry)
                            )
                        },
                        onClick = {
                            musicPlayersViewModel.loadMusicPlayers()
                        }
                    )
                }
            }
        ) {
            val columnState = rememberTransformingLazyColumnState()
            val contentPadding = rememberResponsiveColumnPadding(
                first = ColumnItemType.ListHeader,
                last = ColumnItemType.Button,
            )
            val transformationSpec = rememberTransformationSpec()

            ScreenScaffold(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                scrollState = columnState
            ) { contentPadding ->
                TransformingLazyColumn(
                    contentPadding = contentPadding
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Text(
                                text = stringResource(id = R.string.select_player_pause_prompt),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    items(uiState.players) { player ->
                        val isChecked =
                            selectedPlayer.key != null && selectedPlayer.key == player.key

                        RadioButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                            selected = isChecked,
                            onSelect = {
                                if (!isChecked) {
                                    selectedPlayerViewModel.updateSelectedPlayer(player.key)
                                } else {
                                    selectedPlayerViewModel.updateSelectedPlayer(null)
                                }

                                onDismissRequest.invoke()
                            },
                            label = {
                                Text(text = player.appLabel ?: "")
                            },
                            icon = {
                                player.bitmapIcon?.let {
                                    Icon(
                                        modifier = Modifier.requiredSize(ButtonDefaults.IconSize),
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = player.appLabel ?: "",
                                        tint = Color.Unspecified
                                    )
                                }
                            })
                    }
                }
            }
        }

        LaunchedEffect(showDialog) {
            selectedPlayerViewModel.selectedPlayer.collectLatest { s ->
                runCatching {
                    selectedPlayerViewModel.sendSelectedPlayerUpdate(s.key)
                }.onFailure {
                    Logger.error("MusicPlayerFragment", it, "Error")
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