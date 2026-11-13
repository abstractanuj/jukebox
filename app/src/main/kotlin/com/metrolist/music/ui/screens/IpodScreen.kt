package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.constants.IpodThemeKey
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.viewmodels.IpodViewModel
import com.metrolist.music.ui.screens.ipod.*
import kotlinx.coroutines.delay

@Composable
fun IpodScreen(
    onNavigateBack: () -> Unit,
    onLoginClick: () -> Unit,
    initialQuery: String? = null,
    initialPlaylistId: String? = null,
    initialVideoId: String? = null,
    viewModel: IpodViewModel = hiltViewModel()
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navStack = uiState.navStack
    val currentNavItem = navStack.last()
    val currentRoute = currentNavItem.route

    val storedThemeState = rememberEnumPreference(IpodThemeKey, IpodTheme.SIXTH_GEN_SILVER)
    var currentTheme by remember { mutableStateOf(storedThemeState.value) }
    LaunchedEffect(storedThemeState.value) { currentTheme = storedThemeState.value }

    LaunchedEffect(initialQuery, initialPlaylistId, initialVideoId) {
        viewModel.initializeWithDeepLink(initialQuery, initialPlaylistId, initialVideoId, playerConnection)
    }

    BackHandler { 
        viewModel.onEvent(IpodEvent.OnNavigateBack, playerConnection, onNavigateBack, onLoginClick) {
            currentTheme = it
            storedThemeState.value = it
        }
    }

    val selectedIndex = currentNavItem.scrollIndex

    CompositionLocalProvider(LocalIpodTheme provides currentTheme) {
        val chassisColor = LocalIpodTheme.current.chassis
        val displayBgColor = LocalIpodTheme.current.display

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(chassisColor)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- UPPER DISPLAY ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(displayBgColor)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val viewportWidth = maxWidth
                    val viewportHeight = maxHeight
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentRoute,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                val targetIndex = navStack.indexOfFirst { it.route == targetState }.takeIf { it != -1 } ?: navStack.size
                                val initialIndex = navStack.indexOfFirst { it.route == initialState }.takeIf { it != -1 } ?: navStack.size
                                val direction = if (targetIndex >= initialIndex) 1 else -1
                                
                                val pushSpec = tween<androidx.compose.ui.unit.IntOffset>(280, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f))
                                val popSpec = tween<androidx.compose.ui.unit.IntOffset>(260, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f))
    
                                if (direction == 1) { // Pushing deeper
                                    slideIntoContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                        animationSpec = pushSpec
                                    ) togetherWith slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                        animationSpec = pushSpec
                                    )
                                } else { // Popping back
                                    slideIntoContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                        animationSpec = popSpec
                                    ) togetherWith slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                        animationSpec = popSpec
                                    )
                                }.using(SizeTransform(clip = false))
                            },
                            label = "IpodMenuTransition"
                        ) { targetRoute ->
                            val idx = navStack.lastOrNull { it.route == targetRoute }?.scrollIndex ?: 0
                            
                            val isRoot = targetRoute is IpodRoute.Main || targetRoute is IpodRoute.Settings
        
                            when (targetRoute) {
                                is IpodRoute.NowPlaying -> IpodNowPlaying(playerConnection, viewportWidth, viewportHeight)
                                is IpodRoute.Settings -> {
                                    val themes = IpodTheme.entries.map { it.name.replace("_", " ") }
                                    val settingsItems = (themes + listOf("Login to YouTube")).map { IpodListItem(it) }
                                    IpodGenericListMenu("Settings", settingsItems, idx, playerConnection, isRoot, viewportWidth, viewportHeight) { 
                                        viewModel.onEvent(IpodEvent.OnMenuSelected(it), playerConnection, onNavigateBack, onLoginClick) { theme ->
                                            currentTheme = theme
                                            storedThemeState.value = theme
                                        }
                                    }
                                }
                                is IpodRoute.Queue -> {
                                    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
                                    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
                                    if (queueWindows.isEmpty()) {
                                        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) { Text("Queue is empty", color = Color.Black) }
                                    } else {
                                        val items = queueWindows.mapIndexed { i, window ->
                                            val title = window.mediaItem.mediaMetadata.title?.toString() ?: "Unknown"
                                            val icon = if (i == currentWindowIndex) "▶" else ""
                                            IpodListItem(title, icon)
                                        }
                                        IpodGenericListMenu("Up Next", items, idx, playerConnection, isRoot, viewportWidth, viewportHeight) {
                                            viewModel.onEvent(IpodEvent.OnMenuSelected(it), playerConnection, onNavigateBack, onLoginClick) { theme ->
                                                currentTheme = theme
                                                storedThemeState.value = theme
                                            }
                                        }
                                    }
                                }
                                is IpodRoute.Search -> {
                                    val keyboardChars = listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","SPACE","DEL","ENTER")
                                    val query = viewModel.searchQuery.collectAsStateWithLifecycle().value
                                    IpodSearchMenu(query, idx, keyboardChars) { targetIndex ->
                                        // Update visual keyboard selector
                                        viewModel.onEvent(IpodEvent.OnWheelScrolled(targetIndex - idx), playerConnection, onNavigateBack, onLoginClick) {}
                                        
                                        // Pass the actual string character instead of the index menu event
                                        viewModel.onEvent(IpodEvent.OnSearchInput(keyboardChars[targetIndex]), playerConnection, onNavigateBack, onLoginClick) {}
                                    }
                                }
                                else -> {
                                    if (uiState.error != null) {
                                        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) { Text(uiState.error ?: "", color = Color.Red) }
                                    } else if (uiState.activeNodes != null) {
                                        val items = uiState.activeNodes!!.map { node -> 
                                            IpodListItem(node.title, if (node is MenuNode.ActionNode) "" else "›")
                                        }
                                        IpodGenericListMenu(targetRoute.title, items, idx, playerConnection, isRoot, viewportWidth, viewportHeight) { 
                                            viewModel.onEvent(IpodEvent.OnMenuSelected(it), playerConnection, onNavigateBack, onLoginClick) { theme ->
                                                currentTheme = theme
                                                storedThemeState.value = theme
                                            }
                                        }
                                    } else {
                                        Box(Modifier.fillMaxSize().background(Color.White))
                                    }
                                }
                            }
                        }
                        
                        if (uiState.activeNodes == null && currentRoute !is IpodRoute.NowPlaying && currentRoute !is IpodRoute.Settings && currentRoute !is IpodRoute.Queue && currentRoute !is IpodRoute.Search) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                IpodLoadingIndicator()
                            }
                        }
                    }
                }
            }

            // --- LOWER CHASSIS ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                IpodClickWheel(
                    onInteract = {},
                    onMenu = { 
                        viewModel.onEvent(IpodEvent.OnNavigateBack, playerConnection, onNavigateBack, onLoginClick) {
                            currentTheme = it
                            storedThemeState.value = it
                        }
                    },
                    onPlayPause = { viewModel.onEvent(IpodEvent.OnPlayPause, playerConnection, onNavigateBack, onLoginClick) { currentTheme = it; storedThemeState.value = it } },
                    onPrev = { viewModel.onEvent(IpodEvent.OnPrev, playerConnection, onNavigateBack, onLoginClick) { currentTheme = it; storedThemeState.value = it } },
                    onNext = { viewModel.onEvent(IpodEvent.OnNext, playerConnection, onNavigateBack, onLoginClick) { currentTheme = it; storedThemeState.value = it } },
                    onScroll = { delta -> 
                        viewModel.onEvent(IpodEvent.OnWheelScrolled(delta), playerConnection, onNavigateBack, onLoginClick) { currentTheme = it; storedThemeState.value = it }
                    },
                    onSelect = {
                        val menuListSize = when (currentRoute) {
                            is IpodRoute.Settings -> IpodTheme.entries.size + 1
                            is IpodRoute.Search -> 29
                            is IpodRoute.NowPlaying -> 0
                            is IpodRoute.Queue -> playerConnection.queueWindows.value.size
                            else -> uiState.activeNodes?.size ?: 0
                        }
                        if (menuListSize > 0) {
                            viewModel.onEvent(IpodEvent.OnMenuSelected(selectedIndex), playerConnection, onNavigateBack, onLoginClick) {
                                currentTheme = it
                                storedThemeState.value = it
                            }
                        }
                    }
                )
            }
        }
    }
}

