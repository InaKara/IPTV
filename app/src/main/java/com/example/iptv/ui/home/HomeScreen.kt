package com.example.iptv.ui.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.iptv.R
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.ui.browser.*
import kotlinx.coroutines.flow.first
import kotlin.math.min

internal object BrowserColors {
    val page = Color(0xFF101314)
    val sidebar = Color(0xFF0C0F10)
    val tile = Color(0xFF191D1E)
    val border = Color(0xFF363C3E)
    val text = Color(0xFFF5F5F3)
    val secondary = Color(0xFFD4D8D9)
    val amber = Color(0xFFFFD15C)
    val dark = Color(0xFF111512)
}

private data class FocusCommand(val target: String, val sequence: Int)

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BrowserScreen(
    channels: List<M3UChannel>,
    onChannelClick: (Int, List<M3UChannel>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    LaunchedEffect(channels) { viewModel.loadChannels(channels) }
    val loading by viewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.browser.state.collectAsStateWithLifecycle()
    val controller = viewModel.browser
    if (loading !is HomeUiState.Success) {
        Box(Modifier.fillMaxSize().background(BrowserColors.page), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.loading_channels), color = BrowserColors.text)
        }
        return
    }

    val viewKey = state.location.key
    val items = state.items
    val bookmark = state.bookmark
    val density = state.density
    val gridState = remember(viewKey, density, state.query) {
        LazyGridState(restoredIndex(items, bookmark.firstVisibleId, bookmark.firstVisibleIndex), bookmark.scrollOffset)
    }
    val sidebarRequesters = remember { List(4) { FocusRequester() } }
    val queryRequester = remember { FocusRequester() }
    val densityRequesters = remember { GridDensity.values().associateWith { FocusRequester() } }
    val itemRequesters = remember(viewKey) { mutableMapOf<String, FocusRequester>() }
    var command by remember(viewKey) { mutableStateOf<FocusCommand?>(null) }
    var pendingTarget by remember(viewKey) { mutableStateOf<String?>(null) }
    val sequence = remember { intArrayOf(0) }
    fun request(target: String) { pendingTarget = target; command = FocusCommand(target, ++sequence[0]) }
    fun focused(target: String) {
        if (pendingTarget != null && pendingTarget != target) command = null
        pendingTarget = null
    }
    fun move(target: String) {
        val visible = gridState.layoutInfo.visibleItemsInfo.find { it.key == target }
        val requester = when {
            target.startsWith("sidebar:") -> sidebarRequesters[target.substringAfter(':').toInt()]
            target == "query" -> queryRequester
            target.startsWith("density:") -> densityRequesters[GridDensity.valueOf(target.substringAfter(':'))]
            visible != null && visible.offset.y >= 0 && visible.offset.y + visible.size.height <= gridState.layoutInfo.viewportSize.height -> itemRequesters[target]
            else -> null
        }
        if (requester == null) request(target)
        else { command = null; pendingTarget = null; requester.requestFocus() }
    }
    fun requestGrid() {
        val current = controller.state.value
        if (current.items.isEmpty()) request("sidebar:${current.activeSidebar}")
        else request(current.items[restoredIndex(current.items, current.bookmark.focusedId, current.bookmark.focusedIndex)].id)
    }
    fun direction(key: KeyEvent, physicalTarget: String): Boolean {
        val horizontal = when (key.keyCode) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
        val vertical = when (key.keyCode) { KeyEvent.KEYCODE_DPAD_UP -> -1; KeyEvent.KEYCODE_DPAD_DOWN -> 1; else -> 0 }
        if (horizontal == 0 && vertical == 0) return false
        val target = pendingTarget ?: physicalTarget
        when {
            target.startsWith("sidebar:") -> {
                val index = target.substringAfter(':').toInt()
                if (vertical != 0) move("sidebar:${(index + vertical).coerceIn(0, 3)}")
                else if (horizontal > 0) when (state.location.destination) {
                    BrowserDestination.SETTINGS -> move("density:${density.name}")
                    BrowserDestination.SEARCH -> if (state.bookmark.focusedId == null || items.isEmpty()) move("query") else requestGrid()
                    else -> requestGrid()
                }
            }
            target == "query" -> {
                if (horizontal < 0) move("sidebar:2")
                else if (vertical > 0 && items.isNotEmpty()) requestGrid()
            }
            target.startsWith("density:") -> {
                val index = GridDensity.valueOf(target.substringAfter(':')).ordinal
                if (horizontal < 0) move("sidebar:3")
                else if (vertical != 0) move("density:${GridDensity.values()[(index + vertical).coerceIn(0, 2)].name}")
            }
            else -> {
                val index = items.indexOfFirst { it.id == target }
                if (index >= 0) when {
                    horizontal < 0 && index % density.columns == 0 -> move("sidebar:${state.activeSidebar}")
                    state.location.destination == BrowserDestination.SEARCH && vertical < 0 && index < density.columns -> move("query")
                    else -> gridTarget(index, items.size, density.columns, horizontal, vertical)?.let { move(items[it].id) }
                }
            }
        }
        return true
    }
    var editing by remember(viewKey) { mutableStateOf(false) }
    var menuTile by remember(viewKey) { mutableStateOf<BrowserTile?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val inputMode = LocalInputModeManager.current
    // TV focus must also work after a non-remote launch (for example, an activity start).
    LaunchedEffect(viewKey) { inputMode.requestInputMode(InputMode.Keyboard) }
    BackHandler(enabled = state.canGoBack) { controller.back() }
    BackHandler(enabled = editing) { editing = false; keyboard?.hide(); request("query") }

    LaunchedEffect(viewKey, density, items) {
        if (editing || menuTile != null) return@LaunchedEffect
        val saved = controller.state.value.bookmark
        when (saved.area) {
            BrowserFocus.SIDEBAR -> request("sidebar:${saved.sidebarIndex}")
            BrowserFocus.QUERY -> request("query")
            BrowserFocus.DENSITY -> request("density:${saved.focusedId ?: density.name}")
            BrowserFocus.GRID -> requestGrid()
        }
    }
    LaunchedEffect(command, viewKey, density) {
        val target = command?.target ?: return@LaunchedEffect
        when {
            target.startsWith("sidebar:") -> sidebarRequesters[target.substringAfter(':').toInt()].requestFocus()
            target == "query" -> queryRequester.requestFocus()
            target.startsWith("density:") -> densityRequesters[GridDensity.valueOf(target.substringAfter(':'))]?.requestFocus()
            else -> {
                val index = items.indexOfFirst { it.id == target }
                if (index < 0) return@LaunchedEffect
                val first = firstRowForTarget(index, gridState.firstVisibleItemIndex, density.columns, density.rows)
                if (first != gridState.firstVisibleItemIndex) gridState.scrollToItem(first)
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.key == target } }.first { it }
                withFrameNanos { }
                if (command?.target == target) itemRequesters[target]?.requestFocus()
            }
        }
    }
    LaunchedEffect(gridState, viewKey, items) {
        if (items.isEmpty()) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.totalItemsCount == items.size }.first { it }
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            if (controller.state.value.location.key == viewKey) controller.scroll(index, offset)
        }
    }
    LaunchedEffect(editing) {
        if (editing) { withFrameNanos { }; queryRequester.requestFocus(); keyboard?.show() }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BrowserColors.page)) {
        val scale = min(maxWidth.value / 1672f, maxHeight.value / 941f)
        val isSearch = state.location.destination == BrowserDestination.SEARCH
        val isSettings = state.location.destination == BrowserDestination.SETTINGS
        val title = when (state.location.destination) {
            BrowserDestination.CATEGORIES -> if (state.location.categoryId == null) R.string.categories else R.string.channels
            BrowserDestination.FAVORITES -> R.string.channels
            BrowserDestination.SEARCH -> R.string.search
            BrowserDestination.SETTINGS -> R.string.settings
        }
        Box(Modifier.size((1672 * scale).dp, (941 * scale).dp).align(Alignment.Center)
            .onGloballyPositioned { viewModel.onBrowseReady() }.testTag("browser")) {
            Box(Modifier.width((120 * scale).dp).fillMaxHeight().background(BrowserColors.sidebar)) {
                Box(Modifier.width(1.dp).fillMaxHeight().align(Alignment.CenterEnd).background(BrowserColors.border))
                BrowserDestination.values().forEachIndexed { index, destination ->
                    val label = stringResource(when (destination) {
                        BrowserDestination.CATEGORIES -> R.string.categories
                        BrowserDestination.FAVORITES -> R.string.favorites
                        BrowserDestination.SEARCH -> R.string.search
                        BrowserDestination.SETTINGS -> R.string.settings
                    })
                    val y = if (index == 3) 820 else 102 + index * 96
                    BrowserControl(
                        Modifier.offset((18 * scale).dp, (y * scale).dp).size((84 * scale).dp)
                            .focusRequester(sidebarRequesters[index]).testTag("sidebar_${destination.name}")
                            .semantics { contentDescription = label },
                        scale, active = state.activeSidebar == index, navigation = true,
                        onFocus = { focused("sidebar:$index"); controller.focusArea(BrowserFocus.SIDEBAR, index) },
                        onKey = { key -> direction(key, "sidebar:$index") },
                        onClick = { editing = false; keyboard?.hide(); controller.open(destination) }
                    ) { tint ->
                        if (state.activeSidebar == index && tint != BrowserColors.dark) {
                            Box(Modifier.width((4 * scale).dp).height((64 * scale).dp).align(Alignment.CenterStart).background(BrowserColors.text))
                        }
                        BrowserIcon(index, tint, Modifier.size((36 * scale).dp).align(Alignment.Center))
                    }
                }
            }
            Text(stringResource(title), color = BrowserColors.text, fontSize = (40 * scale).sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.offset((152 * scale).dp, (40 * scale).dp).testTag("browser_heading"))

            if (isSettings) {
                Column(Modifier.offset((152 * scale).dp, (120 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                    Text(stringResource(R.string.grid_density), color = BrowserColors.secondary, fontSize = (22 * scale).sp)
                    GridDensity.values().forEach { value ->
                        val label = stringResource(when (value) { GridDensity.LARGE -> R.string.density_large; GridDensity.STANDARD -> R.string.density_standard; GridDensity.COMPACT -> R.string.density_compact })
                        BrowserControl(Modifier.width((650 * scale).dp).height((74 * scale).dp)
                            .focusRequester(densityRequesters.getValue(value)).testTag("density_${value.name}"), scale,
                            active = density == value, onFocus = { focused("density:${value.name}"); controller.focusArea(BrowserFocus.DENSITY, id = value.name) },
                            onKey = { key -> direction(key, "density:${value.name}") }, onClick = { viewModel.setDensity(value) }
                        ) { tint -> Text("$label  ·  ${value.columns} × ${value.rows}", color = tint, fontSize = (24 * scale).sp,
                            modifier = Modifier.padding((18 * scale).dp).align(Alignment.CenterStart)) }
                    }
                }
            } else {
                if (isSearch) {
                    val fieldModifier = Modifier.offset((152 * scale).dp, (104 * scale).dp).width((1496 * scale).dp).height((64 * scale).dp)
                        .focusRequester(queryRequester)
                    if (editing) {
                        BasicTextField(state.query, viewModel::search, singleLine = true,
                            textStyle = TextStyle(color = BrowserColors.dark, fontSize = (22 * scale).sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { editing = false; keyboard?.hide(); requestGrid() }),
                            modifier = fieldModifier.background(BrowserColors.amber, RoundedCornerShape((8 * scale).dp))
                                .padding((18 * scale).dp).testTag("search_editor")
                                .onFocusChanged { if (it.isFocused) { focused("query"); controller.focusArea(BrowserFocus.QUERY) } })
                    } else {
                        BrowserControl(fieldModifier.testTag("search_input"), scale,
                            onFocus = { focused("query"); controller.focusArea(BrowserFocus.QUERY) },
                            onKey = { key -> direction(key, "query") }, onClick = { editing = true }
                        ) { tint -> Text(state.query.ifBlank { stringResource(R.string.search_hint) }, color = tint,
                            fontSize = (22 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = (18 * scale).dp).align(Alignment.CenterStart)) }
                    }
                }
                val gridTop = if (isSearch) 184 else 104
                val gridHeight = 920 - gridTop
                val gridModifier = Modifier.offset((152 * scale).dp, (gridTop * scale).dp)
                    .size((1496 * scale).dp, (gridHeight * scale).dp)
                if (items.isEmpty()) {
                    val message = when {
                        isSearch && state.query.isBlank() -> R.string.search_instruction
                        isSearch -> R.string.search_empty
                        state.location.destination == BrowserDestination.FAVORITES -> R.string.favorites_empty
                        else -> R.string.category_empty
                    }
                    Box(gridModifier, contentAlignment = Alignment.Center) {
                        Text(stringResource(message), color = BrowserColors.secondary, fontSize = (22 * scale).sp)
                    }
                } else {
                    val tileHeight = ((gridHeight - (density.rows - 1) * 12f) / density.rows * scale).dp
                    LazyVerticalGrid(columns = GridCells.Fixed(density.columns), state = gridState,
                        modifier = gridModifier.testTag("browser_grid"),
                        horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
                        verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
                    ) {
                        itemsIndexed(items, key = { _, tile -> tile.id }) { index, tile ->
                            val requester = itemRequesters.getOrPut(tile.id) { FocusRequester() }
                            BrowserControl(Modifier.fillMaxWidth().height(tileHeight).focusRequester(requester).testTag("tile_$index"), scale,
                                onFocus = { focused(tile.id); controller.focusGrid(tile.id) },
                                onKey = { key ->
                                    if (tile.entry != null && (key.keyCode == KeyEvent.KEYCODE_MENU ||
                                            (key.keyCode == KeyEvent.KEYCODE_F10 && key.isShiftPressed))) {
                                        if (key.repeatCount == 0) menuTile = tile
                                        true
                                    } else {
                                        direction(key, tile.id)
                                    }
                                }, onClick = {
                                    if (tile.category != null) controller.openCategory(tile.category.id)
                                    else {
                                        viewModel.preparePlayback(index)
                                        onChannelClick(index, items.mapNotNull { it.entry?.channel })
                                    }
                                }
                            ) { tint -> BrowserTileContent(tile, tint, scale) }
                        }
                    }
                }
            }
            menuTile?.let { tile ->
                fun closeMenu() { menuTile = null; requestGrid() }
                Dialog(onDismissRequest = { closeMenu() }) {
                    val actionRequester = remember(tile.id) { FocusRequester() }
                    LaunchedEffect(tile.id) { withFrameNanos { }; actionRequester.requestFocus() }
                    Column(Modifier.width((600 * scale).dp).background(BrowserColors.tile, RoundedCornerShape(8.dp)).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tile.entry?.channel?.name.orEmpty(), color = BrowserColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BrowserControl(Modifier.fillMaxWidth().height(56.dp).focusRequester(actionRequester).testTag("favorite_action"), 1f,
                            onClick = { viewModel.toggleFavorite(tile.id); closeMenu() }
                        ) { tint -> Text(stringResource(if (tile.id in state.favorites) R.string.remove_favorite else R.string.add_favorite),
                            color = tint, modifier = Modifier.align(Alignment.Center)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserControl(
    modifier: Modifier, scale: Float, active: Boolean = false, navigation: Boolean = false,
    onFocus: () -> Unit = {}, onKey: (KeyEvent) -> Boolean = { false }, onClick: () -> Unit,
    content: @Composable BoxScope.(Color) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((8 * scale).dp)
    Box(modifier.clip(shape).background(if (focused) BrowserColors.amber else if (active) Color(0xFF252A2B) else if (navigation) Color.Transparent else BrowserColors.tile)
        .border(BorderStroke((1 * scale).dp, if (focused) BrowserColors.amber else if (navigation) Color.Transparent else BrowserColors.border), shape)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        .onPreviewKeyEvent { if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) onKey(it.nativeKeyEvent) else false }
        .semantics(mergeDescendants = true) { selected = active }
        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
    ) { content(if (focused) BrowserColors.dark else BrowserColors.text) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BoxScope.BrowserTileContent(tile: BrowserTile, tint: Color, scale: Float) {
    Column(Modifier.fillMaxSize().padding(horizontal = (18 * scale).dp, vertical = (18 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (tile.entry != null) ChannelLogo(tile.entry.channel.logoUrl, Modifier.fillMaxWidth().height((84 * scale).dp))
            else BrowserIcon(4, tint, Modifier.size((58 * scale).dp))
        }
        val category = tile.category
        if (category != null) {
            Text(category.name, color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.channel_count, category.channels.size, category.channels.size), color = tint, fontSize = (18 * scale).sp,
                maxLines = 1, modifier = Modifier.padding(top = (6 * scale).dp))
        } else tile.entry?.channel?.let { channel ->
            val label = channel.providerNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it   ${channel.name}" } ?: channel.name
            Text(label, color = tint, fontSize = (18 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = (12 * scale).dp))
        }
    }
}
