# Local Streaming App — Implementation Plan

## 1. Goal

Build a lightweight personal streaming app for:
- Android phones
- Android TV

Android TV is the primary performance target.

The app runs locally on each device and has NO backend server.

External services:
- TMDB → metadata/catalog intelligence
- VidAPI → playback
- Room/SQLite → local catalog and user state

Core flow:

Internet
  ├── TMDB
  └── VidAPI
       ↓
Android App
  ├── Room local database
  ├── Online title search + local VidAPI availability filter
  ├── Phone UI
  ├── TV UI
  └── Playback WebView

---

## 2. Technology

Use:
- Kotlin
- Jetpack Compose
- Compose for TV
- Room / SQLite
- Retrofit
- OkHttp
- Kotlin Serialization
- Coil
- Navigation Compose
- DataStore
- WorkManager
- AndroidX WebKit
- Kotlin Coroutines

Prefer manual dependency wiring with an `AppContainer`.

Do NOT add unless explicitly justified:
- Hilt / Koin
- RxJava
- Firebase
- Realm
- Elasticsearch / OpenSearch
- Redis
- Backend/server
- Analytics/crash-reporting SDKs
- Large UI libraries

Keep dependencies and APK size minimal.

---

## 3. Architecture

Use one Android app/codebase for phone and TV.

Suggested structure:

app/
  core/
    common/
    model/
    network/
    database/
  data/
    tmdb/
    vidapi/
    catalog/
    favorites/
    watchhistory/
  domain/
    repository/
    usecase/
  ui/
    common/
    phone/
    tv/
  player/
    PlaybackProvider.kt
    VidApiPlaybackProvider.kt
    PlayerActivity.kt
  MainActivity.kt

Shared:
- Models
- Database
- Repositories
- Networking
- Business logic
- ViewModels

Separate only the presentation where phone/TV UX differs.

### UI Reference

Use `documents/Jet Stream Streaming UI Showcase.png` as a visual reference for:
- A restrained dark theme with a clear accent color.
- Artwork-led featured content and poster-based catalog rows.
- Familiar sections for Home, Movies, TV Shows, Search, Details, Episodes, Player, Favorites, Continue Watching, and Settings.

Treat the image as a visual direction, not as a pixel-perfect specification or a source of product data. It depicts wide desktop-style layouts; implement dedicated phone and Android TV layouts rather than scaling those screens directly. Use real metadata and artwork from the configured services, and keep labels and episode details consistent with the selected content.

### UI Behavior and Screen States

- Give each screen explicit loading, content, empty, offline, and recoverable-error states where applicable.
- Search requires an internet connection, shows clear loading, empty, connection, and recoverable-error states, and filters results to content in the last successfully synced VidAPI ID list.
- Catalog sync must expose progress, last successful sync, and a recoverable failure state in Settings.
- Details and episode screens must distinguish cached metadata from playback availability. Explain when playback cannot start and provide a retry path when appropriate.
- Keep TV focus visible and predictable. Focused controls/cards need a strong outline or equivalent treatment, and D-pad navigation must not depend on touch gestures, hover, or subtle focus changes.
- Use consistent typography, spacing, card proportions, and focus styling across screens while allowing phone and TV layouts to differ.

---

## 4. Core Design Rules

1. Room is the local source of truth for cached browsing, VidAPI availability, and user state. Online title search uses TMDB and filters against Room availability.
2. TMDB provides metadata.
3. VidAPI provides playback.
4. UI must never directly call TMDB or VidAPI.
5. Use repository/provider interfaces.
6. No backend.
7. No authentication.
8. No cloud database.
9. Each device maintains its own local database.
10. Never load the entire catalog into memory.

---

## 5. Database

Use Room/SQLite.

Initial entities:

- MovieEntity
- TvShowEntity
- EpisodeEntity
- GenreEntity
- FavoriteEntity
- WatchProgressEntity
- CatalogMetadataEntity

Movie fields:
- tmdbId
- imdbId
- title
- originalTitle
- overview
- releaseDate
- rating
- voteCount
- popularity
- posterPath
- backdropPath
- originalLanguage
- updatedAt

TV fields:
- tmdbId
- imdbId
- title
- originalTitle
- overview
- firstAirDate
- rating
- voteCount
- popularity
- posterPath
- backdropPath
- originalLanguage
- numberOfSeasons
- numberOfEpisodes
- updatedAt

