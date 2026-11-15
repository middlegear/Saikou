## Version 1.3.1-Stable (17/11/2026)

### Features
* **Anime:** Add KAA source.
* **P2P:** Added full configuration options for Torrentio streams. Customize providers, excluded qualities, languages, and sorting preferences from **Settings ➔ Anime ➔ TorrServer Settings**.
* **P2P:** Added support for P2P streaming (**Settings > Anime > TorrServer Settings**).
* **P2P:** New anime source for P2P streaming.
* **Player:** MPVLib — implement a new player library based on MPV (ExoPlayer is disabled).
* **Timestamps:** Added **IntroDBOrg** as a provider for timestamps (disabled by default; enable it in Player Settings).
* **P2P:** Added support for Debrid via Torrentio (**Settings → Accounts → Debrid Service**).

### Enhancements
* **Player:** Add chapter parsing (only on supported containers).
* **MPV:** Enabled TLS stream verification.
* **P2P:** Add more providers.
* **P2P:** Improve source availability.
* **Caching:** Cache queries for performance and reduce chances of rate limits.
* **Player:** Add missing player settings to change behavior.
* **Manga:** Auto search improvements with support to save entries.
* **Player:** Initialization UI now displays artwork sourced from TheMovieDatabase.
* **Player:** Added a non-persistent P2P stats pill to display swarm metrics.
* **Player:** Separated swipe and double-tap gestures.
* **Player:** Minor UI refinements and cleanup.
* **Search:** Improve auto search for manga providers.

### Fixes
* **Player States:** Fix player state tracking and transitions.
* **RPC:** Make it more resilient.
* **Miscellaneous:** Various changes and performance improvements.
* **Player:** Missing video quality tracks for multi-quality HLS files.
* **P2P:** Ensure P2P server starts on magnet detection.
* **Notifications (Anime):** Fixed notifications.
* **Anime:** Minor fix for serialization errors.
* **MPV:** Make shutdown/reinit self-healing.
* **TorrServer:** Update libs and fix server port orphaning.
* **Player:** Fix crash when auto-playing the next episode.
* **Player:** Fix stutter when switching subtitles.
* **Player:** Fix dropped frames when switching audio/subtitle tracks.
* **Player:** Stop demuxer network activity on background and soft-recover on resume.
* **MPV:** Show actual audio channel.
* **UI:** Minor fix for dialogs which were non-dismissable.
* **Metadata:** Fixed an issue causing missing episode metadata.
* **MangaK:** Fixed chapters not tracking.
* **Anizone:** Fixed search errors, missing sources and incomplete episode counts.
* **Player:** Fixed the timestamp button not appearing even when skip times were available.
* **Player:** Added a fallback to AniSkip when the alternative timestamp provider has no data.
* **Anime:** Fixes forbidden errors in providers. Older builds are deprecated.
* **Player:** Headset and Bluetooth next/previous now change episodes.
* **Player:** Skip-time fetching is skipped when a file has more than one chapter.


### Changes
* **MPV:** Update deps to fix macroblocking.
* **Settings:** Rework settings UI.
* **Player:** Fix UI and other stuff can't remember.
* **Skip Times (IntroDB):** Skip times are now fetched directly on the client side rather than being proxied.
* **Metadata:** Disabled Kitsu in favor of metadata sourced from TheMovieDatabase and TheTVDB (P2P).


### Deps
* **MPV & FFmpeg:** Bump versions with cURL support.

### Migration
* **IntroDB:** Migrated skip time provider from v3 to v4 API.