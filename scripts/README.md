# Vistaro Catalog Ingestion Script

Standalone Python job to populate your MongoDB Atlas cluster with TMDB movies, TV shows, and seasons/episodes.

## Features
- **100% Schema-Compatible**: Produces exact documents required by Vistaro's Android app (`_id = "MOVIE:<id>"` / `"TV_SHOW:<id>"`).
- **Resumable Checkpoints (`.ingest_checkpoint.json`)**: If interrupted by `Ctrl+C` or network drops, it immediately saves state and picks up right where it left off on the next run.
- **Rate-Limited TMDB Client**: Token-bucket algorithm (default 25 req/sec) to prevent TMDB 429 rate limit errors.
- **Bulk Upserts (`UpdateOne` / `ReplaceOne`)**: Minimizes Atlas network round-trips and writes in batches.
- **Storage-Friendly for Atlas Free Tier (512 MB)**: Controls full episode detail downloads via `--episodes-for-top` so you never exceed the 512 MB ceiling.

---

## Setup & Prerequisites

1. Install requirements:
   ```bash
   pip install -r scripts/requirements.txt
   ```

2. Credentials are automatically read from `secrets.properties` in the project root:
   - `TMDB_API_KEY` or `TMDB_ACCESS_TOKEN`
   - `MONGODB_URI`

   *(You can also override them via environment variables `TMDB_API_KEY` and `MONGODB_URI`)*.

---

## Usage

### 1. Check Current Database Stats
Inspect current documents, movie/show counts, and Atlas disk usage:
```bash
python3 scripts/catalog_ingest.py --mode stats
```

### 2. Ingest Popular Content (Default)
Fetches popular movies and TV shows, with full episode data for the top shows:
```bash
python3 scripts/catalog_ingest.py --movie-pages 50 --tv-pages 50 --episodes-for-top 150
```

### 3. Quick Run (Small Batch)
```bash
python3 scripts/catalog_ingest.py --movie-pages 5 --tv-pages 5 --episodes-for-top 10
```

### 4. Interrupt and Resume
- Press `Ctrl+C` at any time while the script is running.
- The script gracefully commits the current batch and updates `scripts/.ingest_checkpoint.json`.
- When you run `python3 scripts/catalog_ingest.py` again, it will automatically detect the checkpoint and resume from the exact page and ID list.

### 5. Reset Checkpoint
To start fresh from page 1:
```bash
python3 scripts/catalog_ingest.py --mode reset
```

---

## CLI Options

| Argument | Default | Description |
|---|---|---|
| `--mode` | `popular` | `popular`, `all`, `stats`, or `reset` |
| `--movie-pages` | `25` | Number of movie pages to fetch (20 items/page) |
| `--tv-pages` | `25` | Number of TV show pages to fetch (20 items/page) |
| `--episodes-for-top` | `100` | Max number of top shows to download full season/episode details for |
| `--batch-size` | `50` | Number of operations per MongoDB bulk write |
| `--rps` | `25.0` | Requests per second to TMDB |