Episode fields:
- tmdbId
- showTmdbId
- seasonNumber
- episodeNumber
- title
- overview
- airDate
- rating
- stillPath
- updatedAt

Favorite:
- contentId
- contentType
- createdAt

WatchProgress:
- contentId
- contentType
- seasonNumber
- episodeNumber
- positionMs
- durationMs
- updatedAt
- completed

Do not initially synchronize all episodes. Cache episodes when a TV show is opened.

---

## 6. Search

Search requires an internet connection. It uses TMDB's text search endpoints for movies and TV shows, then filters the returned TMDB IDs against the locally stored VidAPI availability snapshot. The full TMDB metadata catalog does not need to be downloaded before users can search it.

Search flow:
1. The user enters a movie or TV title.
2. After a short debounce or explicit submission, the search repository calls the matching TMDB search endpoint. Cancel outdated requests; do not send a request for every keystroke.
3. Query Room for the bounded set of result IDs and retain results whose VidAPI availability is `available = true` in the latest completed ID snapshot.
4. Display matching metadata returned by TMDB and cache those result records in Room. Fetch full details only when the user opens a result if the search response does not contain enough detail.
5. If there is no network, show that a connection is required and provide retry. Do not present the cached subset as complete search results.

Search:
- Movie title, including TMDB's original, translated, and alternate title matching.
- TV show title, including TMDB's original, translated, and alternate title matching.
- Bounded pages and result sets; never load the full catalog into memory.
- Only content present in the most recently completed VidAPI movie/TV ID snapshot.

SQLite FTS is not required for the online search flow. Room remains useful for the VidAPI availability filter and caching metadata returned by search.

---

## 7. Catalog Sync

Create `CatalogSyncManager`.

Responsibilities:
1. Obtain VidAPI available movie IDs.
2. Obtain VidAPI available TV IDs.
3. Compare with local IDs.
4. Identify new/removed content.
5. Fetch TMDB metadata for new/changed content.
6. Store metadata in Room.
7. Store the ID snapshot used to filter online search results. Search metadata is cached as users search; full-catalog metadata enrichment is not a prerequisite for search.

Initial sync:
- Movies
- TV shows

Do not sync all episodes initially.

When a user opens a TV show:
- Check cached seasons/episodes.
- Fetch from TMDB if missing/stale.
- Cache in Room.

Support:
- Manual sync from Settings.
- Automatic sync approximately every 24 hours using WorkManager.

Sync must be incremental/batched and must not load the complete catalog into memory.

---

## 8. TMDB

Create:
- `TmdbApi`
- `TmdbRepository`

TMDB provides:
- Movie metadata
- TV metadata
- Seasons
- Episodes
- Genres
- Images
- Ratings/popularity

UI must never depend directly on Retrofit/TMDB classes.

---

## 9. VidAPI

Create:

```kotlin
interface PlaybackProvider {
    suspend fun getMoviePlayback(
        tmdbId: Int,
        imdbId: String?
    ): PlaybackInfo

    suspend fun getEpisodePlayback(
        tmdbId: Int,
        showTmdbId: Int,
        season: Int,
        episode: Int
    ): PlaybackInfo
}
```

Implement `VidApiPlaybackProvider`.

Keep all VidAPI-specific URL/player logic behind this interface.

This allows replacing VidAPI later without rewriting UI.

---

## 10. Playback / WebView

Use WebView for VidAPI playback.

WebView must NOT exist during normal browsing.

Preferred flow:

Home
  → Details
  → Play
  → PlayerActivity
  → Create WebView
  → VidAPI playback
  → Back
  → Destroy WebView
  → Return to Details

Rules:
- One WebView maximum during playback.
- Never preload WebView.
- Never create hidden WebViews.
- Never keep WebView in a ViewModel/singleton.
- Never initialize WebView during app startup.
- Destroy it when PlayerActivity finishes.

Use AndroidX WebKit where appropriate.

---

## 11. Watch Progress

Consume VidAPI player progress events.

Do NOT write to Room for every event.

Preferred:
- Keep current progress in memory.
- Persist approximately every 15–30 seconds.
- Persist on pause.
- Persist on exit.
- Persist on completion.

Implement:
- Continue Watching
- Resume playback

Limit Continue Watching display to a small number, e.g. 10 items.

