package com.thewizrd.simplesleeptimer

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.*
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.widget.WearableLinearLayoutManager
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import com.thewizrd.simplesleeptimer.adapters.PlayerListAdapter
import com.thewizrd.simplesleeptimer.adapters.PlayerListRemoteAdapter
import com.thewizrd.simplesleeptimer.databinding.FragmentMusicPlayersBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.math.roundToInt

class MusicPlayersFragment : Fragment(), OnDataChangedListener {
    companion object {
        private const val TAG = "MusicPlayersFragment"
    }

    private lateinit var binding: FragmentMusicPlayersBinding
    private lateinit var mAdapter: PlayerListAdapter
    private var timer: CountDownTimer? = null
    private var onClickListener: RecyclerOnClickListenerInterface? = null

    private val selectedPlayer: SelectedPlayerViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set timer for retrieving music player data
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val buff = Wearable.getDataClient(requireActivity())
                            .getDataItems(
                                WearableHelper.getWearDataUri(
                                    "*",
                                    WearableHelper.MusicPlayersPath
                                )
                            )
                            .await()

                        for (i in 0 until buff.count) {
                            val item = buff[i]
                            if (WearableHelper.MusicPlayersPath == item.uri.path) {
                                try {
                                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                                    updateMusicPlayers(dataMap)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error", e)
                                }
                                showProgressBar(false)
                            }
                        }
                        buff.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error", e)
                    }
                }
            }
        }

        selectedPlayer.key.observe(this, { s ->
            val mapRequest = PutDataMapRequest.create(SleepTimerHelper.SleepTimerAudioPlayerPath)
            mapRequest.dataMap.putString(SleepTimerHelper.KEY_SELECTEDPLAYER, s)
            Wearable.getDataClient(requireActivity()).putDataItem(
                mapRequest.asPutDataRequest()
            ).addOnFailureListener { e -> Log.e(TAG, "Error", e) }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMusicPlayersBinding.inflate(inflater, container, false)

        binding.playerList.setHasFixedSize(true)
        binding.playerList.isEdgeItemsCenteringEnabled = false
        binding.playerList.layoutManager = WearableLinearLayoutManager(requireActivity(), null)
        binding.playerList.setOnGenericMotionListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_SCROLL &&
                ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
            ) {
                // Don't forget the negation here
                val delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                        ViewConfigurationCompat.getScaledVerticalScrollFactor(
                            ViewConfiguration.get(v.context), v.context
                        )
                // Swap these axes to scroll horizontally instead
                v.scrollBy(0, delta.roundToInt())
                true
            } else {
                false
            }
        }
        binding.playerList.requestFocus()

        mAdapter = PlayerListRemoteAdapter(requireActivity())
        mAdapter.setOnClickListener(object : RecyclerOnClickListenerInterface {
            override fun onClick(view: View, position: Int) {
                onClickListener?.onClick(view, position)
            }
        })
        binding.playerList.adapter = mAdapter
        binding.playerList.visibility = View.GONE

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.playerList.viewTreeObserver.addOnPreDrawListener(object :
            ViewTreeObserver.OnPreDrawListener {
            /* BoxInsetLayout impl */
            private val FACTOR = 0.146447f //(1 - sqrt(2)/2)/2
            private val mIsRound = resources.configuration.isScreenRound
            private val paddingTop = binding.playerList.paddingTop
            private val paddingBottom = binding.playerList.paddingBottom
            private val paddingStart = ViewCompat.getPaddingStart(binding.playerList)
            private val paddingEnd = ViewCompat.getPaddingEnd(binding.playerList)

            override fun onPreDraw(): Boolean {
                if (binding.playerList.visibility != View.VISIBLE || binding.playerList.measuredWidth <= 0) return true
                binding.playerList.viewTreeObserver.removeOnPreDrawListener(this)

                val verticalPadding =
                    resources.getDimensionPixelSize(R.dimen.inner_frame_layout_padding)
                val mScreenHeight = Resources.getSystem().displayMetrics.heightPixels
                val mScreenWidth = Resources.getSystem().displayMetrics.widthPixels
                val rightEdge = Math.min(binding.playerList.measuredWidth, mScreenWidth)
                val bottomEdge = Math.min(binding.playerList.measuredHeight, mScreenHeight)
                val verticalInset = (FACTOR * Math.max(rightEdge, bottomEdge)).toInt()

                binding.playerList.setPaddingRelative(
                    paddingStart,
                    paddingTop,
                    paddingEnd,
                    paddingBottom + if (mIsRound) verticalInset else verticalPadding
                )

                return true
            }
        })
    }

    fun setOnClickListener(listener: RecyclerOnClickListenerInterface?) {
        onClickListener = listener
    }

    private fun showProgressBar(show: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(requireActivity()).addListener(this)

        binding.playerList.requestFocus()

        LocalBroadcastManager.getInstance(requireActivity())
            .sendBroadcast(Intent(WearableHelper.MusicPlayersPath))
        timer!!.start()
        getSelectedPlayerData()
    }

    private fun getSelectedPlayerData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var prefKey: String? = null
            try {
                val buff = Wearable.getDataClient(requireActivity())
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            SleepTimerHelper.SleepTimerAudioPlayerPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (SleepTimerHelper.SleepTimerAudioPlayerPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            prefKey = dataMap.getString(SleepTimerHelper.KEY_SELECTEDPLAYER, null)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error", e)
                        }
                        break
                    }
                }
                buff.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
                prefKey = null
            }
            selectedPlayer.setKey(prefKey)
        }
    }

    override fun onPause() {
        super.onPause()
        timer?.cancel()
        Wearable.getDataClient(requireActivity()).removeListener(this)
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Cancel timer
            timer?.cancel()
            showProgressBar(false)

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (WearableHelper.MusicPlayersPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateMusicPlayers(dataMap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error", e)
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateMusicPlayers(dataMap: DataMap) {
        val supported_players =
            dataMap.getStringArrayList(WearableHelper.KEY_SUPPORTEDPLAYERS) ?: return
        val viewModels = ArrayList<MusicPlayerViewModel>()
        val playerPref = selectedPlayer.key.value
        var selectedPlayerModel: MusicPlayerViewModel? = null
        for (key in supported_players) {
            val map = dataMap.getDataMap(key) ?: continue

            val model = MusicPlayerViewModel().apply {
                appLabel = map.getString(WearableHelper.KEY_LABEL)
                packageName = map.getString(WearableHelper.KEY_PKGNAME)
                activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
                bitmapIcon = try {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(requireActivity()),
                        map.getAsset(WearableHelper.KEY_ICON)
                    )
                } catch (e: Exception) {
                    // ignore
                    null
                }
            }

            viewModels.add(model)

            if (playerPref != null && model.key == playerPref) {
                selectedPlayerModel = model
            }
        }

        selectedPlayer.setKey(selectedPlayerModel?.key)

        viewLifecycleOwner.lifecycleScope.launch {
            mAdapter.updateItems(viewModels)

            binding.noplayersMessageview.visibility =
                if (viewModels.size > 0) View.GONE else View.VISIBLE
            binding.playerList.visibility = if (viewModels.size > 0) View.VISIBLE else View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                if (binding.playerList.visibility == View.VISIBLE && !binding.playerList.hasFocus()) {
                    binding.playerList.requestFocus()
                }
            }
        }
    }
}