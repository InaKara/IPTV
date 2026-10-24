package com.example.iptv.ui.library

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.iptv.R
import com.example.iptv.data.library.*
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.ui.browser.GridDensity
import com.example.iptv.ui.home.BrowserColors
import com.example.iptv.ui.home.BrowserControl
import com.example.iptv.ui.home.ChannelLogo
import kotlinx.coroutines.flow.first

@Composable
internal fun libraryHeading(page: LibraryPage): String {
    if (page.location.route == LibraryRoute.EPISODES) return stringResource(R.string.library_season, page.location.season.orEmpty())
    if (page.location.route == LibraryRoute.SEASONS) return page.location.label + " · " + stringResource(R.string.library_seasons)
    return page.location.label.ifBlank {
        stringResource(when (page.location.route) {
            LibraryRoute.ROOT, LibraryRoute.PLAYLIST -> R.string.vod
            LibraryRoute.CATEGORIES, LibraryRoute.TITLES -> if (page.location.kind == LibraryKind.SERIES) R.string.library_series else R.string.library_movies
            LibraryRoute.CONTINUE -> R.string.library_continue
            LibraryRoute.RECENT -> R.string.recently_viewed
            LibraryRoute.SEASONS -> R.string.library_seasons
            else -> R.string.vod
        })
    }
}

internal fun resumeTime(positionMs: Long): String {
    val minutes = positionMs.coerceAtLeast(0) / 60_000
    return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
}

