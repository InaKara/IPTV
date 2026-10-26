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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
import com.example.iptv.ui.login.StartupBranding
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.ui.browser.*
import com.example.iptv.ui.library.*
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
    viewModel: HomeViewModel = hiltViewModel(),
    onBrowseReady: () -> Unit = {},
    accountSettings: (@Composable (Modifier, FocusRequester, () -> Unit) -> Unit)? = null,
    library: LibraryViewModel? = null
) {
    val focusRestoration = rememberFocusRestoration()
    LaunchedEffect(channels) { viewModel.loadChannels(channels) }
    val loading by viewModel.uiState.collectAsStateWithLifecycle()
    val verbose by viewModel.verboseLogging.collectAsStateWithLifecycle()
    val state by viewModel.browser.state.collectAsStateWithLifecycle()
    val controller = viewModel.browser
    val libraryPage = library?.state?.collectAsStateWithLifecycle()?.value
    val useLibrary = library != null && state.location.destination == BrowserDestination.VOD && libraryPage?.location?.route != LibraryRoute.PLAYLIST
    val libraryRequester = remember { FocusRequester() }
    if (loading !is HomeUiState.Success) {
        StartupBranding(R.string.loading_channels)
        return
    }
    LaunchedEffect(state.location, state.filterAdult) {
        if (state.location.destination != BrowserDestination.SEARCH) viewModel.cancelSearch()
        if (state.location.destination != BrowserDestination.VOD) library?.deactivate()
        viewModel.awaitBrowseReady()
        if (state.location.destination == BrowserDestination.VOD) library?.activate(state.filterAdult)
        else viewModel.syncLibrary()
    }

    val viewKey = state.location.key
    val items = state.items
    val bookmark = state.bookmark
    val density = state.density
    val gridState = remember(viewKey, density, state.query, items) {
        LazyGridState(restoredIndex(items, bookmark.firstVisibleId, bookmark.firstVisibleIndex), bookmark.scrollOffset)
    }
    val sidebarRequesters = remember { List(BrowserDestination.values().size) { FocusRequester() } }
    val queryRequester = remember { FocusRequester() }
    val searchRequester = remember { FocusRequester() }
    val accountRequester = remember { FocusRequester() }
    val filterRequester = remember { FocusRequester() }
    val loggingRequester = remember { FocusRequester() }
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
            target == "search_button" -> searchRequester
            target == "account" -> accountRequester
            target == "adult_filter" -> filterRequester
            target == "logging" -> loggingRequester
            target.startsWith("density:") -> densityRequesters[GridDensity.valueOf(target.substringAfter(':'))]
            visible != null && visible.offset.y >= 0 && visible.offset.y + visible.size.height <= gridState.layoutInfo.viewportSize.height -> itemRequesters[target]
            else -> null
        }
        if (requester == null || !focusRestoration.request(requester)) request(target)
        else { command = null; pendingTarget = null }
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
                if (vertical != 0) move("sidebar:${(index + vertical).coerceIn(0, sidebarRequesters.lastIndex)}")
                else if (horizontal > 0) when (state.location.destination) {
                    BrowserDestination.SETTINGS -> move("density:${density.name}")
                    BrowserDestination.SEARCH -> if (state.bookmark.focusedId == null || items.isEmpty()) move("query") else requestGrid()
                    else -> if (useLibrary) {
                        if (!focusRestoration.request(libraryRequester)) request("library")
                    } else requestGrid()
                }
            }
            target == "query" -> {
                if (horizontal < 0) move("sidebar:${BrowserDestination.SEARCH.ordinal}")
                else if (horizontal > 0) move("search_button")
                else if (vertical > 0 && items.isNotEmpty()) requestGrid()
            }
            target == "search_button" -> {
                if (horizontal < 0) move("query")
                else if (vertical > 0 && items.isNotEmpty()) requestGrid()
            }
            target.startsWith("density:") -> {
                val index = GridDensity.valueOf(target.substringAfter(':')).ordinal
                if (horizontal < 0) move("sidebar:${BrowserDestination.SETTINGS.ordinal}")
                else if (horizontal > 0 && accountSettings != null) move("account")
                else if (vertical > 0 && index == GridDensity.COMPACT.ordinal) move("adult_filter")
                else if (vertical != 0) move("density:${GridDensity.values()[(index + vertical).coerceIn(0, 2)].name}")
            }
            target == "adult_filter" -> {
                if (horizontal < 0) move("sidebar:${BrowserDestination.SETTINGS.ordinal}")
                else if (horizontal > 0 && accountSettings != null) move("account")
                else if (vertical < 0) move("density:${GridDensity.COMPACT.name}")
                else if (vertical > 0 && com.example.iptv.BuildConfig.DEBUG) move("logging")
            }
            target == "logging" -> {
                if (horizontal < 0) move("sidebar:${BrowserDestination.SETTINGS.ordinal}")
                else if (horizontal > 0 && accountSettings != null) move("account")
                else if (vertical < 0) move("adult_filter")
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
    BackHandler(enabled = library != null && state.location.destination == BrowserDestination.VOD && !useLibrary && state.location.categoryId == null) { library?.back() }
    BackHandler(enabled = editing) { viewModel.cancelSearchDraft(); editing = false; keyboard?.hide(); request("query") }

    LaunchedEffect(viewKey, density, items, focusRestoration.version) {
        if (useLibrary) return@LaunchedEffect
        if (editing || menuTile != null) return@LaunchedEffect
        val saved = controller.state.value.bookmark
        when (saved.area) {
            BrowserFocus.SIDEBAR -> request("sidebar:${saved.sidebarIndex}")
            BrowserFocus.QUERY -> request("query")
            BrowserFocus.SEARCH_BUTTON -> request("search_button")
            BrowserFocus.DENSITY -> request("density:${saved.focusedId ?: density.name}")
            BrowserFocus.ACCOUNT -> request("account")
            BrowserFocus.ADULT_FILTER -> request("adult_filter")
            BrowserFocus.LOGGING -> request("logging")
            BrowserFocus.GRID -> requestGrid()
        }
    }
    LaunchedEffect(command, viewKey, density, focusRestoration.version) {
        val requested = command ?: return@LaunchedEffect
        val target = requested.target
        val requester = when {
            target.startsWith("sidebar:") -> sidebarRequesters.getOrNull(target.substringAfter(':').toInt())
            target == "query" -> queryRequester
            target == "search_button" -> searchRequester
            target == "account" -> accountRequester
            target == "adult_filter" -> filterRequester
            target == "logging" -> loggingRequester
            target == "library" -> libraryRequester
            target.startsWith("density:") -> densityRequesters[GridDensity.valueOf(target.substringAfter(':'))]
            else -> {
                val index = items.indexOfFirst { it.id == target }
                if (index < 0) return@LaunchedEffect
                val first = firstRowForTarget(index, gridState.firstVisibleItemIndex, density.columns, density.rows)
                if (first != gridState.firstVisibleItemIndex) gridState.scrollToItem(first)
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.key == target } }.first { it }
                itemRequesters[target]
            }
        }
        requester?.let {
            focusRestoration.restore(it) { command == requested && controller.state.value.location.key == viewKey && !editing && menuTile == null }
        }
    }
    LaunchedEffect(gridState, viewKey, items) {
        if (items.isEmpty()) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.totalItemsCount == items.size }.first { it }
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            if (controller.state.value.location.key == viewKey) controller.scroll(index, offset)
        }
    }
    LaunchedEffect(editing, focusRestoration.version) {
        if (editing) focusRestoration.restore(queryRequester, onFocused = { keyboard?.show() }) { editing }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BrowserColors.page)) {
        val scale = min(maxWidth.value / 1672f, maxHeight.value / 941f)
        val isSearch = state.location.destination == BrowserDestination.SEARCH
        val isSettings = state.location.destination == BrowserDestination.SETTINGS
        val title = if (useLibrary) libraryHeading(libraryPage!!) else when (state.location.destination) {
            BrowserDestination.CATEGORIES, BrowserDestination.VOD -> if (state.location.categoryId == null) {
                stringResource(if (state.location.destination == BrowserDestination.VOD) R.string.vod else R.string.live_tv)
            } else state.categories.find { it.id == state.location.categoryId }?.name?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.channels)
            BrowserDestination.FAVORITES -> stringResource(R.string.favorites)
            BrowserDestination.SEARCH -> stringResource(R.string.search)
            BrowserDestination.SETTINGS -> stringResource(R.string.settings)
        }
        Box(Modifier.size((1672 * scale).dp, (941 * scale).dp).align(Alignment.Center)
            .onGloballyPositioned { viewModel.onBrowseReady(); onBrowseReady() }.testTag("browser")) {
            Box(Modifier.width((120 * scale).dp).fillMaxHeight().background(BrowserColors.sidebar)) {
                Box(Modifier.width(1.dp).fillMaxHeight().align(Alignment.CenterEnd).background(BrowserColors.border))
                BrowserDestination.values().forEachIndexed { index, destination ->
                    val label = stringResource(when (destination) {
                        BrowserDestination.CATEGORIES -> R.string.live_tv
                        BrowserDestination.VOD -> R.string.vod
                        BrowserDestination.FAVORITES -> R.string.favorites
                        BrowserDestination.SEARCH -> R.string.search
                        BrowserDestination.SETTINGS -> R.string.settings
                    })
                    val y = if (destination == BrowserDestination.SETTINGS) 820 else 102 + index * 96
                    BrowserControl(
                        Modifier.offset((18 * scale).dp, (y * scale).dp).size((84 * scale).dp)
                            .attachedFocusRequester(sidebarRequesters[index], focusRestoration).testTag("sidebar_${destination.name}")
                            .semantics { contentDescription = label },
                        scale, active = state.activeSidebar == index, navigation = true,
                        onFocus = { focused("sidebar:$index"); controller.focusArea(BrowserFocus.SIDEBAR, index) },
                        onKey = { key -> direction(key, "sidebar:$index") },
                        onClick = {
                            viewModel.cancelSearchDraft(); editing = false; keyboard?.hide()
                            if (destination == BrowserDestination.VOD) library?.root()
                            controller.open(destination)
                        }
                    ) { tint ->
                        if (state.activeSidebar == index && tint != BrowserColors.dark) {
                            Box(Modifier.width((4 * scale).dp).height((64 * scale).dp).align(Alignment.CenterStart).background(BrowserColors.text))
                        }
                        val icon = when (destination) {
                            BrowserDestination.CATEGORIES -> 0
                            BrowserDestination.VOD -> 5
                            BrowserDestination.FAVORITES -> 1
                            BrowserDestination.SEARCH -> 2
                            BrowserDestination.SETTINGS -> 3
                        }
                        BrowserIcon(icon, tint, Modifier.size((36 * scale).dp).align(Alignment.Center))
                    }
                }
            }
            Text(title, color = BrowserColors.text, fontSize = (40 * scale).sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset((152 * scale).dp, (40 * scale).dp).width((1496 * scale).dp).testTag("browser_heading"))

            if (useLibrary) {
                LibraryPane(library!!, scale, density,
                    Modifier.offset((152 * scale).dp, (104 * scale).dp).size((1496 * scale).dp, (816 * scale).dp),
                    libraryRequester, { move("sidebar:${BrowserDestination.VOD.ordinal}") },
                    onPlay = { index, entries -> viewModel.prepareLibraryPlayback(entries, library.playbackKey()); onChannelClick(index, entries) },
                    externalBack = { controller.closeLibraryDetails() }, focusRestoration = focusRestoration)
            } else if (isSettings) {
                Row(Modifier.offset((152 * scale).dp, (120 * scale).dp).size((1496 * scale).dp, (800 * scale).dp),
                    horizontalArrangement = Arrangement.spacedBy((32 * scale).dp)) {
                    Column(Modifier.width(((if (accountSettings == null) 650 else 420) * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                        Text(stringResource(R.string.grid_density), color = BrowserColors.secondary, fontSize = (22 * scale).sp)
                        GridDensity.values().forEach { value ->
                            val label = stringResource(when (value) { GridDensity.LARGE -> R.string.density_large; GridDensity.STANDARD -> R.string.density_standard; GridDensity.COMPACT -> R.string.density_compact })
                            BrowserControl(Modifier.fillMaxWidth().height((74 * scale).dp)
                                .attachedFocusRequester(densityRequesters.getValue(value), focusRestoration).testTag("density_${value.name}"), scale,
                                active = density == value, onFocus = { focused("density:${value.name}"); controller.focusArea(BrowserFocus.DENSITY, id = value.name) },
                                onKey = { key -> direction(key, "density:${value.name}") }, onClick = { viewModel.setDensity(value) }
                            ) { tint -> Text("$label  ·  ${value.columns} × ${value.rows}", color = tint, fontSize = (24 * scale).sp,
                                modifier = Modifier.padding((18 * scale).dp).align(Alignment.CenterStart)) }
                        }
                        val filterStatus = stringResource(if (state.filterAdult) R.string.filtering_active else R.string.filtering_inactive)
                        BrowserControl(Modifier.fillMaxWidth().height((116 * scale).dp)
                            .attachedFocusRequester(filterRequester, focusRestoration).testTag("adult_filter")
                            .semantics {
                                toggleableState = if (state.filterAdult) ToggleableState.On else ToggleableState.Off
                                stateDescription = filterStatus
                            }, scale, role = Role.Switch,
                            onFocus = { focused("adult_filter"); controller.focusArea(BrowserFocus.ADULT_FILTER) },
                            onKey = { direction(it, "adult_filter") }, onClick = viewModel::toggleAdultFilter
                        ) { tint ->
                            Column(Modifier.padding((18 * scale).dp).align(Alignment.CenterStart)) {
                                Text(stringResource(R.string.filter_adult_content), color = tint, fontSize = (24 * scale).sp)
                                Text(filterStatus, color = tint, fontSize = (20 * scale).sp)
                            }
                        }
                        if (com.example.iptv.BuildConfig.DEBUG) {
                            val status = stringResource(if (verbose) R.string.logging_on else R.string.logging_off)
                            BrowserControl(Modifier.fillMaxWidth().height((100 * scale).dp).attachedFocusRequester(loggingRequester, focusRestoration).testTag("verbose_logging")
                                .semantics { toggleableState = if (verbose) ToggleableState.On else ToggleableState.Off; stateDescription = status },
                                scale, role = Role.Switch,
                                onFocus = { focused("logging"); controller.focusArea(BrowserFocus.LOGGING) },
                                onKey = { direction(it, "logging") }, onClick = { viewModel.setVerboseLogging(!verbose) }) { tint ->
                                Column(Modifier.padding((18 * scale).dp).align(Alignment.CenterStart)) {
                                    Text(stringResource(R.string.verbose_logging), color = tint, fontSize = (24 * scale).sp)
                                    Text(status, color = tint, fontSize = (20 * scale).sp)
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth().testTag("software_info"), verticalArrangement = Arrangement.spacedBy((6 * scale).dp)) {
                            Text(stringResource(R.string.software_information), color = BrowserColors.secondary, fontSize = (22 * scale).sp)
                            Text(stringResource(R.string.software_version, com.example.iptv.BuildConfig.VERSION_NAME,
                                com.example.iptv.BuildConfig.VERSION_CODE), color = BrowserColors.text, fontSize = (20 * scale).sp,
                                modifier = Modifier.testTag("software_version"))
                            Text(stringResource(R.string.software_built, stringResource(R.string.software_build_timestamp)),
                                color = BrowserColors.text, fontSize = (20 * scale).sp, modifier = Modifier.testTag("software_built"))
                        }
                    }
                    accountSettings?.invoke(Modifier.weight(1f).fillMaxHeight().focusAnchor(accountRequester, focusRestoration).onFocusChanged {
                        if (it.hasFocus) { focused("account"); controller.focusArea(BrowserFocus.ACCOUNT) }
                    }, accountRequester, { move("density:${density.name}") })
                }
            } else {
                if (isSearch) {
                    fun submit() {
                        editing = false; keyboard?.hide(); viewModel.submitSearch()
                        controller.focusArea(BrowserFocus.SEARCH_BUTTON); request("search_button")
                    }
                    val fieldModifier = Modifier.offset((152 * scale).dp, (104 * scale).dp).width((1280 * scale).dp).height((64 * scale).dp)
                        .attachedFocusRequester(queryRequester, focusRestoration)
                    if (editing) {
                        BasicTextField(state.draftQuery, viewModel::editSearch, singleLine = true,
                            textStyle = TextStyle(color = BrowserColors.dark, fontSize = (22 * scale).sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }),
                            modifier = fieldModifier.background(BrowserColors.amber, RoundedCornerShape((8 * scale).dp))
                                .padding((18 * scale).dp).testTag("search_editor")
                                .onPreviewKeyEvent {
                                    if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && it.nativeKeyEvent.keyCode in
                                        setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) { submit(); true } else false
                                }
                                .onFocusChanged { if (it.isFocused) { focused("query"); controller.focusArea(BrowserFocus.QUERY) } })
                    } else {
                        BrowserControl(fieldModifier.testTag("search_input"), scale,
                            onFocus = { focused("query"); controller.focusArea(BrowserFocus.QUERY) },
                            onKey = { key -> direction(key, "query") }, onClick = { editing = true }
                        ) { tint -> Text(state.draftQuery.ifBlank { stringResource(R.string.search_hint) }, color = tint,
                            fontSize = (22 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = (18 * scale).dp).align(Alignment.CenterStart)) }
                    }
                    BrowserControl(Modifier.offset((1448 * scale).dp, (104 * scale).dp).size((200 * scale).dp, (64 * scale).dp)
                        .attachedFocusRequester(searchRequester, focusRestoration).testTag("search_submit"), scale,
                        onFocus = { focused("search_button"); controller.focusArea(BrowserFocus.SEARCH_BUTTON) },
                        onKey = { direction(it, "search_button") }, onClick = { submit() }) { tint ->
                        Text(stringResource(R.string.search), color = tint, fontSize = (22 * scale).sp, modifier = Modifier.align(Alignment.Center))
                    }
                }
                val gridTop = if (isSearch) 184 else 104
                val gridHeight = 920 - gridTop
                val gridModifier = Modifier.offset((152 * scale).dp, (gridTop * scale).dp)
                    .size((1496 * scale).dp, (gridHeight * scale).dp)
                if (items.isEmpty()) {
                    val message = when {
                        isSearch && state.searching -> R.string.searching
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
                            BrowserControl(Modifier.fillMaxWidth().height(tileHeight).attachedFocusRequester(requester, focusRestoration).testTag("tile_$index"), scale,
                                onFocus = { focused(tile.id); controller.focusGrid(tile.id) },
                                onKey = { key ->
                                    if (tile.entry != null && (isSearch || tile.entry.library?.kind != com.example.iptv.data.library.LibraryKind.SERIES) && (key.keyCode == KeyEvent.KEYCODE_MENU ||
                                            (key.keyCode == KeyEvent.KEYCODE_F10 && key.isShiftPressed))) {
                                        if (key.repeatCount == 0) menuTile = tile
                                        true
                                    } else {
                                        direction(key, tile.id)
                                    }
                                }, onClick = {
                                    if (tile.category != null) controller.openCategory(tile.category.id)
                                    else {
                                        if (controller.state.value.items.none { it.id == tile.id }) return@BrowserControl
                                        controller.focusGrid(tile.id)
                                        val season = tile.entry?.season
                                        if (season != null && library != null) {
                                            controller.openLibraryDetails(); library.externalSeason(season, state.filterAdult); return@BrowserControl
                                        }
                                        val metadata = tile.entry?.library
                                        if (metadata != null && library != null) {
                                            controller.openLibraryDetails()
                                            library.external(metadata, state.filterAdult, fromSearch = isSearch)
                                            return@BrowserControl
                                        }
                                        // Re-read visibility so an obsolete tile cannot launch a filtered entry.
                                        val visible = controller.state.value.items
                                        val playable = visible.filter { it.entry != null && it.entry.season == null && it.entry.library?.kind != com.example.iptv.data.library.LibraryKind.SERIES }
                                        val selected = playable.indexOfFirst { it.id == tile.id }
                                        if (selected >= 0) {
                                            val collection = viewModel.preparePlayback(selected, playable)
                                            onChannelClick(selected, collection)
                                        }
                                    }
                                }
                            ) { tint -> BrowserTileContent(tile, tint, scale, isSearch) }
                        }
                    }
                }
            }
            menuTile?.let { tile ->
                fun closeMenu() { menuTile = null; requestGrid() }
                Dialog(onDismissRequest = { closeMenu() }) {
                    val menuFocus = rememberFocusRestoration()
                    val actionRequester = remember(tile.id) { FocusRequester() }
                    val seriesResult = tile.entry?.library?.kind == com.example.iptv.data.library.LibraryKind.SERIES
                    var watched by remember(tile.id) { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(tile.id) { tile.entry?.takeIf { it.channel.isVod && it.season == null }?.let { watched = viewModel.watched(it) } }
                    LaunchedEffect(tile.id, menuFocus.version) { menuFocus.restore(actionRequester) { menuTile?.id == tile.id } }
                    Column(Modifier.width((600 * scale).dp).background(BrowserColors.tile, RoundedCornerShape(8.dp)).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tile.entry?.channel?.name.orEmpty(), color = BrowserColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        var locationUnavailable by remember(tile.id) { mutableStateOf(false) }
                        if (isSearch) {
                            BrowserControl(Modifier.fillMaxWidth().height(56.dp)
                                .then(if (seriesResult) Modifier.attachedFocusRequester(actionRequester, menuFocus) else Modifier).testTag("browse_location"), 1f,
                                onClick = {
                                    val entry = tile.entry ?: return@BrowserControl
                                    if (entry.library != null && library != null) {
                                        val origin = controller.navigationRevision
                                        library.browseLocation(entry.library, state.filterAdult) { opened ->
                                            if (opened && controller.navigationRevision == origin) { menuTile = null; controller.openLibraryDetails() }
                                            else locationUnavailable = true
                                        }
                                    } else if (controller.browseLocation(entry)) {
                                        if (entry.channel.isVod) library?.fallback()
                                        menuTile = null
                                    } else locationUnavailable = true
                                }) { tint -> Text(stringResource(R.string.browse_location), color = tint, modifier = Modifier.align(Alignment.Center)) }
                            if (locationUnavailable) Text(stringResource(R.string.location_unavailable), color = BrowserColors.secondary)
                        }
                        if (tile.entry?.library?.kind != com.example.iptv.data.library.LibraryKind.SERIES) {
                        BrowserControl(Modifier.fillMaxWidth().height(56.dp).attachedFocusRequester(actionRequester, menuFocus).testTag("favorite_action"), 1f,
                            onClick = { viewModel.toggleFavorite(tile.id); closeMenu() }
                        ) { tint -> Text(stringResource(if (tile.id in state.favorites) R.string.remove_favorite else R.string.add_favorite),
                            color = tint, modifier = Modifier.align(Alignment.Center)) }
                        }
                        if (library != null && watched != null && tile.entry?.channel?.isVod == true && tile.entry.library?.kind != com.example.iptv.data.library.LibraryKind.SERIES) {
                            BrowserControl(Modifier.fillMaxWidth().height(56.dp), 1f,
                                onClick = { viewModel.markWatched(tile.entry, watched != true, ::closeMenu) }
                            ) { tint -> Text(stringResource(if (watched == true) R.string.library_reset else R.string.library_mark), color = tint, modifier = Modifier.align(Alignment.Center)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowserControl(
    modifier: Modifier, scale: Float, active: Boolean = false, navigation: Boolean = false,
    role: Role = Role.Button,
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
        .clickable(remember { MutableInteractionSource() }, indication = null, role = role, onClick = onClick)
    ) { content(if (focused) BrowserColors.dark else BrowserColors.text) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BoxScope.BrowserTileContent(tile: BrowserTile, tint: Color, scale: Float, showLocation: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(horizontal = (18 * scale).dp, vertical = (18 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (tile.entry != null) ChannelLogo(tile.entry.channel.logoUrl, Modifier.fillMaxSize().testTag("channel_logo_${tile.id}"))
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
            if (showLocation) {
                val metadata = tile.entry.library
                val context = if (metadata?.kind == com.example.iptv.data.library.LibraryKind.EPISODE && metadata.season.isNotBlank())
                    listOf(channel.groupTitle, stringResource(R.string.library_season, metadata.season)).filter { it.isNotBlank() }.joinToString(" · ")
                    else channel.groupTitle
                Text(context.ifBlank { stringResource(R.string.location_unavailable) }, color = tint, fontSize = (15 * scale).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("result_location_${tile.id}"))
            }
        }
    }
}
