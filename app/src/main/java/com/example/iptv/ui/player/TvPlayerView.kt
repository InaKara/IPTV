package com.example.iptv.ui.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.PopupWindow
import com.example.iptv.data.model.M3UChannel
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.example.iptv.R

/** Native controller owns visible navigation; hidden-controller keys keep the TV shortcuts. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class TvPlayerView(context: Context) : PlayerView(context) {
    var nextChannel: () -> Unit = {}
    var previousChannel: () -> Unit = {}
    var seek: (Long) -> Unit = {}
    var onControlsVisibilityChanged: (Boolean) -> Unit = {}
    var channel: M3UChannel? = null
        set(value) { field = value; updateInfo() }
    private var infoPopup: PopupWindow? = null
    private var infoText: TextView? = null
    private var restoreInfoFocus = true
    private val infoTimeout = Runnable {
        dismissInfo(false)
        hideController()
        requestFocus()
    }
    private val infoButton = Button(context).apply {
        id = R.id.playback_info
        text = context.getString(R.string.playback_info)
        isAllCaps = false; textSize = 14f; minWidth = dp(64); minimumHeight = dp(48)
        backgroundTintList = null; stateListAnimator = null
        setOnClickListener { openInfo() }
    }
    private val controls get() = findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
    var isLiveChannel: Boolean = false
        set(value) { field = value; if (!value) dismissInfo(false); refreshLiveControls(); scheduleLiveTick() }
    private val liveLag = TextView(context).apply {
        id = R.id.live_lag
        setTextColor(Color.WHITE)
        textSize = 14f
        setPadding(dp(8), 0, dp(8), 0)
    }
    private val liveButton = Button(context).apply {
        id = R.id.go_live
        isAllCaps = false
        textSize = 14f
        minWidth = dp(72)
        minimumHeight = dp(48)
        backgroundTintList = null
        stateListAnimator = null
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { player?.takeIf { canGoLive(it) }?.seekToDefaultPosition(); resetTimeout() }
    }
    private val ordinaryTimes = mutableListOf<View>()
    private var boundPlayer: Player? = null
    private val liveListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refreshLiveControls(); updateInfo() }
    }
    private val liveTick = object : Runnable {
        override fun run() {
            if (isAttachedToWindow && isLiveChannel && isControllerFullyVisible) {
                refreshLiveControls()
                postDelayed(this, 1000)
            }
        }
    }

    init {
        useController = true
        controllerAutoShow = false
        controllerShowTimeoutMs = PlayerViewModel.OVERLAY_MS.toInt()
        setShowNextButton(false)
        setShowPreviousButton(false)
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        controls.setAnimationEnabled(false)
        configureBottomPanel()
        findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_time).let { time ->
            for (i in 0 until time.childCount) ordinaryTimes.add(time.getChildAt(i))
            time.addView(liveLag)
            time.addView(liveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
            time.addView(infoButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        }
        styleControls(controls)
        setControllerVisibilityListener(ControllerVisibilityListener { visibility ->
            onControlsVisibilityChanged(visibility == View.VISIBLE)
            if (visibility != View.VISIBLE) { dismissInfo(false); requestFocus() }
            refreshLiveControls()
            scheduleLiveTick()
        })
        refreshLiveControls()
        hideController()
    }

    override fun setPlayer(player: Player?) {
        if (player == null) dismissInfo(false)
        boundPlayer?.removeListener(liveListener)
        boundPlayer = null
        super.setPlayer(player)
        bindLiveListener()
        refreshLiveControls()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bindLiveListener()
        refreshLiveControls()
        scheduleLiveTick()
    }

    override fun onDetachedFromWindow() {
        dismissInfo(false)
        removeCallbacks(infoTimeout)
        removeCallbacks(liveTick)
        boundPlayer?.removeListener(liveListener)
        boundPlayer = null
        super.onDetachedFromWindow()
    }

    private fun bindLiveListener() {
        if (isAttachedToWindow && boundPlayer !== player) {
            boundPlayer?.removeListener(liveListener)
            boundPlayer = player
            boundPlayer?.addListener(liveListener)
        }
    }

    private fun scheduleLiveTick() {
        removeCallbacks(liveTick)
        if (isAttachedToWindow && isLiveChannel && isControllerFullyVisible) postDelayed(liveTick, 1000)
    }

    private fun refreshLiveControls() {
        val wasFocused = liveButton.hasFocus()
        ordinaryTimes.forEach { it.visibility = if (isLiveChannel) View.GONE else View.VISIBLE }
        liveLag.visibility = if (isLiveChannel) View.VISIBLE else View.GONE
        liveButton.visibility = if (isLiveChannel) View.VISIBLE else View.GONE
        infoButton.visibility = if (isLiveChannel) View.VISIBLE else View.GONE
        val p = player
        val target = p?.let { liveDefaultPosition(it) }
        val lag = if (target != null && p != null) (target - p.currentPosition.coerceAtLeast(0)).coerceAtLeast(0) else null
        val atLive = lag != null && lag <= 2000
        liveLag.text = liveLagText(lag)
        liveLag.contentDescription = context.getString(R.string.live_lag_description, liveLag.text)
        liveButton.text = context.getString(if (atLive) R.string.at_live_button else R.string.live_button)
        liveButton.contentDescription = context.getString(if (atLive) R.string.at_live else R.string.jump_to_live)
        liveButton.isEnabled = p != null && canGoLive(p)
        liveButton.alpha = if (liveButton.isEnabled) 1f else 0.4f
        if (wasFocused && (!isLiveChannel || !liveButton.isEnabled)) {
            // Play/Pause can also be disabled while the replacement timeline is empty.
            val fallback = listOf(androidx.media3.ui.R.id.exo_play_pause, androidx.media3.ui.R.id.exo_settings)
                .map { findViewById<View>(it) }.firstOrNull { it.isEnabled && it.isShown }
            (fallback ?: this).requestFocus()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun configureBottomPanel() {
        // Keep Media3's layout manager/IDs, but never shade the picture above the controls.
        findViewById<View>(androidx.media3.ui.R.id.exo_controls_background).setBackgroundColor(Color.TRANSPARENT)
        findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { height = dp(52); topMargin = 0 }
        }
        controls.addView(View(context).apply {
            id = R.id.playback_controls_panel
            setBackgroundColor(0xb3000000.toInt())
        }, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CONTROLS_PANEL_DP), Gravity.BOTTOM))
        findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_center_controls).apply {
            setPadding(0, 0, 0, 0)
            // Keep 52dp TV focus targets without expanding this row through native padding.
            for (index in 0 until childCount) getChildAt(index).apply {
                minimumWidth = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                layoutParams = layoutParams.apply { width = dp(52); height = dp(52) }
            }
            layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                height = dp(52)
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(76)
            }
        }
        findViewById<View>(androidx.media3.ui.R.id.exo_progress).apply {
            layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { height = dp(24) }
        }
    }

    fun openControls() {
        showController()
        val play = findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
        if (play.isEnabled && play.visibility == View.VISIBLE) play.requestFocus()
        resetTimeout()
    }

    fun closeControls(): Boolean {
        if (infoPopup != null) { dismissInfo(true); return true }
        if (!isControllerFullyVisible) return false
        hideController()
        requestFocus()
        return true
    }

    private fun resetTimeout() {
        removeCallbacks(infoTimeout)
        // The Info popup owns the shared expiry while Media3's modal-popup timer is suspended.
        controls.showTimeoutMs = if (infoPopup != null) 0 else PlayerViewModel.OVERLAY_MS.toInt()
        if (infoPopup != null) postDelayed(infoTimeout, PlayerViewModel.OVERLAY_MS)
    }
    private fun dismissInfo(restoreFocus: Boolean) {
        restoreInfoFocus = restoreFocus
        infoPopup?.dismiss()
        restoreInfoFocus = true
    }

    private fun openInfo() {
        if (!isLiveChannel || infoPopup != null) return
        infoText = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 16f
            setPadding(dp(20), dp(16), dp(20), dp(16))
            maxLines = 9; ellipsize = android.text.TextUtils.TruncateAt.END
            isFocusable = true; isFocusableInTouchMode = true
            setOnKeyListener { _, _, event -> if (event.action == KeyEvent.ACTION_DOWN) resetTimeout(); false }
        }
        updateInfo()
        val popup = PopupWindow(infoText, minOf(dp(420), (width * .6f).toInt()).coerceAtLeast(1), dp(220), true)
        popup.setBackgroundDrawable(ColorDrawable(0xee191d1e.toInt()))
        popup.isOutsideTouchable = true
        popup.setTouchInterceptor { _, event -> if (event.action == android.view.MotionEvent.ACTION_DOWN) resetTimeout(); false }
        popup.setOnDismissListener {
            infoPopup = null; infoText = null
            resetTimeout()
            if (restoreInfoFocus && isControllerFullyVisible && isAttachedToWindow) infoButton.requestFocus()
        }
        infoPopup = popup
        resetTimeout()
        popup.showAtLocation(this, Gravity.BOTTOM or Gravity.END, dp(24), dp(CONTROLS_PANEL_DP + 12))
        infoText?.requestFocus()
    }

    private fun updateInfo() {
        val text = infoText ?: return
        val unknown = context.getString(R.string.playback_info_unknown)
        val video = player?.currentTracks?.groups?.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) } }
        val audio = player?.currentTracks?.groups?.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) } }
        val resolution = if (video != null && video.width > 0 && video.height > 0) "${video.width} × ${video.height}" else unknown
        val format = audio?.sampleMimeType?.let { mime ->
            val label = when (mime) { "audio/mpeg-L2" -> "MP2"; "audio/mp4a-latm" -> "AAC"; "audio/ac3" -> "AC-3"; "audio/eac3" -> "E-AC-3"; "audio/mpeg" -> "MPEG audio"; "audio/opus" -> "Opus"; "audio/flac" -> "FLAC"; else -> unknown }
            listOf(label, audio.channelCount.takeIf { it > 0 }?.let { "$it ch" }, audio.sampleRate.takeIf { it > 0 }?.let { "$it Hz" }).filterNotNull().joinToString(" · ")
        } ?: unknown
        text.text = listOfNotNull(context.getString(R.string.playback_info_channel, channel?.name ?: unknown),
            context.getString(R.string.playback_info_category, channel?.groupTitle ?: unknown),
            channel?.providerNumber?.takeIf(String::isNotBlank)?.let { context.getString(R.string.playback_info_number, it) },
            context.getString(R.string.playback_info_video, resolution), context.getString(R.string.playback_info_audio, format)).joinToString("\n")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        if (code == KeyEvent.KEYCODE_MENU) {
            if (down) openControls()
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK && isControllerFullyVisible) {
            if (event.action == KeyEvent.ACTION_UP) closeControls()
            return true
        }
        val shortcut: (() -> Unit)? = when (code) {
            KeyEvent.KEYCODE_CHANNEL_UP -> nextChannel
            KeyEvent.KEYCODE_CHANNEL_DOWN -> previousChannel
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> ({ player?.let { if (it.playWhenReady) it.pause() else it.play() } })
            KeyEvent.KEYCODE_MEDIA_PLAY -> ({ player?.play() })
            KeyEvent.KEYCODE_MEDIA_PAUSE -> ({ player?.pause() })
            KeyEvent.KEYCODE_MEDIA_REWIND -> ({ seek(-PlayerViewModel.DEDICATED_SEEK_MS) })
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> ({ seek(PlayerViewModel.DEDICATED_SEEK_MS) })
            else -> null
        }
        if (shortcut != null) {
            if (down) shortcut()
            if (isControllerFullyVisible) resetTimeout()
            return true
        }
        if (isControllerFullyVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) resetTimeout()
            return super.dispatchKeyEvent(event)
        }
        val hiddenAction: (() -> Unit)? = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> nextChannel
            KeyEvent.KEYCODE_DPAD_DOWN -> previousChannel
            KeyEvent.KEYCODE_DPAD_LEFT -> ({ if (!isLiveChannel) seek(-PlayerViewModel.DPAD_SEEK_MS) })
            KeyEvent.KEYCODE_DPAD_RIGHT -> ({ if (!isLiveChannel) seek(PlayerViewModel.DPAD_SEEK_MS) })
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                ({ player?.let { if (it.playWhenReady) it.pause() else it.play() } })
            else -> null
        }
        if (hiddenAction != null) {
            if (down) hiddenAction()
            return true // Consume key-up and repeats too; native code must not reveal controls.
        }
        return super.dispatchKeyEvent(event)
    }

    private fun styleControls(view: View) {
        if (view.isFocusable || view.isClickable) {
            view.isFocusableInTouchMode = true
            view.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(AMBER))
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }
            val tint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(DARK, Color.WHITE))
            when (view) {
                is ImageView -> view.imageTintList = tint
                is TextView -> view.setTextColor(tint)
                is DefaultTimeBar -> view.onFocusChangeListener = OnFocusChangeListener { _, focused ->
                    view.setPlayedColor(if (focused) DARK else AMBER)
                    view.setScrubberColor(if (focused) DARK else Color.WHITE)
                }
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) styleControls(view.getChildAt(i))
    }

    companion object {
        const val CONTROLS_PANEL_DP = 128
        const val AMBER = 0xffffd15c.toInt()
        const val DARK = 0xff171817.toInt()
    }
}