---

## 12. Home

Keep TV UI lightweight.

Suggested sections:
- Featured
- Continue Watching
- Popular Movies
- Popular TV Shows
- Recently Added
- Genres

Use lazy components:
- LazyColumn
- LazyRow
- LazyVerticalGrid

Do not load all sections/content simultaneously if unnecessary.

---

## 13. Images

Use Coil.

Use appropriately sized TMDB image variants rather than original images.

Rules:
- No manual bitmap retention.
- No huge image preloading.
- Avoid expensive transformations.
- Avoid blur-heavy UI.
- Load only images needed by the current screen.

---

## 14. Android TV

TV is the primary performance target.

Assume low-memory devices, including approximately 1 GB RAM.

Optimize for:
- Fast startup
- Smooth D-pad navigation
- Low memory
- Minimal recomposition
- Limited image decoding
- Minimal background work

Avoid:
- Animated/video backgrounds
- Heavy blur
- Lottie
- Large animations
- Excessive parallax
- Huge image buffers
- Persistent WebView

Primary controls:
- Up
- Down
- Left
- Right
- OK
- Back

The entire TV app must be usable using only a remote.

TV presentation requirements:
- Use a landscape, 10-foot layout with large readable type and controls sized for viewing from a distance.
- Make the focused item unmistakable, including on poster cards, tabs, buttons, season selectors, and player controls.
- Define predictable focus entry, restoration, and movement between navigation, content rows, and screen actions. Back must return to the prior screen and restore the prior focus when possible.
- Keep horizontal content rows and vertical page movement compatible with the remote; do not require precision pointing or scrolling gestures.
- Keep poster rows visually close to the reference direction, prioritizing readable metadata and remote navigation over fitting many cards on screen. The selected TV home layout has no hero banner.
- Use the attached Jet Stream image as the TV palette reference: charcoal background, dark neutral surfaces, soft white text, muted gray supporting text, and a clear pale focus outline.
- The top navigation has Home, Movies, TV Shows, Search, and Settings on one horizontal row.
- Home has exactly three content rows: Continue Watching, Latest Movies, and Latest TV Shows. Until watch progress is implemented, show an honest empty state instead of sample resume entries.
- Movies and TV Shows each start with three rows: Latest, Top, and Categories. A category opens its own page with Latest and Popular rows for that genre.
- Each horizontal media row shows at most 20 titles, followed by a View All tile. View All opens a vertically scrolling, paginated tile grid that loads more as the user approaches the end.
- Use the same media tile in horizontal rows and vertical grids: poster, title, release year, and a rating only when available. Identify the rating source; the current catalog provides TMDB vote averages, not IMDb ratings.

---

## 15. Phone

Reuse all business/data logic.

Phone presentation requirements:
- Keep the same charcoal palette, Home/Movies/TV Shows content rows, category pages, View All grids, and media tile information used by TV.
- Place Home, Movies, TV Shows, Search, and Settings in a touch-friendly bottom navigation bar instead of the TV top navigation.
- Keep poster sizes and vertical grids responsive to narrow portrait screens while preserving the shared poster/title/year/rating treatment.
- Preserve access to the same core features and local data as TV.

Screens:
- Home
- Search
- Movie Details
- TV Details
- Episode List
- Favorites
- Continue Watching
- Settings
- Player

Movie Details and TV Details are separate screen designs. Movie Details focuses on movie metadata and a single play action. TV Details presents show metadata and season/episode navigation; episode lists and playback selection belong to the TV flow. Share reusable visual components where useful without making the two screens ambiguous.

---

## 16. Navigation

Use Navigation Compose.

Suggested routes:

- home
- search
- movie/{tmdbId}
- tv/{tmdbId}
- tv/{tmdbId}/season/{season}
- favorites
- continueWatching
- settings

Playback should use `PlayerActivity` rather than keeping a heavy WebView inside the main navigation stack.

---

## 17. Settings

Initial settings:
- Sync Catalog
- Last Catalog Sync
- Clear Catalog Cache
- Clear Watch History
- Clear Favorites
- Preferred Playback Quality
- Autoplay
- About

Use DataStore for preferences.
Use Room for catalog/user state.

---

## 18. Offline/Cached Behavior

Cached browsing and details should use Room without internet. Search explicitly requires an internet connection and must explain that requirement when disconnected; cached search results are not represented as complete.

