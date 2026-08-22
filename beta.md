
## Version 1.2.5-beta (22/08/2026)

### Features

* **P2P:** Added support for P2P streaming (**Settings > Anime > TorrServer Settings**).
* **AniDB:** New anime source.
* **Torrentio:** New anime source for P2P streaming.

### Fixes

* **Metadata:** Fixed an issue causing missing episode metadata.
* **MangaK:** Fixed chapters not tracking.

### Enhancements

* **Player:** The initialization UI now displays artwork sourced from TheMovieDatabase.
* **Player:** Added a non-persistent P2P stats pill to display swarm metrics.

### Changes

* **Sources:**  Disabled Anikoto.
* **Skip Times (IntroDB):** Skip times are now fetched directly on the client side rather than being proxied.
* **Metadata:** Disabled Kitsu in favor of metadata sourced from TheMovieDatabase and TheTVDB (P2P).