## [Unreleased]

## [4.1.6] - 2026-06-28

### Added
- **Sortable Release Name + sort direction:** The catalog sort menu now offers a **Release Name**
  option and supports both ascending and descending order for every field. Re-tapping the active
  field flips its direction while a ▲/▼ arrow in the menu shows the current sort direction.
  Each field starts in its natural direction (A→Z for names, newest/largest/most-popular first
  otherwise).
- **Sorted value visible inline:** The active sort key is now shown directly on each game row —
  Last Updated date, popularity, or release name appear next to the version/size line in the
  accent colour, so the sorted field is visible without expanding the card.

### Fixed
- **Game list reshuffling while scrolling (#54):** Under the **Size** sort, the catalog list
  kept reordering as you scrolled because game sizes are fetched lazily as games come into view,
  and each newly resolved size re-triggered a full re-sort that moved items around. The sorted
  order is now frozen against transient updates (lazily fetched sizes, install/queue progress)
  and is only recomputed when the structural sort inputs change (sort mode, search query, filter,
  or the set of games shown). Per-item content such as the size label still updates live; only
  the ordering is held stable.
- **Catalog sync failure on large catalogs (#49):** On devices running Android 11 or earlier
  (SQLite < 3.32), pruning games removed from the server used a single `NOT IN` query that hit
  SQLite's 999-variable limit once the catalog grew large enough. The entire sync would fail
  silently, leaving the local database out of date. Stale games are now deleted in chunked
  batches computed in Kotlin, avoiding the limit entirely.
- **Stuck "update available" banner after catalog sync (#50):** Two silent failure paths in
  `CatalogUpdateWorker` could leave the update banner visible indefinitely. If the downloaded
  catalog file failed to move to its final location, the worker reported success without saving
  the new metadata; if the extraction produced empty content, it also fell through to success.
  Both cases now return a retryable failure so WorkManager retries with back-off.
- **Incorrect supporter tier in periodic stats (#48):** The periodic `StatsCollectionWorker`
  was forwarding unknown tier values verbatim to the stats endpoint instead of normalising them
  to `standard`/`supporter`/`lucky`, unlike the on-demand and debounce workers. It now uses
  the shared `resolveTier()` helper so all stats paths agree.

## [4.1.5] - 2026-06-27

### Added
- **Trailer player (Story 11.2 / 11.3):** Each game detail view now shows a trailer card.
  Games with a resolved YouTube video display a 16:9 thumbnail with a play overlay; games
  with only a search-link fallback display a generic "watch trailer" card. Both open the
  video externally via `ACTION_VIEW` (system YouTube app or browser) — no in-app IFrame,
  which is incompatible with YouTube's 2025 embedder-identity enforcement.
- **MetaMetadata hero image:** The game detail screen now displays the MetaMetadata
  thumbnail as a full-width hero image above the game info.
- **Room v7 migration:** `GameEntity` gains a nullable `trailerUrl` column. Trailer URLs
  are populated at sync time from `meta.7z` (primary) or fetched on-demand from the
  per-game directory listing (fallback). Existing rows migrate without data loss.

## [4.1.4] - 2026-06-18

### Fixed
- **Stats worker:** The periodic `StatsCollectionWorker` (daily WorkManager task) was reporting all installed
  packages on the device — including system apps and test packages — instead of filtering to VRHub catalog
  games only. It now mirrors `maybeCollectStats()` by intersecting `gameDao().getAllGamesList()` with
  installed packages. `isFavorite` is also correctly read from the catalog entity instead of being
  hardcoded to `false`.

## [4.1.3] - 2026-06-17

### Added
- **Stats debounce (Story 5.2):** Rapid favorite toggles now coalesce into a single stats
  report sent 5 minutes after the last toggle, instead of firing one POST per toggle.
  Uses WorkManager `OneTimeWorkRequest` with `REPLACE` policy — each toggle resets the
  5-minute timer. The consolidated POST carries the real `isFavorite` values read from
  Room at fire time.
- **Game name in stats payload (Story 8.6):** The human-readable game name is now
  transmitted alongside the package name in `/stats/collect`, allowing the server to
  display it directly in Discord webhooks without resolving names from the catalogue.

## [4.1.2] - 2026-06-15

### Fixed
- **New games not appearing after server rescan:** When the server catalog changed (new
  game added), `syncCatalog` correctly detected the new ETag but reused the local
  `catalog_meta_cache.7z` if it was less than 1 hour old, then saved the new ETag without
  loading the new content. Subsequent refreshes saw a matching ETag and skipped entirely,
  permanently hiding new games. The local-file freshness optimisation is now bypassed
  whenever the server reports new content.
- **Deleted games not removed from catalog:** Games removed from the server were never
  deleted from the local Room database because `insertGames` is an upsert. After each
  successful sync, games whose `releaseName` is absent from the new server catalog are
  now deleted from the local database so the catalog stays in sync without requiring an
  app reset.

## [4.1.1] - 2026-05-03

### Added
- ProGuard rules to fix R8 full mode compatibility with Retrofit generic types

### Fixed
- **Monetization API:** Fixed "Response must include generic type" error on Become Supporter and Restore Purchase screens when R8 full mode is enabled 

## [4.1.0] - 2026-05-01

### âœ¨ New Features
- **VRHub Monetization System:** New supporter tiers and email-based purchase validation:
  - Email initiation flow with magic link via Ko-fi
  - Supporter tier (gold badge) and Lucky tier (purple badge)
  - Restore purchase flow with saved email validation
  - Debug monetization panel for testing backend endpoints
  - Build flavors: dev (.debug suffix) + prod flavors

### ðŸ”§ Fixes
- **Test Migration:** Migrated unit tests to use correct package structure
- **CI Workflow:** Fixed CI workflow to use explicit dev flavor task names
- **Instrumented Tests:** Made instrumented tests optional due to Android emulator instability on GitHub Actions

## [4.0.0] - 2026-04-28

### ðŸš€ Improvements
- **App Renamed to VRHub:** Rebranded the entire application with new package name `com.vrhub`
- **Build System:** Streamlined workflows and catalog for better maintainability

### âœ¨ New Features
- **Server Configuration:** Complete new settings UI to configure your own game server:
  - JSON URL mode (provide a URL to your server config file)
  - Manual mode (enter server details key by key)
  - Test button to validate your configuration before saving
  - Settings are saved and persist between app launches

### ðŸ”§ Fixes
- **Better Password Handling:** Improved validation of archive passwords
- **Game List Compatibility:** Server can now serve game list as `GameList.txt`
- **Build Cleanup:** Removed unnecessary files from the project

## [3.0.0] - 2026-02-17

### âœ¨ New Features
- **Secure Update Gateway:** Implemented update system for private distribution.
- **HMAC-SHA256 Request Signing:** Added request signing for secure update validation to prevent unauthorized updates.
- **Automated APK Deployment:** CI/CD automatically deploys new APKs on every release.
- **Release Candidate Support:** Added support for pre-release versioning (RC) and automated GitHub release creation.
- **Fast Track Local Install:** Games that are already downloaded can be installed instantly without re-downloading.
- **Shelving Ready-to-Install Games:** Downloaded games can be "shelved" (moved to long-term storage) and later restored for installation.
- **Local Installs UI:** New interface to manage locally installed games that aren't in the catalog.

### ðŸš€ Improvements
- **Fail-Fast Validation:** Added validation to ensure tag/version inputs match project configuration.
- **Clean Build Process:** Old APK files are automatically cleaned up before deploying new versions.
- **CI/CD Reliability:** Improved configuration and secrets management for more reliable deployments.

### ðŸ”§ Technical Notes
- **AGP 9.0.0 Upgrade Rejected:** Android Studio recommended upgrading to AGP 9.0.0, but this caused build failures due to Room 2.6.1 incompatibility with Kotlin 2.2.x. Reverted to AGP 8.13.2 + Kotlin 1.9.22. Future upgrade will require Room 2.8.x with KSP.

## [2.4.0] - 2026-01-05

### âœ¨ New Features
- **Advanced Sorting:** Sort games by Name (A-Z/Z-A), Size, Last Updated, and Popularity to find exactly what you want.
- **"New" Filter:** A new filter tab to quickly see games added since your last visit.
- **Popularity Tracking:** Games now have popularity data to help you find trending titles.
- **Favorites System:** You can now mark games as favorites! They will appear with a gold border and can be easily accessed via a new "Favorites" filter tab.
- **Special Install Support:** Added support for games with custom installation scripts (e.g., ports like Quake3Quest) via `install.txt` parsing to handle complex data placement.
- **Battery Optimization:** Integrated a check to request ignoring battery optimizations, preventing the system from killing the app during long downloads.

### ðŸš€ Improvements
- **Smarter Space Check:** Improved pre-flight storage validation with better estimation for 7z archives (accounting for extraction overhead) and correct external storage path checking.
- **Setup UI:** Enhanced the permission setup screen with better scrolling and clarity for a smoother onboarding experience.
- **Error Handling:** Better reporting for storage errors during installation to alert users immediately.

## [2.3.0] - 2026-01-01

### âœ¨ New Features
- **Cache Management:** Added a "Clear Download Cache" option in settings to free up storage space.
- **Smart Cleaning:** The app now automatically verifies installed games and cleans up temporary installation files to save space.

### ðŸš€ Improvements
- **Enhanced Installation Logic:** Significant improvements to how the app handles complex game file structures (nested folders, split OBBs).
- **Download Verification:** Better detection of already downloaded files to prevent unnecessary redownloads.
- **Recursive Parsing:** Added support for downloading games with deep folder structures from the catalog.

### ðŸ”§ Fixes
- **OBB Placement:** Fixed issues where OBB files in subfolders weren't being placed correctly.

## [2.2.1] - 2025-12-30

### ðŸ”§ Fixes
- **Game Status Refresh:** Fixed an issue where game statuses (downloaded/installed) were not automatically updating after a catalog sync.
- **App Update UI:** Fixed navigation and layout issues during mandatory app updates to ensure a smoother experience.

## [2.2.0] - 2025-12-30

### âœ¨ New Features
- **Install Queue & Manager:** Implemented a background installation queue with pause/resume capabilities, task promotion, and cancellable operations.
- **Game Metadata:** Added support for game descriptions and screenshots with an expandable UI.
- **File Management:** Added ability to delete downloaded game files with a confirmation dialog.
- **Smart Features:** Smart install and download caching, better game identification, and catalog deduplication.
- **UI/UX Overhaul:** Major interface updates, optimizations for large screens, and unified progress display.
- **Filtering:** Added status filtering and visual indicators for downloaded games.
- **Setup Experience:** Unified setup layout with immersive overlays for updates and permissions.

### ðŸš€ Improvements
- **Performance:** Reduced memory usage and optimized for large screens.
- **Infrastructure:** Windows compatibility for build tools (Makefile) and release signing configuration.
- **User Feedback:** Added snackbar messages for better user feedback.

## [2.1.1] - 2025-12-28

### ðŸ”§ Fixes
- **Installation Resume:** Fixed a bug where stopping the installation during the extraction phase would prevent it from resuming correctly. It now remembers where it left off. (#11)
- **Storage Check:** The app now checks if you have enough space before starting a download to prevent errors.

## [2.1.0] - 2025-12-27

### âœ¨ New Features
- **Automatic Updates:** The app now updates itself easily without needing a computer.
- **See Game Sizes:** You can now see how big a game is before downloading it.
- **Download Options:** You can choose to "Download Only" (without installing) or "Keep Files" after installation to save them for later.
- **Settings Menu:** A new menu to configure your preferences (like keeping downloaded files).
- **Resume Downloads:** If your internet cuts out, downloads now pick up right where they left off.

### ðŸš€ Improvements
- **Clearer Status:** Changed "Checking for updates" to "Syncing catalog" so you know when it's just refreshing the game list.
- **Background Installation:** Game installations now continue even if you leave the app.
- **Faster Startup:** The app loads much faster and feels smoother to use.
- **Better Battery Life:** The app now pauses background tasks when you're not using it to save resources.
- **Easier Setup:** A simple guide helps you set up permissions on the first run.
- **Smarter Sorting:** Games are organized better, making them easier to find.
- **Visual Refresh:** Updated launcher icon and background colors for a better look.

### ðŸ”§ Fixes
- **Interaction Lock:** Prevents clicking buttons while an app update is pending to avoid conflicts.
- **Installation:** Made game installations more reliable (especially for large games) and added checks for app visibility before launching installers.
- **Package Refresh:** Improved the detection of installed packages on startup.
- **Update Flow:** Allowed manual refresh and permission checks during the update process.
- **General:** Various small bug fixes and performance tweaks.

## [2.0.0] - 2025-12-22

### Changed
- **Complete Rebuild:** The app has been entirely rewritten as a native Android application for better performance and stability (goodbye Unity!).
- **New Interface:** A completely new, modern, and cleaner user interface.

### Added
- **Game Management:** You can now uninstall games directly from the app.
- **Smart Updates:** The app now automatically detects installed games and shows you when a new version is available.
- **Improved Downloads:** Better handling of downloads and installations.

## [1.0.0] - 2025-12-21

### Added
- Standalone Meta Quest application for native sideloading.