data class IpodListItem(val title: String, val icon: String = ">")

@Composable
fun IpodGenericListMenu(
    title: String,
    items: List<IpodListItem>,
    selectedIndex: Int,
    playerConnection: PlayerConnection?,
    isRoot: Boolean = false,
    viewportWidth: androidx.compose.ui.unit.Dp,
    viewportHeight: androidx.compose.ui.unit.Dp,
    onItemClick: (Int) -> Unit
) {
    // Add strict 31px discrete scrolling
    val rowHeight = 31.dp
    val headerHeight = 22.dp
    val contentHeight = viewportHeight - headerHeight
    val visibleRows = (contentHeight / rowHeight).toInt()
    
    // Calculate the top index to display so that the selected item is always visible
    val topIndex = remember(selectedIndex, visibleRows, items.size) {
        if (items.isEmpty()) 0
        else {
            val maxTop = maxOf(0, items.size - visibleRows)
            when {
                selectedIndex < 0 -> 0 // Should not happen
                selectedIndex > items.lastIndex -> maxTop // Should not happen
                else -> {
                    // Try to keep selection in the middle if possible, but don't scroll past ends
                    val half = visibleRows / 2
                    val targetTop = selectedIndex - half
                    targetTop.coerceIn(0, maxTop)
                }
            }
        }
    }
    
    val paneWidth = if (isRoot) viewportWidth / 2 else viewportWidth
    
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(paneWidth).fillMaxHeight()) {
            IpodHeader(title, playerConnection)
            Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No Playlists", fontSize = 15.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Render only the visible rows
                    val endIndex = minOf(items.size, topIndex + visibleRows)
                    for (index in topIndex until endIndex) {
                        val item = items[index]
                        val isSelected = index == selectedIndex
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .background(
                                    if (isSelected) 
                                        Brush.verticalGradient(
                                            0f to Color(0xFF5CA0F2), 
                                            0.49f to Color(0xFF347FE8), 
                                            0.5f to Color(0xFF1E69DE), 
                                            1f to Color(0xFF1654C0)
                                        ) 
                                    else 
                                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                                )
                                .clickable { onItemClick(index) }
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title, 
                                color = if (isSelected) Color.White else Color.Black, 
                                fontSize = 14.sp, // Reduced font size for tighter rows
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).then(if (isSelected) Modifier.basicMarquee(initialDelayMillis = 1500, velocity = 35.dp) else Modifier)
                            )
                            if (item.icon.isNotEmpty()) {
                                Text(
                                    text = item.icon, 
                                    color = if (isSelected) Color.White else Color.Gray, 
                                    fontSize = 14.sp, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // Classic scrollbar
                if (items.size > visibleRows) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(8.dp)
                            .background(Color(0xFFE1E4E8))
                    ) {
                        val thumbHeight = (visibleRows.toFloat() / items.size.toFloat()) * 100f
                        val thumbOffset = (topIndex.toFloat() / items.size.toFloat()) * 100f
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(thumbHeight / 100f)
                                .offset(y = (contentHeight * (thumbOffset / 100f)))
                                .background(Brush.verticalGradient(listOf(Color(0xFF8A95A5), Color(0xFF6B7585))))
                        )
                    }
                }
            }
        }
        
        // Split screen preview
        if (isRoot) {
            Box(Modifier.width(paneWidth).fillMaxHeight().background(Color.White)) {
                // Determine what to show on right pane based on selection
                val selectedItem = items.getOrNull(selectedIndex)
                AnimatedContent(
                    targetState = selectedItem?.title,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                    modifier = Modifier.fillMaxSize(),
                    label = "PreviewSwap"
                ) { title ->
                    if (title != null) {
                        when (title) {
                            "Settings" -> {
                               // Render settings preview (glossy iPod or icon)
                               Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                   Text("Settings", color = Color.Gray)
                               }
                            }
                            "Up Next" -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                   Text("Queue", color = Color.Gray)
                               }
                            }
                            "Search" -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                   Text("Search", color = Color.Gray)
                               }
                            }
                            else -> { // Jukebox / Music
                                // For now just show "Music Preview"
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                   Text("Music", color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IpodSearchMenu(query: String, selectedIndex: Int, keyboardChars: List<String>, onItemClick: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        IpodHeader("Search", null)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFE5E5E5), RoundedCornerShape(4.dp)).padding(8.dp)) {
                    Text(if (query.isEmpty()) "Search..." else query, fontSize = 16.sp, color = if (query.isEmpty()) Color.Gray else Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    keyboardChars.chunked(6).forEachIndexed { rowIndex, rowItems ->
                        Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowItems.forEachIndexed { colIndex, key ->
                                val index = rowIndex * 6 + colIndex
                                val isSelected = index == selectedIndex
                                val isAction = key == "SPACE" || key == "ENTER" || key == "DEL"
                                Box(
                                    modifier = Modifier
                                        .weight(if (key == "SPACE" || key == "ENTER") 2f else 1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) Color(0xFF3B82F6) else (if (isAction) Color(0xFFC4C4C4) else Color.Transparent))
                                        .clickable { onItemClick(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = if (key.length > 1) 10.sp else 14.sp,
                                        fontWeight = if (isSelected || isAction) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IpodNowPlaying(playerConnection: PlayerConnection, viewportWidth: androidx.compose.ui.unit.Dp, viewportHeight: androidx.compose.ui.unit.Dp) {
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    
    val positionState = remember { mutableLongStateOf(0L) }
    val durationState = remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, mediaMetadata?.id) {
        positionState.longValue = runCatching { playerConnection.player.currentPosition }.getOrDefault(0L)
        durationState.longValue = runCatching { playerConnection.player.duration }.getOrDefault(0L)
        
        if (isPlaying) {
            while (true) {
                delay(100)
                positionState.longValue = runCatching { playerConnection.player.currentPosition }.getOrDefault(0L)
                durationState.longValue = runCatching { playerConnection.player.duration }.getOrDefault(0L)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        IpodHeader("Now Playing", playerConnection)
        
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Left Column (Album Art Stage, X: 12 to 132)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(viewportWidth * 0.45f) // Roughly left half
                    .padding(start = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (mediaMetadata != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // The art itself
                        AsyncImage(
                            model = mediaMetadata?.thumbnailUrl,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(115.dp)
                                .border(1.dp, Color(0xFFCCCCCC))
                        )
                        
                        // True reflection
                        Box(modifier = Modifier.size(115.dp, 35.dp)) {
                            AsyncImage(
                                model = mediaMetadata?.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleY = -1f
                                        alpha = 0.5f
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.White.copy(alpha = 0.4f),
                                            1f to Color.White
                                        )
                                    )
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.size(115.dp).border(1.dp, Color.Gray))
                }
            }
            
            // Right Column (Metadata Stack)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(viewportWidth * 0.55f) // Roughly right half
                    .padding(start = 10.dp, end = 12.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    mediaMetadata?.title ?: "Unknown Song", 
                    fontSize = 15.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color(0xFF000000), 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    mediaMetadata?.artists?.joinToString { it.name } ?: "Unknown Artist", 
                    fontSize = 13.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color(0xFF333333), 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bottom Scrubber Zone (Y: 182 to 212)
        val position = positionState.longValue.coerceAtLeast(0L).toFloat()
        val duration = durationState.longValue.coerceAtLeast(1L).toFloat()
        val progress = if (duration > 0f) position / duration else 0f
        
        val formatTime = { ms: Long ->
            val safeMs = ms.coerceAtLeast(0L)
            val totalSeconds = safeMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
        }
        val remaining = durationState.longValue - positionState.longValue
        val formatTimeRemaining = { ms: Long ->
            val safeMs = ms.coerceAtLeast(0L)
            val totalSeconds = safeMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format(java.util.Locale.US, "-%d:%02d", minutes, seconds)
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 20.dp, vertical = 6.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatTime(positionState.longValue), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(10.dp)
                    .background(Color(0xFFD0D4DC))
                    .border(0.5.dp, Color.Gray)
            ) {
                Box(Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Brush.verticalGradient(listOf(Color(0xFF4A90E2), Color(0xFF1B60B8))))
                )
            }
            Text(formatTimeRemaining(remaining), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}