private fun actionSymbol(id: String, watched: Boolean) = when (id) {
    "play" -> LibrarySymbol.PLAY
    "watch" -> if (watched) LibrarySymbol.WATCHED else LibrarySymbol.UNWATCHED
    "sort" -> LibrarySymbol.SORT
    "unwatched" -> LibrarySymbol.FILTER
    "retry" -> LibrarySymbol.RETRY
    "previous" -> LibrarySymbol.PREVIOUS
    "next" -> LibrarySymbol.NEXT
    "next_episode" -> LibrarySymbol.NEXT_EPISODE
    else -> LibrarySymbol.SERIES
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryLoading(page: LibraryPage, scale: Float, modifier: Modifier, updating: Boolean) {
    val message = when (page.location.route) {
        LibraryRoute.CATEGORIES, LibraryRoute.TITLES -> if (page.location.kind == LibraryKind.MOVIE) {
            if (updating) R.string.updating_movies else R.string.loading_movies
        } else if (updating) R.string.updating_series else R.string.loading_series
        LibraryRoute.SEASONS, LibraryRoute.EPISODES -> if (updating) R.string.updating_episodes else R.string.loading_episodes
        LibraryRoute.DETAIL -> if (updating) R.string.updating_details else R.string.loading_details
        else -> R.string.library_loading
    }
    Row(modifier.testTag("library_loading").padding((16 * scale).dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size((36 * scale).dp), color = BrowserColors.amber,
            strokeWidth = (3 * scale).dp)
        Spacer(Modifier.width((16 * scale).dp))
        Text(stringResource(message), color = BrowserColors.text, fontSize = (25 * scale).sp,
            modifier = Modifier.testTag("library_loading_status"))
    }
}

/** Uses the existing sidebar shell. Disk pages bound the grid's retained metadata. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LibraryPane(viewModel: LibraryViewModel, scale: Float, density: GridDensity,
    modifier: Modifier, entryRequester: FocusRequester, leave: () -> Unit,
    onPlay: (Int, List<M3UChannel>) -> Unit, externalBack: () -> Unit) {
    val page by viewModel.state.collectAsStateWithLifecycle()
    val playChoice by viewModel.playChoice.collectAsStateWithLifecycle()
    val route = page.location.route
    val key = page.location.key + ":${page.page}"
    val grid = remember(key, density) { LazyGridState(page.bookmark.first, page.bookmark.offset) }
    val requesters = remember(key) { mutableMapOf<String, FocusRequester>() }
    var contextRow by remember(key) { mutableStateOf<LibraryRow?>(null) }
    var menuReturn by remember(key) { mutableStateOf<String?>(null) }
    var previousRows by remember(key) { mutableStateOf(emptySet<String>()) }
    var choiceWasOpen by remember(key) { mutableStateOf(false) }
    LaunchedEffect(playChoice) {
        if (playChoice != null) choiceWasOpen = true
        else if (choiceWasOpen) { choiceWasOpen = false; menuReturn = "play" }
    }
    BackHandler(enabled = route != LibraryRoute.ROOT) {
        if (viewModel.consumeExternalReturn()) externalBack() else viewModel.back()
    }
    BackHandler(enabled = playChoice != null) { viewModel.cancelPlayChoice() }
    LaunchedEffect(grid, key) {
        snapshotFlow { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }.collect { (index, offset) -> viewModel.scroll(index, offset) }
    }
    LaunchedEffect(menuReturn, key) {
        val target = menuReturn ?: return@LaunchedEffect
        withFrameNanos { }
        val visibleTarget = page.rows.any { it.key == target } || (route == LibraryRoute.DETAIL &&
            target in setOf("play", "watch", "favorite", "seasons", "next_episode"))
        (if (visibleTarget) requesters[target] ?: entryRequester else entryRequester).requestFocus()
        menuReturn = null
    }
    LaunchedEffect(key) {
        withFrameNanos { }
        (requesters[page.bookmark.focus] ?: entryRequester).requestFocus()
    }
    LaunchedEffect(key, page.rows, page.failed, page.loading, page.preparing) {
        if (route in setOf(LibraryRoute.CATEGORIES, LibraryRoute.SEASONS, LibraryRoute.CONTINUE, LibraryRoute.RECENT) &&
            page.rows.isEmpty() && !page.failed && !page.loading && !page.preparing) leave()
    }
    // Restore a surviving item after paging, sorting or filtering; explicit sidebar focus stays there.
    LaunchedEffect(key, page.rows) {
        val target = page.bookmark.focus?.takeIf { id -> page.rows.any { it.key == id } }
        if (target != null) {
            val index = page.rows.indexOfFirst { it.key == target }
            snapshotFlow { grid.layoutInfo.totalItemsCount == page.rows.size }.first { it }
            if (grid.layoutInfo.visibleItemsInfo.none { it.key == target }) grid.scrollToItem((index / density.columns) * density.columns)
            snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == target } }.first { it }
            withFrameNanos { }; requesters[target]?.requestFocus()
        } else if (page.bookmark.focus in previousRows || (previousRows.isEmpty() && page.rows.isNotEmpty())) {
            entryRequester.requestFocus()
        }
        previousRows = page.rows.mapTo(mutableSetOf()) { it.key }
    }
    Column(modifier.focusRequester(entryRequester).focusGroup().onPreviewKeyEvent {
        if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // Compose handles left between controls; only the first column returns to the sidebar.
            val focused = page.bookmark.focus
            val index = page.rows.indexOfFirst { row -> row.key == focused }
            val rootIndex = focused?.takeIf { it.startsWith("root_") }?.substringAfter('_')?.toIntOrNull()
            val firstControl = focused in setOf("play", "seasons", "next_episode", "retry", "sort", "previous") ||
                (focused == "unwatched" && route == LibraryRoute.EPISODES) || (focused == "next" && page.page == 0)
            if ((rootIndex != null && rootIndex % density.columns == 0) || (index >= 0 && index % density.columns == 0) ||
                (index < 0 && firstControl)) { leave(); true } else false
        } else false
    }, verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
        @Composable fun action(label: String, id: String, click: () -> Unit) {
            val requester = requesters.getOrPut(id) { FocusRequester() }
            val watchedAction = if (id == "watch") stringResource(if (page.progress.watched) R.string.library_reset else R.string.library_mark) else null
            BrowserControl(Modifier.height((60 * scale).dp).widthIn(min = (150 * scale).dp)
                .focusRequester(requester).testTag("library_$id")
                .semantics { if (watchedAction != null) { contentDescription = watchedAction; stateDescription = label } }, scale,
                onFocus = { viewModel.focus(id) }, onClick = click) { tint ->
                Row(Modifier.padding(horizontal = (16 * scale).dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
                    LibraryIcon(actionSymbol(id, page.progress.watched), tint, Modifier.size((26 * scale).dp))
                    Text(label, color = tint, fontSize = (21 * scale).sp, maxLines = 1)
                }
            }
        }
        @Composable fun heart(season: Boolean = false) {
            val label = stringResource(if (season) {
                if (page.favorite) R.string.remove_season_favorite else R.string.add_season_favorite
            } else if (page.favorite) R.string.remove_favorite else R.string.add_favorite)
            val status = stringResource(if (page.favorite) R.string.is_favorite else R.string.not_favorite)
            val requester = requesters.getOrPut("favorite") { FocusRequester() }
            BrowserControl(Modifier.size((60 * scale).dp).focusRequester(requester).testTag("library_favorite")
                .semantics { contentDescription = label; stateDescription = status; toggleableState = if (page.favorite) ToggleableState.On else ToggleableState.Off },
                scale, onFocus = { viewModel.focus("favorite") }, onClick = viewModel::toggleFavorite) { tint ->
                LibraryIcon(LibrarySymbol.HEART, tint, Modifier.size((28 * scale).dp).align(Alignment.Center), page.favorite)
            }
        }
        if (route == LibraryRoute.ROOT) {
            val cards = listOf(R.string.library_movies, R.string.library_series, R.string.library_continue,
                R.string.recently_viewed, R.string.library_playlist)
            LazyVerticalGrid(GridCells.Fixed(density.columns), state = grid, modifier = Modifier.fillMaxSize().testTag("library_root_grid"),
                horizontalArrangement = Arrangement.spacedBy((12 * scale).dp), verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
              itemsIndexed(cards) { index, label ->
                val id = "root_$index"
                val requester = requesters.getOrPut(id) { FocusRequester() }
                BrowserControl(Modifier.fillMaxWidth().height(((816f - (density.rows - 1) * 12f) / density.rows * scale).dp)
                    .focusRequester(requester).testTag("library_$id"), scale,
                    onFocus = { viewModel.focus(id) }, onClick = {
                    when (index) {
                        0 -> viewModel.openRoot(LibraryRoute.CATEGORIES)
                        1 -> viewModel.openRoot(LibraryRoute.CATEGORIES, LibraryKind.SERIES)
                        2 -> viewModel.openRoot(LibraryRoute.CONTINUE)
                        3 -> viewModel.openRoot(LibraryRoute.RECENT)
                        else -> viewModel.fallback()
                    }
                }) { tint ->
                    Column(Modifier.align(Alignment.Center).padding((18 * scale).dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                        LibraryIcon(listOf(LibrarySymbol.MOVIES, LibrarySymbol.SERIES, LibrarySymbol.CONTINUE, LibrarySymbol.HISTORY, LibrarySymbol.FOLDER)[index],
                            tint, Modifier.size((58 * scale).dp))
                        Text(stringResource(label), color = tint, fontSize = (22 * scale).sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
              }
            }
        } else if (route == LibraryRoute.DETAIL) {
            val entry = page.location.entry!!
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy((28 * scale).dp)) {
                ChannelLogo(entry.poster, Modifier.width((280 * scale).dp).fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((14 * scale).dp)) {
                    if (entry.kind == LibraryKind.SERIES) action(stringResource(R.string.library_seasons), "seasons", viewModel::seasons)
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                            action(stringResource(R.string.library_play), "play") { viewModel.requestPlay(onPlay) }
                            action(stringResource(if (page.progress.watched) R.string.library_watched else R.string.library_unwatched), "watch", viewModel::toggleWatched)
                            heart()
                        }
                        if (page.nextEpisode != null) action(stringResource(R.string.library_next_episode), "next_episode", click = viewModel::nextEpisode)
                    }
                    val details = page.details
                    val facts = listOfNotNull(details?.year?.takeIf(String::isNotBlank), (details?.runtime?.takeIf(String::isNotBlank) ?: entry.runtime.takeIf(String::isNotBlank)),
                        entry.rating?.toString(), details?.genre?.takeIf(String::isNotBlank)).joinToString("  ·  ")
                    if (facts.isNotBlank()) Text(facts, color = BrowserColors.secondary, fontSize = (21 * scale).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    details?.synopsis?.takeIf(String::isNotBlank)?.let { Text(it, color = BrowserColors.text, fontSize = (23 * scale).sp, maxLines = 9, overflow = TextOverflow.Ellipsis) }
                    details?.cast?.takeIf(String::isNotBlank)?.let { Text(stringResource(R.string.library_cast, it), color = BrowserColors.secondary, fontSize = (19 * scale).sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    details?.director?.takeIf(String::isNotBlank)?.let { Text(stringResource(R.string.library_director, it), color = BrowserColors.secondary, fontSize = (19 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                if (page.loading) LibraryLoading(page, scale, Modifier.weight(1f), page.details != null)
                if (page.failed) Text(stringResource(R.string.library_unavailable), color = BrowserColors.secondary)
                if (page.failed && !page.loading) action(stringResource(R.string.library_retry_details), "retry", click = viewModel::refresh)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                if (route == LibraryRoute.TITLES) action(stringResource(when (page.sort) {
                    LibrarySort.PROVIDER -> R.string.library_sort_provider
                    LibrarySort.NAME -> R.string.library_sort_name
                    LibrarySort.RATING -> R.string.library_sort_rating
                    LibrarySort.RECENT -> if (page.location.kind == LibraryKind.SERIES) R.string.library_sort_updated else R.string.library_sort_added
                }), "sort", click = viewModel::sort)
                if ((route == LibraryRoute.TITLES && page.location.kind == LibraryKind.MOVIE) || route == LibraryRoute.EPISODES)
                    action(stringResource(if (page.unwatched) R.string.library_unwatched_on else R.string.library_unwatched_off), "unwatched", click = viewModel::unwatched)
                if (route == LibraryRoute.EPISODES) heart(season = true)
                if ((page.failed || page.unavailable) && !page.loading) {
                    val retry = if (route == LibraryRoute.CATEGORIES || route == LibraryRoute.TITLES) {
                        if (page.location.kind == LibraryKind.SERIES) R.string.library_retry_series else R.string.library_retry_movies
                    } else R.string.library_retry_details
                    action(stringResource(retry), "retry", click = viewModel::refresh)
                }
            }
            val usable = page.rows.any { it.category?.id != "" }
            if (page.loading && usable) LibraryLoading(page, scale, Modifier.fillMaxWidth(), true)
            if (page.failed) Text(stringResource(R.string.library_unavailable), color = BrowserColors.secondary)
            if (page.unavailable && !page.loading) Text(stringResource(R.string.season_unavailable), color = BrowserColors.secondary)
            if (page.rows.isEmpty() && !page.loading && !page.preparing) Text(stringResource(R.string.category_empty), color = BrowserColors.secondary)
            if (page.loading && !usable) LibraryLoading(page, scale, Modifier.fillMaxWidth().weight(1f), false)
            else LazyVerticalGrid(GridCells.Fixed(density.columns), state = grid, modifier = Modifier.weight(1f).testTag("library_grid"),
                horizontalArrangement = Arrangement.spacedBy((12 * scale).dp), verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                itemsIndexed(page.rows, key = { _, row -> row.key }) { _, row ->
                    val requester = requesters.getOrPut(row.key) { FocusRequester() }
                    BrowserControl(Modifier.height(((640f - (density.rows - 1) * 12) / density.rows * scale).dp)
                        .focusRequester(requester).testTag("library_item_${row.key}"), scale,
                        onFocus = { viewModel.focus(row.key) }, onKey = {
                            if (it.keyCode == KeyEvent.KEYCODE_MENU && row.channel?.channel?.isVod == true && row.entry?.kind != LibraryKind.SERIES) {
                                contextRow = row; true
                            } else false
                        }, onClick = {
                            viewModel.open(row)
                        }) { tint ->
                        Column(Modifier.fillMaxSize().padding((12 * scale).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            row.entry?.poster?.let { ChannelLogo(it, Modifier.weight(1f).fillMaxWidth()) }
                            val label = when {
                                row.category?.id == "" -> stringResource(if (page.location.kind == LibraryKind.MOVIE) R.string.library_all_movies else R.string.library_all_series)
                                row.season != null -> stringResource(R.string.library_season, row.season)
                                row.entry?.episode?.isNotBlank() == true -> stringResource(R.string.library_episode, row.entry.episode, row.label)
                                else -> row.label
                            }
                            Text(label, color = tint, fontSize = (20 * scale).sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (row.progress.watched) Text(stringResource(R.string.library_watched), color = tint, fontSize = (17 * scale).sp)
                            else if (row.progress.resumable) Text(stringResource(R.string.library_progress, row.progress.position / 60000), color = tint, fontSize = (17 * scale).sp)
                            row.entry?.runtime?.takeIf(String::isNotBlank)?.let { Text(it, color = tint, maxLines = 1, fontSize = (17 * scale).sp) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                if (page.page > 0) action(stringResource(R.string.library_previous), "previous") { viewModel.page(-1) }
                if (page.more) action(stringResource(R.string.library_next), "next") { viewModel.page(1) }
            }
        }
    }
    playChoice?.let { progress ->
        androidx.compose.ui.window.Dialog(onDismissRequest = viewModel::cancelPlayChoice) {
            val resume = remember { FocusRequester() }
            LaunchedEffect(Unit) { withFrameNanos { }; resume.requestFocus() }
            Column(Modifier.width((650 * scale).dp).background(BrowserColors.tile).padding((24 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                listOf(false, true).forEach { beginning ->
                    BrowserControl(Modifier.fillMaxWidth().height((72 * scale).dp)
                        .then(if (!beginning) Modifier.focusRequester(resume) else Modifier)
                        .testTag(if (beginning) "play_beginning" else "play_resume"), scale,
                        onClick = { viewModel.play(beginning, onPlay) }) { tint ->
                        Row(Modifier.padding((16 * scale).dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                            LibraryIcon(if (beginning) LibrarySymbol.BEGINNING else LibrarySymbol.CONTINUE, tint, Modifier.size((28 * scale).dp))
                            Text(stringResource(if (beginning) R.string.library_beginning else R.string.library_resume_from, resumeTime(progress.position)),
                                color = tint, fontSize = (23 * scale).sp)
                        }
                    }
                }
            }
        }
    }
    contextRow?.let { row ->
        LibraryActions(viewModel, row) {
            if (contextRow != null) {
                contextRow = null
                menuReturn = if (requesters[row.key] != null) row.key else page.bookmark.focus ?: "entry"
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryActions(viewModel: LibraryViewModel, row: LibraryRow, close: () -> Unit) {
    val focus = remember { FocusRequester() }
    val favoriteFocus = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = close) {
        Column(Modifier.width(460.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val channel = viewModel.channel(row)
            LaunchedEffect(channel) { if (channel != null) { withFrameNanos { }; favoriteFocus.requestFocus() } }
            var watched by remember(row.key) { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(row.key) { watched = viewModel.watched(row) }
            LaunchedEffect(watched) { if (watched != null) { withFrameNanos { }; focus.requestFocus() } }
            if (channel != null) {
              if (watched != null) {
                BrowserControl(Modifier.fillMaxWidth().height(60.dp).focusRequester(focus).testTag("library_mark"), 1f,
                    onClick = { viewModel.mark(channel.channel, watched != true, channel.id, close) }) { tint ->
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LibraryIcon(if (watched == true) LibrarySymbol.WATCHED else LibrarySymbol.UNWATCHED, tint, Modifier.size(26.dp))
                        Text(stringResource(if (watched == true) R.string.library_reset else R.string.library_mark), color = tint)
                    }
                }
              }
                BrowserControl(Modifier.fillMaxWidth().height(60.dp).focusRequester(favoriteFocus), 1f,
                    onClick = { viewModel.favorite(channel); close() }) { tint ->
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LibraryIcon(LibrarySymbol.HEART, tint, Modifier.size(26.dp))
                        Text(stringResource(R.string.library_favorite), color = tint)
                    }
                }
            }
        }
    }
}
