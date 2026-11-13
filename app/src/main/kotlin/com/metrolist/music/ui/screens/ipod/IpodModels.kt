package com.metrolist.music.ui.screens.ipod

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.db.entities.Playlist

enum class IpodTheme(val chassis: Color, val wheel: Color, val center: Color, val display: Color) {
    // Classics
    FIFTH_GEN_WHITE(Color(0xFFF7F7F7), Color(0xFFE6E7E9), Color(0xFFF7F7F7), Color(0xFFFFFFFF)),
    SIXTH_GEN_SILVER(Color(0xFFD8DADF), Color(0xFFFFFFFF), Color(0xFFD8DADF), Color(0xFFFFFFFF)),
    SIXTH_GEN_CHARCOAL(Color(0xFF222326), Color(0xFF141416), Color(0xFF222326), Color(0xFFFFFFFF)),
    
    // Emotional / Vibe Colors (Color Theory)
    CALM_OCEAN(Color(0xFF6B8B9B), Color(0xFFE8F1F5), Color(0xFF6B8B9B), Color(0xFFFFFFFF)),   // Serene blue-gray for relaxation
    DEEP_PEACE(Color(0xFF2C3E50), Color(0xFF34495E), Color(0xFF2C3E50), Color(0xFFFFFFFF)),   // Dark, muted navy for focus
    DANCE_VIBE(Color(0xFFFF3366), Color(0xFF222222), Color(0xFFFF3366), Color(0xFFFFFFFF)),   // High energy neon pink & dark contrast
    SING_ALONG(Color(0xFFFFB03B), Color(0xFFFFF9E6), Color(0xFFFFB03B), Color(0xFFFFFFFF)),   // Warm, inviting amber/gold
    LATE_NIGHT(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFFFFFFFF))    // Midnight slate with retro green glow display
}

val LocalIpodTheme = compositionLocalOf { IpodTheme.SIXTH_GEN_SILVER }

sealed interface IpodEvent {
    data class OnWheelScrolled(val delta: Int) : IpodEvent
    data class OnMenuSelected(val index: Int) : IpodEvent
    data class OnThemeChanged(val theme: IpodTheme) : IpodEvent
    data class OnSearchInput(val char: String) : IpodEvent
    object OnNavigateBack : IpodEvent
    
    // Playback intents
    object OnPlayPause : IpodEvent
    object OnNext : IpodEvent
    object OnPrev : IpodEvent
}

data class IpodNavItem(val route: IpodRoute, val scrollIndex: Int = 0)

data class IpodUiState(
    val navStack: List<IpodNavItem> = listOf(IpodNavItem(IpodRoute.Main())),
    val searchQuery: String = "",
    val activeNodes: List<MenuNode>? = null, // null means loading
    val error: String? = null
) {
    val currentRoute: IpodRoute get() = navStack.last().route
    val currentScrollIndex: Int get() = navStack.last().scrollIndex
}

sealed class MenuNode {
    abstract val title: String
    
    data class ActionNode(
        override val title: String,
        val action: () -> Unit
    ) : MenuNode()

    data class NavNode(
        override val title: String,
        val destination: IpodRoute
    ) : MenuNode()
}

sealed class IpodRoute(val title: String) {
    data class Main(val id: Int = 0) : IpodRoute("Jukebox")
    data class NowPlaying(val id: Int = 0) : IpodRoute("Now Playing")
    data class Queue(val id: Int = 0) : IpodRoute("Up Next")
    data class Settings(val id: Int = 0) : IpodRoute("Settings")
    
    data class Music(val id: Int = 0) : IpodRoute("Music")
    data class Playlists(val id: Int = 0) : IpodRoute("Playlists")
    
    data class BrowseResultMenu(val menuTitle: String, val browseId: String, val params: String?) : IpodRoute(menuTitle)
    data class BrowseSectionMenu(val menuTitle: String, val parentBrowseId: String, val index: Int) : IpodRoute(menuTitle)
    data class OnlinePlaylistSongs(val menuTitle: String, val browseId: String, val isAlbum: Boolean) : IpodRoute(menuTitle)
    data class PlaylistSongs(val menuTitle: String, val id: String) : IpodRoute(menuTitle)
    
    data class Search(val id: Int = 0) : IpodRoute("Search")
    data class YTItemList(val menuTitle: String, val items: List<YTItem>) : IpodRoute(menuTitle)
}
