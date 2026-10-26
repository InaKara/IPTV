package com.example.iptv.ui.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.iptv.util.AppTaskNavigation

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Focus belongs to a placed target in a resumed, attached window, never an old screen. */
@Stable
internal class FocusRestoration(private val view: View, private val lifecycle: Lifecycle) {
    private data class Anchor(val owner: Any, val coordinates: LayoutCoordinates)
    // Layout callbacks must not write snapshot state that their own layout can observe.
    private val anchors = mutableMapOf<FocusRequester, Anchor>()
    private data class Pending(val requester: FocusRequester, val current: () -> Boolean, val focused: () -> Unit)
    private var pending: Pending? = null
    private var posted = false
    private val deliver = Runnable {
        posted = false
        val request = pending
        pending = null
        if (request != null && request.current() && this.request(request.requester)) {
            request.focused()
        }
    }
    var version by mutableIntStateOf(0)
        private set
    private var disposed = false
    private val activity = view.context.activity()
    private val active get() = !disposed && view.isAttachedToWindow && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        activity?.let { !it.isFinishing && !it.isDestroyed && !AppTaskNavigation.isClosing(it) } != false

    fun changed() { pending = null; view.removeCallbacks(deliver); posted = false; version++ }
    fun close() { disposed = true; pending = null; view.removeCallbacks(deliver); posted = false; anchors.clear() }
    fun placed(requester: FocusRequester, owner: Any, coordinates: LayoutCoordinates) {
        if (!disposed) { anchors[requester] = Anchor(owner, coordinates); schedule() }
    }
    fun detached(requester: FocusRequester, owner: Any) {
        if (anchors[requester]?.owner === owner) {
            anchors.remove(requester)
            if (pending?.requester === requester) pending = null
        }
    }
    fun request(requester: FocusRequester): Boolean {
        if (!active || anchors[requester]?.coordinates?.isAttached != true) return false
        requester.requestFocus()
        return true
    }
    fun restore(requester: FocusRequester, onFocused: () -> Unit = {}, current: () -> Boolean = { true }) {
        pending = if (active && current()) Pending(requester, current, onFocused) else null
        schedule()
    }
    private fun schedule() {
        val request = pending ?: return
        if (!posted && active && anchors[request.requester]?.coordinates?.isAttached == true) {
            posted = true
            view.post(deliver)
        }
    }
}

@Composable
internal fun rememberFocusRestoration(): FocusRestoration {
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = remember(view, lifecycle) { FocusRestoration(view, lifecycle) }
    DisposableEffect(view, lifecycle, focus) {
        val observer = LifecycleEventObserver { _, _ -> focus.changed() }
        val attached = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = focus.changed()
            override fun onViewDetachedFromWindow(v: View) = focus.changed()
        }
        lifecycle.addObserver(observer); view.addOnAttachStateChangeListener(attached)
        onDispose { lifecycle.removeObserver(observer); view.removeOnAttachStateChangeListener(attached); focus.close() }
    }
    return focus
}

internal fun Modifier.focusAnchor(requester: FocusRequester, focus: FocusRestoration): Modifier = composed {
    val owner = remember { Any() }
    DisposableEffect(requester, focus) { onDispose { focus.detached(requester, owner) } }
    onGloballyPositioned { focus.placed(requester, owner, it) }
}

internal fun Modifier.attachedFocusRequester(requester: FocusRequester, focus: FocusRestoration): Modifier =
    focusRequester(requester).focusAnchor(requester, focus)