External playback requires network access.

Handle separately:
- Catalog available
- Playback unavailable

Do not crash if TMDB/VidAPI is unavailable.

---

## 19. Performance Requirements

Primary target: low-memory Android TV.

Rules:
- Never load full catalogs into memory.
- Query/filter/order at database level.
- Use lazy UI lists.
- Limit result sets.
- Avoid unnecessary recomposition.
- Avoid expensive image transformations.
- No background polling.
- No WebView outside playback.
- No heavy animations.

Suggested engineering targets:
- Cold start: <1.5 sec where practical.
- Normal browsing: aim for ~120–150 MB or lower where practical.
- APK: preferably <25 MB compressed.

Measure actual performance rather than optimizing blindly.

---

## 20. Release Optimization

Enable:
- R8
- Minification
- Resource shrinking

After MVP stabilization, add a Baseline Profile for:
- App startup
- Home
- D-pad navigation
- Search
- Movie details
- TV details
- Episode list
- Starting playback

---

## 21. Security

Do not commit TMDB API keys.

Use local Gradle configuration such as:
- `secrets.properties` (ignored by Git)

Do not introduce a backend merely to hide a client-side API key.

No sensitive data should be logged.

---

## 22. Testing

Unit-test important logic:
- Catalog synchronization
- ID matching
- Search
- Watch progress
- Favorites
- Playback URL generation
- Repository behavior

Do not pursue unnecessary 100% coverage.

Test on:
- Android phone
- Android TV emulator
- Real Android TV where possible
- Lowest-memory target device available

Important tests:
- Cold start
- D-pad navigation
- Search
- Movie details
- TV details
- Season/episode selection
- Playback
- Back from playback
- Repeated playback
- Continue Watching
- Favorites
- Catalog sync
- Network loss/restoration
- Background/foreground
- Screen off/on
- Memory growth after repeated playback
- Empty search results, network loss during search, and retry
- Catalog sync progress, interruption, and recoverable failure
- Unavailable playback and retry behavior
- TV focus visibility, D-pad traversal, and focus restoration after Back
- Phone layouts and touch targets at narrow screen sizes

---

# 23. Implementation Phases

### Status Snapshot — 2026-10-09

- **Phase 0 — Project Setup: Foundation complete.** Debug build and phone installation are verified. The app now installs and launches on the `Television_4K` Android TV emulator.
- **Phase 1 — Architecture: Complete.** Shared models, Room, DAOs, repositories, dependency wiring, and navigation are in place.
- **Phase 2 — TV UI: Implemented; emulator validation underway.** The top row now includes Home, Movies, TV Shows, Search, and Settings. Earlier TV testing verified the palette and tiles, D-pad entry into media rows, category detail navigation, View All grid paging, Back navigation, and opening a movie detail page on `Television_4K`. The latest top-row change still needs TV emulator revalidation. Full remote-only acceptance also needs coverage of Popular category rows and TV/episode detail actions.
- **Phase 3 — TMDB: Implemented and verified on phone and TV emulator.** Live movie/TV rows and poster art loaded from TMDB on `Television_4K`; View All loaded further items while scrolling, and a movie detail page opened with TMDB metadata. TV details and episode flows still need TV-specific validation.
- **Phase 4 — Catalog Sync: Implemented and phone-verified.** VidAPI movie/TV IDs are streamed into Room; manual/daily WorkManager sync, status, and retry are present. The last verified snapshot stored 91,993 movie IDs and 20,701 TV IDs; metadata enrichment is deliberately batched, with 111,615 items remaining after the last run.
- **Phase 5 — Online Search: Implemented and phone-verified.** Search calls TMDB movie and TV endpoints after a 350 ms debounce, cancels obsolete requests, filters results using a durable membership table for the most recently completed VidAPI snapshot, caches returned metadata in Room, and exposes retry plus up to three pages. A connected-phone search for “Gnomes” returned matching movie and TV results and opened TV details. Search requires a completed VidAPI sync and internet access; TV-specific validation remains.
- **Phase 6 — VidAPI Playback: Not started.** Current playback routes are placeholders.
- **Phase 7 — Watch Progress: Not started.** Database foundation exists, but player event capture and resume flow are not implemented.
- **Phase 8 — Favorites: Not started.** Database foundation exists; current UI state is not yet persisted through the favorites repository.
- **Phase 9 — Phone UI: Shared design and key fixes phone-verified.** The phone entry point uses the same browse screens and charcoal palette as TV with bottom navigation. Safe drawing insets, narrower horizontal margins, first-item carousel positioning, touch navigation into details, and consistent category card heights are implemented. The latest APK opened on the connected phone; status-bar spacing, bottom navigation, search results, and result-to-details navigation were verified. A complete screen-by-screen regression remains part of Phase 11.
- **Phase 10 — Performance: Not started.** No planned profile, release shrinking, or baseline profile work is complete.
- **Phase 11 — Final Testing: In progress.** Initial Android TV emulator installation, launch, browsing, D-pad navigation, and paginated browsing were exercised. Full phone/TV regression, playback, memory, and low-memory-device checks remain.

