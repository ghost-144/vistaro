# Catalog Sync

Catalog sync uses VidAPI's daily TMDB ID exports and enriches those IDs with
TMDB metadata. The exported files are streamed into Room in batches, so the
complete catalog is never held in memory. Movie and TV availability are stored
separately from TMDB metadata, and episodes are not synchronized as part of the
initial catalog.

The sync runs manually from Settings or approximately every 24 hours through
WorkManager when a network connection is available. WorkManager persists the
job across app restarts and retries retryable network failures. Interrupted ID
downloads do not mark unseen entries as removed; removed entries are marked
only after the corresponding ID file has been read completely.

Each run fetches metadata for at most 250 movies and 250 TV shows. This keeps a
large initial catalog sync bounded and resumable; uncached metadata remains
queued for later sync runs. Settings shows the latest successful snapshot,
available ID counts, current progress, pending metadata count, and a retry
action when a recoverable failure occurs.

TMDB credentials are read from the ignored root `secrets.properties` file. See
[`TMDB_SETUP.md`](TMDB_SETUP.md) for setup instructions.
