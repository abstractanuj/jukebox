# Metrolist Target Architecture

To make the codebase easy to maintain, scalable, and highly performant, we need to transition from a monolithic structure to a **Modular Clean Architecture**. This means separating the "How it looks" (Front-end) from the "How it works" (Back-end/Playback), and grouping features into self-contained building blocks.

## 1. Modularization (The Building Blocks)
Instead of keeping everything in the `app` module, we split the code into smaller, grouped modules. If you want to change the iPod UI, you shouldn't accidentally break the background playback service.

* **`:core:ui`**: The design system. Contains pure Compose components (buttons, custom click wheels, loading indicators, typography). **No business logic or ViewModels live here.**
* **`:core:playback`**: The audio engine. Contains Jetpack Media3 (`ExoPlayer`), the `MusicService`, and exposes a clean interface (e.g., `PlaybackController`) to control media.
* **`:core:data`**: The truth engine. Contains the Room Database, Innertube API handlers, and Repositories.
* **`:feature:[name]`**: Standalone feature modules (e.g., `:feature:ipod`, `:feature:library`, `:feature:search`). They use the core modules to build screens.
* **`:app`**: The glue. It contains `MainActivity`, the root `NavHost`, and Hilt dependency injection setup. It connects features together.

## 2. Front-End Architecture (Unidirectional Data Flow)
We will standardize how the UI communicates with the rest of the app using **State and Events**.

* **State**: Every screen has a single data class representing everything on it.
  ```kotlin
  data class IpodUiState(val activeMenu: List<MenuItem>, val isLoading: Boolean)
  ```
* **Events**: The UI (Compose) does not execute logic. It sends simple intents to the Handler/ViewModel.
  ```kotlin
  sealed class IpodEvent {
      class OnWheelScrolled(val delta: Int) : IpodEvent()
      class OnPlaySong(val songId: String) : IpodEvent()
  }
  ```
* **UI Render**: The Compose screen only reads the State and draws it. It uses modern Compose animations (`AnimatedContent`, Shared Element Transitions) to make it fluid.

## 3. Back-End Architecture (Handlers & Repositories)
The "backend" of the Android app handles data and playback efficiently in the background without blocking the UI.

* **Repositories**: The UI never talks to the Database or YouTube API directly. It asks a `MusicRepository`. The repository decides whether to fetch from the local Room DB (fast) or the network (slow), caching the results automatically.
* **Playback Handlers**: The `MusicService` runs entirely decoupled from the UI. Features like the iPod UI don't manage ExoPlayer directly; they send a command to a singleton `PlayerConnection` interface, which safely dispatches it to the background service.

## 4. Modern Standards & Libraries
* **UI**: Jetpack Compose + Material 3 design guidelines.
* **Concurrency**: Kotlin Coroutines + `StateFlow`. We replace live callbacks with reactive streams.
* **DI**: Hilt (Dependency Injection) to easily pass the `PlaybackController` or `MusicRepository` into any ViewModel without passing them manually through Compose constructors.
* **Animations**: `androidx.compose.animation` (Spring physics, Parallax slides) for 60/120fps fluid transitions.

## Example: Applying this to the iPod Feature
Right now, `IpodScreen.kt` and `IpodViewModel.kt` do a bit of everything: UI rendering, fetching from YouTube, and controlling the player.

**In the new architecture:**
1. `IpodScreen` listens to `IpodUiState` (e.g., current menu list, loading state).
2. The user spins the click wheel. The UI fires `viewModel.onEvent(OnWheelScrolled)`.
3. `IpodViewModel` calculates the new highlighted index and updates the `IpodUiState` instantly (UI responds immediately).
4. The user clicks play. The UI fires `viewModel.onEvent(OnPlaySong)`.
5. The `IpodViewModel` asks the `PlaybackController` (from `:core:playback`) to queue the song and start playback. The ViewModel doesn't know *how* ExoPlayer works, just that it told it to play.