## Phase 0 — Project Setup

Create:
- Kotlin project
- Compose
- TV support
- Phone support
- Room foundation
- Networking foundation
- DataStore
- WorkManager
- Basic navigation
- AppContainer

Acceptance:
- Debug APK builds.
- Installs on phone.
- Installs on TV.
- Home screen opens.
- Establish shared theme tokens and separate phone/TV presentation entry points.

Do not implement external APIs yet.

---

## Phase 1 — Architecture

Implement:
- Models
- Room database
- DAOs
- Repository interfaces
- AppContainer
- Navigation
- Device detection

Use fake data.

Acceptance:
- Home
- Search
- Movie Details
- TV Details
- Settings

all navigate successfully.
- Movie and TV Details use distinct layouts.
- Core screens have explicit loading, empty, offline, and recoverable-error states using fake data/state fixtures.

---

## Phase 2 — TV UI

Build TV UI first:
- Home
- Search
- Movie Details
- TV Details
- Season/Episode screen
- Favorites
- Continue Watching
- Settings

Use fake data.

Acceptance:
Entire app can be navigated with a TV remote.
- Focus remains visible and predictable across navigation, cards, tabs, season selection, and actions.
- Back returns to the previous screen and restores focus where possible.
- The top bar contains Home, Movies, and TV Shows tabs; Search and Settings sit at the top left with Search above Settings.
- Home presents Continue Watching, Latest Movies, and Latest TV Shows rows.
- Movies and TV Shows each present Latest, Top, and Categories rows. A selected category opens a page with Latest and Popular rows.
- Horizontal media rows contain no more than 20 media items followed by View All. View All opens a vertically paginated grid with load-more behavior.
- Horizontal rows and vertical grids share the same poster/title/year/rating tile and focus treatment. Ratings are labelled by source; do not present TMDB vote averages as IMDb ratings.
- Use the attached reference's dark charcoal palette, restrained surfaces, muted text, and clearly visible light focus border.

---

## Phase 3 — TMDB

Implement:
- TMDB API
- Movie metadata
- TV metadata
- Seasons
- Episodes
- Images
- Room caching

Acceptance:
- Real movie metadata appears.
- Real TV metadata appears.
- Images load.
- Cached content works without network.

---

## Phase 4 — Catalog Sync

Implement `CatalogSyncManager`.

Start with:
- Movies
- TV shows

Add:
- Manual sync
- 24-hour WorkManager sync

Acceptance:
- Sync works.
- Sync survives interruption.
- Catalog is stored locally.
- Cached catalog remains browsable without internet.
- Settings shows sync progress, last successful sync, and a recoverable failure with retry.

---

## Phase 5 — Online Search

Implement a search repository that queries TMDB `/3/search/movie` or `/3/search/tv` for titles after a short debounce or explicit submission, filters the bounded results against the locally stored VidAPI availability snapshot, and caches the displayed metadata in Room. Do not require full-catalog TMDB enrichment or SQLite FTS for search.

Acceptance:
- Search finds movies and TV shows online even when their full details were not previously cached.
- Results are restricted to TMDB IDs in the latest completed VidAPI availability snapshot.
- Search is debounced, cancels obsolete requests, and uses bounded pages/results.
- Search results are cached and open into details; missing full details can be fetched on demand.
- Empty results, no connection, request failures, and retry are clear.
- Search explicitly requires internet; offline search is not an acceptance requirement.

---

## Phase 6 — VidAPI Playback

