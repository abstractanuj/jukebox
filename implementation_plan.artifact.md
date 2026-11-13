# Codebase Stability & Architecture Polish Plan

This plan addresses the technical debt, hidden edge cases, and stability risks identified in the codebase analysis. By executing this plan, we will solidify the foundation (building blocks) of the application, improving memory safety, preventing infinite loops, and adhering to Clean Architecture principles.

## Goal Description

Refactor critical points in the application to ensure data classes are pure (no network side-effects), prevent infinite skipping loops in the media player, and ensure BroadcastReceivers do not leak memory via unmanaged coroutines.

## Proposed Changes

### 1. Purify Domain Models (Entities)
Data classes representing database entities should not execute background network requests. This leads to untracked coroutines and silent failures.

#### [MODIFY] `SongEntity.kt` & `AlbumEntity.kt`
- Remove `CoroutineScope(Dispatchers.IO).launch { YouTube.likeVideo(...) }` from the `toggleLike` and `toggleLibrary` methods.
- The methods will become pure functions that only return the modified entity state.

#### [MODIFY] Handlers (`QueueMenu.kt`, `SongMenu.kt`, `SelectionSongsMenu.kt`, `YouTubeSongMenu.kt`)
- Update the call sites of `toggleLike` and `toggleLibrary`.
- After updating the local database state, launch the YouTube sync request using the existing `coroutineScope` (which is properly tied to the UI/ViewModel lifecycle) to ensure the network request is tracked and canceled appropriately if the user leaves the context.

### 2. ExoPlayer Infinite Skip Circuit Breaker
If the device loses internet connection, the player currently skips to the next track infinitely, wiping out the queue and draining the battery.

#### [MODIFY] `MusicService.kt`
- Introduce a `consecutiveErrorCount` state variable.
- In `handleFinalFailure()`, increment this counter.
- If `consecutiveErrorCount > 3` (or similar threshold), trigger `stopOnError()` (pausing playback and holding the queue state) instead of `skipOnError()`.
- Reset `consecutiveErrorCount` to `0` inside `onIsPlayingChanged` when playback successfully resumes.

### 3. Safe BroadcastReceiver Coroutines
BroadcastReceivers have a maximum lifespan (typically 10 seconds). Launching an unmanaged `CoroutineScope` inside them can cause memory leaks and process crashes.

#### [MODIFY] `PlaylistWidgetReceiver.kt` (and similar receivers)
- Refactor the `refreshIdleWidgets` methods.
- Continue using `goAsync()`, but ensure the `launch` block is properly tracked. If possible, utilize `GlobalScope` safely with a strict timeout (`withTimeout(9000L)`), or pass the intent to `WorkManager` for guaranteed background execution. (We will implement a safe `PendingResult` coroutine wrapper).

## Verification Plan

### Automated Tests
- Ensure `SongEntityTest.kt` passes after removing the `syncToYouTube` parameter from the entity functions.

### Manual Verification
1. **Circuit Breaker:** Turn off Wi-Fi/Cellular data while a playlist is queued. Let the current track fail. Verify that after ~3 tracks fail, the player pauses instead of skipping through the entire 100-song queue.
2. **Entity Purity:** Like a song from the UI. Verify the UI updates instantly (optimistic update) and that the network request to YouTube is still dispatched successfully in the background.
3. **Widgets:** Add the Playlist widget to the home screen and interact with it to ensure `goAsync()` completes successfully without leaving zombie coroutines.