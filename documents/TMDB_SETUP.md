# TMDB setup

Vistaro uses TMDB's API Read Access Token for metadata requests. Keep the token in the ignored root `secrets.properties` file:

```properties
TMDB_ACCESS_TOKEN=your_api_read_access_token
```

Alternatively, set `TMDB_ACCESS_TOKEN` in the environment used to run Gradle. The value is added to the local debug build and is not stored in source files. Rebuild and reinstall the app after configuring it.

The app loads popular movies and TV shows, refreshes item details when a detail screen opens, and fetches a season's episodes when that season opens. Successful responses are cached in Room. Search remains local. If the token is missing or a request fails, the sample catalog remains available and the detail or episode screen provides a retry action.

TMDB requires the app to display this notice in an About or Credits section: “This product uses the TMDB API but is not endorsed or certified by TMDB.”