Implement:
- PlaybackProvider
- VidApiPlaybackProvider
- PlayerActivity
- WebView

Acceptance:
- Movie playback works.
- TV episode playback works.
- Back navigation works.
- WebView is destroyed after playback.
- Repeated playback does not continuously increase memory usage.
- Unavailable playback produces a clear message and retry path.

---

## Phase 7 — Watch Progress

Implement:
- VidAPI player events
- WatchProgressRepository
- Continue Watching
- Resume playback

Acceptance:
Start → watch → exit → reopen → resume from previous position.

---

## Phase 8 — Favorites

Implement:
- FavoriteRepository
- Favorites screen
- Add/remove favorite

Acceptance:
Favorites survive app restart.

---

## Phase 9 — Phone UI

Implement phone-specific UI while reusing:
- Database
- Repositories
- ViewModels
- Networking
- Playback

Acceptance:
Phone UI works without changing TV behavior.

---

## Phase 10 — Performance

Profile:
- Memory
- CPU
- Startup
- Recomposition
- Database queries
- Image loading
- WebView lifecycle
- APK size

Then implement:
- R8
- Resource shrinking
- Baseline Profile

---

## Phase 11 — Final Testing

Test on:
- Android phone
- Android TV emulator
- Real Android TV
- Lowest-memory available device

Perform repeated playback/memory tests.

---

# 24. Codex Working Rules

1. Implement one phase at a time.
2. Do not implement the entire plan in one change.
3. Inspect the existing repository before changing it.
4. Before changing architecture, explain why.
5. Do not add dependencies without justification.
6. Prefer standard AndroidX/Kotlin APIs.
7. Do not introduce a backend.
8. Do not introduce authentication.
9. Do not introduce Firebase.
10. Do not introduce Hilt/Koin without explicit approval.
11. Do not introduce Elasticsearch/OpenSearch.
12. Do not load entire catalogs into memory.
13. Do not call TMDB on every search keystroke.
14. Do not keep WebView alive outside playback.
15. Do not build TV UI as a stretched phone UI.
16. TV must support D-pad-only navigation.
17. Prefer simple code over abstraction-heavy code.
18. Avoid premature generalization.
19. Run relevant tests after meaningful changes.
20. Preserve existing functionality when modifying code.
21. Do not modify unrelated files.
22. Report deviations from this plan.
23. Before adding a library, verify whether existing dependencies already provide the capability.
24. Use the UI reference for visual direction only; do not copy its desktop layout onto TV or phone.
25. Keep movie and TV details distinct and verify that displayed metadata matches the selected item.
26. Implement and review loading, empty, offline, and recoverable-error states alongside each relevant feature.

---

# 25. MVP Definition of Done

The MVP is complete when:

- [ ] App builds successfully
- [ ] Android phone works
- [ ] Android TV works
- [ ] TV is fully controllable with D-pad
- [ ] Catalog sync works
- [ ] Movies appear
- [ ] TV shows appear
- [ ] Online TMDB search works and filters to VidAPI-available titles
- [ ] Movie details work
- [ ] TV details work
- [ ] Seasons/episodes work
- [ ] VidAPI movie playback works
- [ ] VidAPI TV playback works
- [ ] WebView lifecycle is safe
- [ ] Continue Watching works
- [ ] Resume playback works
- [ ] Favorites work
- [ ] Catalog survives restart
- [ ] Cached browsing works without internet
- [ ] Search clearly requires internet and handles connection loss
- [ ] External API errors are handled gracefully
- [ ] No backend exists
- [ ] No Firebase exists
- [ ] Release build works with R8
- [ ] No obvious memory growth after repeated playback
- [ ] TV performance is acceptable on low-memory hardware

---

# 26. Explicitly Out of Scope for MVP

Do NOT implement unless explicitly requested later:

- User accounts
- Cloud synchronization
- Multiple profiles
- Parental controls
- Recommendation engine
- Social features
- Comments
- Ratings system
- Downloads
- Offline video playback
- Chromecast
- AirPlay
- Multi-device synchronization
- Notifications
- Analytics
- Advertising
- Subscription/payment system
- External player integrations
- P2P/torrent functionality

The MVP goal is:

Fast local catalog
+ Fast online title search filtered by local availability
+ Lightweight TV UI
+ Lightweight phone UI
+ VidAPI playback
+ Continue Watching
+ Favorites
