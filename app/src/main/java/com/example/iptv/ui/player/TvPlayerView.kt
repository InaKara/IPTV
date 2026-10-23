package com.example.iptv.ui.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView

/** Native controller owns visible navigation; hidden-controller keys keep the TV shortcuts. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class TvPlayerView(context: Context) : PlayerView(context) {
    var nextChannel: () -> Unit = {}
    var previousChannel: () -> Unit = {}
    var seek: (Long) -> Unit = {}
    private val controls get() = findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)

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
        styleControls(controls)
        setControllerVisibilityListener(ControllerVisibilityListener { visibility ->
            if (visibility != View.VISIBLE) requestFocus()
        })
        hideController()
    }

    fun openControls() {
        showController()
        val play = findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
        if (play.isEnabled && play.visibility == View.VISIBLE) play.requestFocus()
        resetTimeout()
    }

    fun closeControls(): Boolean {
        if (!isControllerFullyVisible) return false
        hideController()
        requestFocus()
        return true
    }

    private fun resetTimeout() {
        // Use Media3's own scheduler, including its suspension while a settings popup is open.
        controls.showTimeoutMs = PlayerViewModel.OVERLAY_MS.toInt()
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
            KeyEvent.KEYCODE_DPAD_LEFT -> ({ seek(-PlayerViewModel.DPAD_SEEK_MS) })
            KeyEvent.KEYCODE_DPAD_RIGHT -> ({ seek(PlayerViewModel.DPAD_SEEK_MS) })
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
        const val AMBER = 0xffffd15c.toInt()
        const val DARK = 0xff171817.toInt()
    }
}
