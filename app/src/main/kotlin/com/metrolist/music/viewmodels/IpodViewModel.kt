package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.*
import com.metrolist.innertube.pages.BrowseResult
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.screens.ipod.IpodEvent
import com.metrolist.music.ui.screens.ipod.IpodTheme
import com.metrolist.music.ui.screens.ipod.IpodRoute
import com.metrolist.music.ui.screens.ipod.IpodUiState
import com.metrolist.music.ui.screens.ipod.IpodNavItem
import com.metrolist.music.ui.screens.ipod.MenuNode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IpodViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IpodUiState())
    val uiState = _uiState.asStateFlow()

    // Caches to prevent refetching during deep navigation
    private var cachedHomeSections: List<HomePage.Section>? = null
    private var cachedRecommendations: List<SongItem>? = null
    private var cachedPlaylists: List<Playlist>? = null
    
    val searchQuery = savedStateHandle.getStateFlow("searchQuery", "")

    init {
        // Pre-warm caches
        viewModelScope.launch {
            database.playlistsByNameAsc().collect { dbPlaylists ->
                val likedSongsPlaylist = Playlist(
                    playlist = PlaylistEntity(id = "LP_LIKED", name = "Liked Songs"),
                    songCount = 0,
                    songThumbnails = emptyList()
                )
                cachedPlaylists = listOf(likedSongsPlaylist) + dbPlaylists
            }
        }
        viewModelScope.launch {
            YouTube.home().onSuccess { page ->
                cachedHomeSections = page.sections
                val items = mutableListOf<SongItem>()
                page.sections.forEach { section ->
                    items.addAll(section.items.filterIsInstance<SongItem>())
                }
                cachedRecommendations = items.distinctBy { it.id }
            }
        }
    }

    private val prefetchCache = android.util.LruCache<String, List<MenuNode>>(20)
    private var prefetchJob: Job? = null
    private var routeJob: Job? = null
    private var searchJob: Job? = null
    
    fun onEvent(
        event: IpodEvent,
        playerConnection: PlayerConnection?,
        onNavigateBack: () -> Unit = {},
        onLoginClick: () -> Unit = {},
        onThemeChange: (IpodTheme) -> Unit = {}
    ) {
        when (event) {
            is IpodEvent.OnNavigateBack -> {
                val stack = _uiState.value.navStack
                if (stack.size > 1) {
                    val wasSearch = stack.last().route is IpodRoute.Search
                    if (wasSearch) {
                        clearSearchQuery() // Reset query immediately on back
                    }
                    _uiState.value = _uiState.value.copy(navStack = stack.dropLast(1))
                    loadRoute(_uiState.value.currentRoute, playerConnection)
                } else {
                    onNavigateBack()
                }
            }
            is IpodEvent.OnPlayPause -> playerConnection?.togglePlayPause()
            is IpodEvent.OnNext -> playerConnection?.seekToNext()
            is IpodEvent.OnPrev -> playerConnection?.seekToPrevious()
            is IpodEvent.OnSearchInput -> {
                val key = event.char
                when (key) {
                    "SPACE" -> updateSearchQuery(" ")
                    "DEL" -> backspaceSearchQuery()
                    "ENTER" -> {
                        if (searchQuery.value.isNotBlank()) {
                            executeSearch { items ->
                                clearSearchQuery() // Clear after navigating to results
                                pushRoute(IpodRoute.YTItemList("Results", items), playerConnection)
                            }
                        }
                    }
                    else -> updateSearchQuery(key)
                }
            }
            is IpodEvent.OnThemeChanged -> {
                onThemeChange(event.theme)
            }
            is IpodEvent.OnWheelScrolled -> {
                val currentRoute = _uiState.value.currentRoute
                if (currentRoute is IpodRoute.NowPlaying) {
                    playerConnection?.let { pc ->
                        val pos = pc.player.currentPosition
                        val newPos = (pos + event.delta * 5000L).coerceAtLeast(0L).coerceAtMost(pc.player.duration.coerceAtLeast(0L))
                        pc.seekTo(newPos)
                    }
                } else {
                    val activeNodesSize = when (currentRoute) {
                        is IpodRoute.Settings -> IpodTheme.entries.size + 1
                        is IpodRoute.Search -> 29 // Keyboard chars
                        is IpodRoute.NowPlaying -> 0
                        is IpodRoute.Queue -> playerConnection?.queueWindows?.value?.size ?: 0
                        else -> _uiState.value.activeNodes?.size ?: 0
                    }
                    if (activeNodesSize > 0) {
                        val currentScrollIndex = _uiState.value.navStack.last().scrollIndex
                        val newIndex = (currentScrollIndex + event.delta).coerceIn(0, activeNodesSize - 1)
                        val newStack = _uiState.value.navStack.dropLast(1) + _uiState.value.navStack.last().copy(scrollIndex = newIndex)
                        _uiState.value = _uiState.value.copy(navStack = newStack)
                    }
                }
            }
            is IpodEvent.OnMenuSelected -> {
                val currentRoute = _uiState.value.currentRoute
                when (currentRoute) {
                    is IpodRoute.Settings -> {
                        if (event.index < IpodTheme.entries.size) {
                            onEvent(IpodEvent.OnThemeChanged(IpodTheme.entries[event.index]), playerConnection, onNavigateBack, onLoginClick, onThemeChange)
                        } else {
                            onLoginClick()
                        }
                    }
                    is IpodRoute.Queue -> {
                        playerConnection?.player?.seekToDefaultPosition(event.index)
                        pushRoute(IpodRoute.NowPlaying(), playerConnection)
                    }
                    is IpodRoute.NowPlaying -> {} // Do nothing
                    is IpodRoute.Search -> {
                        val keyboardChars = listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","SPACE","DEL","ENTER")
                        keyboardChars.getOrNull(event.index)?.let { key ->
                            onEvent(IpodEvent.OnSearchInput(key), playerConnection, onNavigateBack, onLoginClick, onThemeChange)
                        }
                    }
                    else -> {
                        _uiState.value.activeNodes?.getOrNull(event.index)?.let { node ->
                            when (node) {
                                is MenuNode.ActionNode -> node.action()
                                is MenuNode.NavNode -> pushRoute(node.destination, playerConnection)
                            }
                        }
                    }
                }
            }
        }
    }

    fun initializeWithDeepLink(initialQuery: String?, initialPlaylistId: String?, initialVideoId: String?, playerConnection: PlayerConnection?) {
        if (!initialQuery.isNullOrEmpty()) {
            setSearchQuery(initialQuery)
            executeSearch { items ->
                val newStack = listOf(IpodNavItem(IpodRoute.Main()), IpodNavItem(IpodRoute.Search()), IpodNavItem(IpodRoute.YTItemList("Results", items)))
                _uiState.value = _uiState.value.copy(navStack = newStack)
                loadRoute(newStack.last().route, playerConnection)
            }
        } else if (!initialPlaylistId.isNullOrEmpty()) {
            val newStack = listOf(IpodNavItem(IpodRoute.Main()), IpodNavItem(IpodRoute.Playlists()), IpodNavItem(IpodRoute.PlaylistSongs("", initialPlaylistId)))
            _uiState.value = _uiState.value.copy(navStack = newStack)
            loadRoute(newStack.last().route, playerConnection)
        } else if (!initialVideoId.isNullOrEmpty()) {
            val newStack = listOf(IpodNavItem(IpodRoute.Main()), IpodNavItem(IpodRoute.NowPlaying()))
            _uiState.value = _uiState.value.copy(navStack = newStack)
            loadRoute(newStack.last().route, playerConnection)
        } else {
            loadRoute(_uiState.value.currentRoute, playerConnection)
        }
    }

    private fun pushRoute(route: IpodRoute, playerConnection: PlayerConnection?) {
        _uiState.value = _uiState.value.copy(navStack = _uiState.value.navStack + IpodNavItem(route))
        loadRoute(route, playerConnection)
    }

    private fun createPlayAction(playerConnection: PlayerConnection?): (SongItem) -> Unit = { song ->
        playerConnection?.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
        playerConnection?.play()
        pushRoute(IpodRoute.NowPlaying(), playerConnection)
    }
    
    fun onNodeHighlighted(node: MenuNode, playerConnection: PlayerConnection?) {
        if (node !is MenuNode.NavNode) return
        
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300) // Debounce rapid scrolling
            val route = node.destination
            val cacheKey = route.toString()
            
            if (prefetchCache.get(cacheKey) != null) return@launch

            when (route) {
                is IpodRoute.OnlinePlaylistSongs -> {
                    if (route.isAlbum && !route.browseId.startsWith("VL")) {
                        YouTube.album(route.browseId).onSuccess { page ->
                            val nodes = page.songs.map { song -> MenuNode.ActionNode(song.title) { createPlayAction(playerConnection)(song) } }
                            prefetchCache.put(cacheKey, nodes)
                        }
                    } else {
                        YouTube.playlist(route.browseId).onSuccess { page ->
                            val nodes = page.songs.map { song -> MenuNode.ActionNode(song.title) { createPlayAction(playerConnection)(song) } }
                            prefetchCache.put(cacheKey, nodes)
                        }
                    }
                }
                is IpodRoute.BrowseResultMenu -> {
                    YouTube.browse(route.browseId, route.params).onSuccess { result ->
                        val nodes = result.items.mapIndexed { index, section ->
                            MenuNode.NavNode(section.title ?: "Section", IpodRoute.BrowseSectionMenu(section.title ?: "Section", route.browseId, index))
                        }
                        prefetchCache.put(cacheKey, nodes)
                    }
                }
                else -> {} // Skip prefetching complex routes for now
            }
        }
    }

    fun loadRoute(route: IpodRoute, playerConnection: PlayerConnection?) {
        routeJob?.cancel()
        _uiState.value = _uiState.value.copy(activeNodes = null, error = null)
        
        // Fast-path: Return prefetched data instantly
        prefetchCache.get(route.toString())?.let { cachedNodes ->
            _uiState.value = _uiState.value.copy(activeNodes = cachedNodes)
            return
        }

        when (route) {
            is IpodRoute.Main -> {
                val nodes = listOf(
                    MenuNode.NavNode("Recommended Music", IpodRoute.Music()),
                    MenuNode.NavNode("Your Playlists", IpodRoute.Playlists()),
                    MenuNode.NavNode("Search", IpodRoute.Search()),
                    MenuNode.NavNode("Now Playing", IpodRoute.NowPlaying()),
                    MenuNode.NavNode("Queue", IpodRoute.Queue()),
                    MenuNode.NavNode("Settings", IpodRoute.Settings())
                )
                _uiState.value = _uiState.value.copy(activeNodes = nodes)
            }
            is IpodRoute.Music -> {
                val recs = cachedRecommendations
                if (recs != null) {
                    val nodes = recs.map { song ->
                        MenuNode.ActionNode(song.title) { createPlayAction(playerConnection)(song) }
                    }
                    _uiState.value = _uiState.value.copy(activeNodes = nodes)
                } else {
                    _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = "No recommendations found")
                }
            }
            is IpodRoute.Playlists -> {
                val nodes = cachedPlaylists?.map { playlist ->
                    MenuNode.NavNode(playlist.title, IpodRoute.PlaylistSongs(playlist.title, playlist.id))
                } ?: emptyList()
                _uiState.value = _uiState.value.copy(activeNodes = nodes)
            }
            is IpodRoute.BrowseResultMenu -> {
                routeJob = viewModelScope.launch {
                    try {
                        YouTube.browse(route.browseId, route.params).onSuccess { result ->
                            val nodes = mutableListOf<MenuNode>()
                            result.items.forEachIndexed { index, section ->
                                val sectionTitle = section.title
                                if (sectionTitle != null && section.items.isNotEmpty()) {
                                    nodes.add(MenuNode.NavNode(sectionTitle, IpodRoute.BrowseSectionMenu(sectionTitle, route.browseId, index)))
                                } else {
                                    section.items.forEach { item ->
                                        when (item) {
                                            is PlaylistItem -> nodes.add(MenuNode.NavNode(item.title, IpodRoute.OnlinePlaylistSongs(item.title, item.id, false)))
                                            is AlbumItem -> nodes.add(MenuNode.NavNode(item.title, IpodRoute.OnlinePlaylistSongs(item.title, item.browseId, true)))
                                            is SongItem -> nodes.add(MenuNode.ActionNode(item.title) { createPlayAction(playerConnection)(item) })
                                            is ArtistItem -> nodes.add(MenuNode.NavNode(item.title, IpodRoute.BrowseResultMenu(item.title, item.id, null)))
                                            is PodcastItem -> nodes.add(MenuNode.NavNode(item.title, IpodRoute.BrowseResultMenu(item.title, item.id, null)))
                                            is EpisodeItem -> nodes.add(MenuNode.ActionNode(item.title) { createPlayAction(playerConnection)(item.asSongItem()) })
                                        }
                                    }
                                }
                            }
                            if (nodes.isEmpty()) {
                                _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = "No items found")
                            } else {
                                _uiState.value = _uiState.value.copy(activeNodes = nodes)
                            }
                        }.onFailure { err ->
                            _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = err.message ?: "Failed to load")
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = "Failed to load: ${e.message}")
                    }
                }
            }
            is IpodRoute.BrowseSectionMenu -> {
                // To keep it simple, if we already loaded it, we should pass it.
                // But since we can't easily cache every nested section, we re-fetch the parent.
                // In a perfect system we'd cache `BrowseResult` maps.
                routeJob = viewModelScope.launch {
                    try {
                        YouTube.browse(route.parentBrowseId, null).onSuccess { result ->
                            val section = result.items.getOrNull(route.index)
                            if (section != null) {
                                val nodes = section.items.map { item ->
                                    when (item) {
                                        is PlaylistItem -> MenuNode.NavNode(item.title, IpodRoute.OnlinePlaylistSongs(item.title, item.id, false))
                                        is AlbumItem -> MenuNode.NavNode(item.title, IpodRoute.OnlinePlaylistSongs(item.title, item.browseId, true))
                                        is SongItem -> MenuNode.ActionNode(item.title) { createPlayAction(playerConnection)(item) }
                                        is ArtistItem -> MenuNode.NavNode(item.title, IpodRoute.BrowseResultMenu(item.title, item.id, null))
                                        is PodcastItem -> MenuNode.NavNode(item.title, IpodRoute.BrowseResultMenu(item.title, item.id, null))
                                        is EpisodeItem -> MenuNode.ActionNode(item.title) { createPlayAction(playerConnection)(item.asSongItem()) }
                                    }
                                }
                                _uiState.value = _uiState.value.copy(activeNodes = nodes)
                            } else {
                                _uiState.value = _uiState.value.copy(activeNodes = emptyList())
                            }
                        }.onFailure {
                            _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = "Failed to load")
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = "Failed to load section: ${e.message}")
                    }
                }
            }
            is IpodRoute.PlaylistSongs -> {
                routeJob = viewModelScope.launch {
                    try {
                        if (route.id == "LP_LIKED") {
                            database.likedSongs(SongSortType.CREATE_DATE, true).collect { songs ->
                                val collected = songs.map {
                                    SongItem(
                                        id = it.song.id,
                                        title = it.song.title,
                                        artists = it.orderedArtists.map { a -> Artist(a.name, a.id) },
                                        album = it.album?.let { a -> Album(a.title, a.id) },
                                        duration = it.song.duration,
                                        thumbnail = it.song.thumbnailUrl ?: ""
                                    )
                                }
                                val nodes = collected.map { song -> MenuNode.ActionNode(song.title) { createPlayAction(playerConnection)(song) } }
                                _uiState.value = _uiState.value.copy(activeNodes = nodes)
                            }
                        } else {
                            database.playlistSongs(route.id).collect { songs ->
                                val collected = songs.map {
                                    SongItem(
                                        id = it.song.id,
                                        title = it.song.title,
                                        artists = it.song.orderedArtists.map { a -> Artist(a.name, a.id) },
                                        album = it.song.album?.let { a -> Album(a.title, a.id) },
                                        duration = it.song.song.duration,
                                        thumbnail = it.song.song.thumbnailUrl ?: ""
                                    )
                                }
                                val nodes = collected.map { song -> MenuNode.ActionNode(song.title) { createPlayAction(playerConnection)(song) } }
                                _uiState.value = _uiState.value.copy(activeNodes = nodes)
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = "Failed to load local playlist")
                    }
                }
            }
            is IpodRoute.OnlinePlaylistSongs -> {
                routeJob = viewModelScope.launch {
                    try {
                        if (route.isAlbum && !route.browseId.startsWith("VL")) {
                            YouTube.album(route.browseId).onSuccess { page ->
                                val nodes = page.songs.map { song -> MenuNode.ActionNode(song.title) { createPlayAction(playerConnection)(song) } }
                                _uiState.value = _uiState.value.copy(activeNodes = nodes)
                            }.onFailure {
                                _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = "Failed to load")
                            }
                        } else {
                            YouTube.playlist(route.browseId).onSuccess { page ->
                                val nodes = page.songs.map { song -> MenuNode.ActionNode(song.title) { createPlayAction(playerConnection)(song) } }
                                _uiState.value = _uiState.value.copy(activeNodes = nodes)
                            }.onFailure {
                                _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = "Failed to load")
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(activeNodes = emptyList(), error = "Failed to load playlist/album: ${e.message}")
                    }
                }
            }
            is IpodRoute.YTItemList -> {
                val nodes = route.items.map { item ->
                    when (item) {
                        is PlaylistItem -> MenuNode.NavNode(item.title, IpodRoute.OnlinePlaylistSongs(item.title, item.id, false))
                        is AlbumItem -> MenuNode.NavNode(item.title, IpodRoute.OnlinePlaylistSongs(item.title, item.browseId, true))
                        is SongItem -> MenuNode.ActionNode(item.title) { createPlayAction(playerConnection)(item) }
                        is ArtistItem -> MenuNode.NavNode(item.title, IpodRoute.BrowseResultMenu(item.title, item.id, null))
                        is PodcastItem -> MenuNode.NavNode(item.title, IpodRoute.BrowseResultMenu(item.title, item.id, null))
                        is EpisodeItem -> MenuNode.ActionNode(item.title) { createPlayAction(playerConnection)(item.asSongItem()) }
                    }
                }
                _uiState.value = _uiState.value.copy(activeNodes = nodes)
            }
            is IpodRoute.Search -> {
                clearSearchQuery()
                _uiState.value = _uiState.value.copy(activeNodes = emptyList())
            }
            is IpodRoute.Settings, is IpodRoute.Queue, is IpodRoute.NowPlaying -> {
                // Handled by custom UI in IpodScreen
                _uiState.value = _uiState.value.copy(activeNodes = emptyList())
            }
        }
    }

    fun updateSearchQuery(append: String) {
        savedStateHandle["searchQuery"] = searchQuery.value + append
    }

    fun setSearchQuery(query: String) {
        savedStateHandle["searchQuery"] = query
    }

    fun clearSearchQuery() {
        savedStateHandle["searchQuery"] = ""
    }

    fun backspaceSearchQuery() {
        val current = searchQuery.value
        if (current.isNotEmpty()) {
            savedStateHandle["searchQuery"] = current.dropLast(1)
        }
    }

    fun executeSearch(onResult: (List<YTItem>) -> Unit) {
        if (searchQuery.value.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                YouTube.searchSummary(searchQuery.value).onSuccess { page ->
                    val items = mutableListOf<SongItem>()
                    page.summaries.forEach { summary ->
                        items.addAll(summary.items.filterIsInstance<SongItem>())
                    }
                    onResult(items.distinctBy { it.id })
                }.onFailure {
                    onResult(emptyList())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                onResult(emptyList())
            }
        }
    }
}
