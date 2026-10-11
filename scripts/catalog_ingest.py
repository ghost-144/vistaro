#!/usr/bin/env python3
"""
Vistaro Catalog Ingestion Script
================================
Fetches movies, TV shows, and seasons/episodes from TMDB and upserts them
directly into MongoDB Atlas, matching Vistaro's Android app schema.

Features:
- Resumable checkpoints (scripts/.ingest_checkpoint.json)
- Graceful interrupt handling (Ctrl+C saves state immediately)
- Rate-limited TMDB client (token bucket, default 25 req/sec)
- Bulk MongoDB upserts for maximum throughput and minimal Atlas RU consumption
- Controlled storage sizing for Atlas Free Tier (512 MB limit)
"""

import argparse
import json
import logging
import os
import signal
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

import requests
from pymongo import MongoClient, ReplaceOne, UpdateOne

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("vistaro-ingest")

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
CHECKPOINT_FILE = SCRIPT_DIR / ".ingest_checkpoint.json"
SECRETS_FILE = PROJECT_DIR / "secrets.properties"


def load_secrets() -> Dict[str, str]:
    """Load secrets from secrets.properties and environment variables."""
    secrets = {}
    if SECRETS_FILE.exists():
        with open(SECRETS_FILE, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, val = line.split("=", 1)
                    secrets[key.strip()] = val.strip()

    # Allow environment variables to override
    if "MONGODB_URI" in os.environ:
        secrets["MONGODB_URI"] = os.environ["MONGODB_URI"]
    if "TMDB_API_KEY" in os.environ:
        secrets["TMDB_API_KEY"] = os.environ["TMDB_API_KEY"]
    if "TMDB_ACCESS_TOKEN" in os.environ:
        secrets["TMDB_ACCESS_TOKEN"] = os.environ["TMDB_ACCESS_TOKEN"]

    return secrets


class CheckpointManager:
    """Manages resumable checkpoint state on disk."""

    def __init__(self, file_path: Path):
        self.file_path = file_path
        self.state: Dict[str, Any] = self._load()

    def _load(self) -> Dict[str, Any]:
        if self.file_path.exists():
            try:
                with open(self.file_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    logger.info("Loaded existing checkpoint from %s", self.file_path.name)
                    return data
            except Exception as e:
                logger.warning("Could not read checkpoint file (%s), starting fresh.", e)
        return {
            "movie_page": 1,
            "tv_page": 1,
            "processed_movie_ids": [],
            "processed_tv_ids": [],
            "episodes_processed_tv_ids": [],
            "total_movies_saved": 0,
            "total_tv_saved": 0,
            "last_updated": datetime.now().isoformat(),
        }

    def save(self):
        """Atomically save checkpoint to disk."""
        self.state["last_updated"] = datetime.now().isoformat()
        temp_file = self.file_path.with_suffix(".tmp")
        try:
            with open(temp_file, "w", encoding="utf-8") as f:
                json.dump(self.state, f, indent=2)
            temp_file.replace(self.file_path)
        except Exception as e:
            logger.error("Failed to save checkpoint: %s", e)

    def reset(self):
        """Reset checkpoint to empty state."""
        if self.file_path.exists():
            self.file_path.unlink()
        self.state = self._load()
        logger.info("Checkpoint state reset.")


class RateLimiter:
    """Token bucket rate limiter to stay within TMDB API limits."""

    def __init__(self, requests_per_second: float = 25.0):
        self.rate = requests_per_second
        self.tokens = requests_per_second
        self.last_check = time.monotonic()

    def wait(self):
        now = time.monotonic()
        elapsed = now - self.last_check
        self.last_check = now
        self.tokens = min(self.rate, self.tokens + elapsed * self.rate)
        if self.tokens < 1.0:
            sleep_needed = (1.0 - self.tokens) / self.rate
            time.sleep(sleep_needed)
            self.tokens = 0.0
        else:
            self.tokens -= 1.0


class TmdbClient:
    """TMDB API client with rate limiting and retry handling."""

    BASE_URL = "https://api.themoviedb.org/3"

    def __init__(self, api_key: str, access_token: Optional[str] = None, rps: float = 25.0):
        self.api_key = api_key
        self.access_token = access_token
        self.session = requests.Session()
        self.rate_limiter = RateLimiter(rps)
        if access_token:
            self.session.headers.update({"Authorization": f"Bearer {access_token}"})

    def _get(self, endpoint: str, params: Optional[Dict[str, Any]] = None, retries: int = 4) -> Optional[Dict[str, Any]]:
        url = f"{self.BASE_URL}{endpoint}"
        query = {"api_key": self.api_key}
        if params:
            query.update(params)

        for attempt in range(retries):
            self.rate_limiter.wait()
            try:
                resp = self.session.get(url, params=query, timeout=12)
                if resp.status_code == 200:
                    return resp.json()
                elif resp.status_code == 429:
                    retry_after = int(resp.headers.get("Retry-After", 2))
                    logger.warning("TMDB 429 Rate limited. Backing off for %d seconds...", retry_after)
                    time.sleep(retry_after)
                elif resp.status_code == 404:
                    return None
                else:
                    logger.warning("TMDB HTTP %d on %s: %s (attempt %d/%d)", resp.status_code, endpoint, resp.text[:100], attempt + 1, retries)
                    time.sleep(1.0 * (attempt + 1))
            except requests.RequestException as e:
                logger.warning("TMDB request failed (%s): %s (attempt %d/%d)", endpoint, e, attempt + 1, retries)
                time.sleep(1.5 * (attempt + 1))

        logger.error("Exhausted retries fetching %s", endpoint)
        return None

    def get_popular_movies(self, page: int = 1) -> Optional[Dict[str, Any]]:
        return self._get("/movie/popular", {"page": page})

    def get_top_rated_movies(self, page: int = 1) -> Optional[Dict[str, Any]]:
        return self._get("/movie/top_rated", {"page": page})

    def get_popular_tv(self, page: int = 1) -> Optional[Dict[str, Any]]:
        return self._get("/tv/popular", {"page": page})

    def get_top_rated_tv(self, page: int = 1) -> Optional[Dict[str, Any]]:
        return self._get("/tv/top_rated", {"page": page})

    def get_movie_details(self, tmdb_id: int) -> Optional[Dict[str, Any]]:
        return self._get(f"/movie/{tmdb_id}", {"append_to_response": "external_ids"})

    def get_tv_details(self, tmdb_id: int) -> Optional[Dict[str, Any]]:
        return self._get(f"/tv/{tmdb_id}", {"append_to_response": "external_ids"})

    def get_season_details(self, tmdb_id: int, season_number: int) -> Optional[Dict[str, Any]]:
        return self._get(f"/tv/{tmdb_id}/season/{season_number}")


class CatalogIngestJob:
    """Ingestion orchestrator that maps TMDB data to MongoDB Atlas."""

    def __init__(
        self,
        mongo_uri: str,
        tmdb_client: TmdbClient,
        checkpoint: CheckpointManager,
        batch_size: int = 100,
        db_name: str = "vistaro",
    ):
        self.mongo_client = MongoClient(mongo_uri)
        self.db = self.mongo_client[db_name]
        self.catalog_col = self.db["catalog"]
        self.tmdb = tmdb_client
        self.checkpoint = checkpoint
        self.batch_size = batch_size
        self.interrupted = False

        # Set up signal handlers for graceful exit
        signal.signal(signal.SIGINT, self._handle_interrupt)
        signal.signal(signal.SIGTERM, self._handle_interrupt)

        self._verify_connections()
        self._ensure_indexes()

    def _verify_connections(self):
        """Pre-flight check for MongoDB Atlas and TMDB API connectivity with diagnostic logs."""
        logger.info("=========================================")
        logger.info(" PRE-FLIGHT CONNECTION CHECKS")
        logger.info("=========================================")

        # 1. Check TMDB API
        logger.info("Testing TMDB API connection...")
        try:
            tmdb_check = self.tmdb._get("/configuration")
            if tmdb_check and "images" in tmdb_check:
                logger.info(" [OK] TMDB API connected successfully (base_url: %s)", tmdb_check["images"].get("secure_base_url"))
            else:
                raise ConnectionError("TMDB returned unexpected response or authorization failed.")
        except Exception as e:
            logger.error(" [FAIL] TMDB API connection failed: %s", e)
            raise SystemExit(1)

        # 2. Check MongoDB Atlas
        logger.info("Testing MongoDB Atlas connection...")
        try:
            ping_res = self.db.command("ping")
            server_info = self.mongo_client.server_info()
            version = server_info.get("version", "unknown")
            logger.info(" [OK] MongoDB Atlas connected successfully (version: %s, ping: %s)", version, ping_res.get("ok"))
        except Exception as e:
            logger.error(" [FAIL] MongoDB Atlas connection failed: %s", e)
            logger.error("Hint: Check MONGODB_URI secret and verify MongoDB Atlas Network Access IP whitelist (allow 0.0.0.0/0 for GitHub Actions).")
            raise SystemExit(1)

        logger.info("=========================================")

    def _handle_interrupt(self, signum, frame):
        logger.warning("\nInterrupt received! Finalizing batch and saving checkpoint before exit...")
        self.interrupted = True

    def _ensure_indexes(self):
        try:
            logger.info("Verifying MongoDB indexes on 'catalog' collection...")
            self.catalog_col.create_index([("title", "text"), ("originalTitle", "text")], name="title_text_index")
            self.catalog_col.create_index([("tmdbId", 1), ("contentType", 1)], name="tmdb_content_idx")
            self.catalog_col.create_index([("popularity", -1)], name="popularity_idx")
            logger.info("Indexes are ready.")
        except Exception as e:
            logger.warning("Could not create indexes (might already exist): %s", e)

    def _build_movie_document(self, movie: Dict[str, Any], details: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Convert TMDB movie data to Vistaro Movie document schema."""
        tmdb_id = movie.get("id")
        imdb_id = None
        if details and "external_ids" in details:
            imdb_id = details["external_ids"].get("imdb_id")
        elif details and "imdb_id" in details:
            imdb_id = details.get("imdb_id")

        now_ms = int(time.time() * 1000)
        return {
            "_id": f"MOVIE:{tmdb_id}",
            "contentType": "MOVIE",
            "tmdbId": tmdb_id,
            "imdbId": imdb_id,
            "title": movie.get("title") or movie.get("original_title") or "",
            "originalTitle": movie.get("original_title") or movie.get("title") or "",
            "overview": movie.get("overview") or "",
            "releaseDate": movie.get("release_date"),
            "rating": float(movie.get("vote_average", 0.0)) if movie.get("vote_average") is not None else None,
            "voteCount": movie.get("vote_count", 0),
            "popularity": float(movie.get("popularity", 0.0)),
            "posterPath": movie.get("poster_path"),
            "backdropPath": movie.get("backdrop_path"),
            "originalLanguage": movie.get("original_language"),
            "updatedAt": now_ms,
        }

    def _build_tv_document(self, show: Dict[str, Any], details: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Convert TMDB TV show data to Vistaro TV show document schema."""
        tmdb_id = show.get("id")
        imdb_id = None
        number_of_seasons = 0
        number_of_episodes = 0

        if details:
            if "external_ids" in details:
                imdb_id = details["external_ids"].get("imdb_id")
            number_of_seasons = details.get("number_of_seasons", 0)
            number_of_episodes = details.get("number_of_episodes", 0)

        now_ms = int(time.time() * 1000)
        return {
            "_id": f"TV_SHOW:{tmdb_id}",
            "contentType": "TV_SHOW",
            "tmdbId": tmdb_id,
            "imdbId": imdb_id,
            "title": show.get("name") or show.get("original_name") or "",
            "originalTitle": show.get("original_name") or show.get("name") or "",
            "overview": show.get("overview") or "",
            "firstAirDate": show.get("first_air_date"),
            "rating": float(show.get("vote_average", 0.0)) if show.get("vote_average") is not None else None,
            "voteCount": show.get("vote_count", 0),
            "popularity": float(show.get("popularity", 0.0)),
            "posterPath": show.get("poster_path"),
            "backdropPath": show.get("backdrop_path"),
            "originalLanguage": show.get("original_language"),
            "numberOfSeasons": number_of_seasons,
            "numberOfEpisodes": number_of_episodes,
            "updatedAt": now_ms,
        }

    def _build_episode_documents(self, season_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Convert TMDB season episodes to Vistaro Episode schema."""
        episodes = []
        now_ms = int(time.time() * 1000)
        for ep in season_data.get("episodes", []):
            episodes.append({
                "tmdbId": ep.get("id"),
                "seasonNumber": ep.get("season_number"),
                "episodeNumber": ep.get("episode_number"),
                "title": ep.get("name") or f"Episode {ep.get('episode_number')}",
                "overview": ep.get("overview") or "",
                "airDate": ep.get("air_date"),
                "rating": float(ep.get("vote_average", 0.0)) if ep.get("vote_average") is not None else None,
                "stillPath": ep.get("still_path"),
                "updatedAt": now_ms,
            })
        return episodes

    def ingest_movies(self, max_pages: int = 50, fetch_details: bool = False):
        """Fetch and bulk upsert movies starting from last checkpoint page."""
        start_page = self.checkpoint.state.get("movie_page", 1)
        target_page = start_page + max_pages
        logger.info(">>> Starting Movie ingestion from page %d to %d (batch size: %d)", start_page, target_page - 1, self.batch_size)

        processed_ids = set(self.checkpoint.state.get("processed_movie_ids", []))
        bulk_operations: List[ReplaceOne] = []

        for page in range(start_page, target_page):
            if self.interrupted:
                break

            data = self.tmdb.get_popular_movies(page=page)
            if not data or not data.get("results"):
                logger.info("Reached end of popular movies at page %d", page)
                break

            results = data.get("results", [])
            for item in results:
                tmdb_id = item["id"]
                if tmdb_id in processed_ids:
                    continue

                details = None
                if fetch_details:
                    details = self.tmdb.get_movie_details(tmdb_id)

                doc = self._build_movie_document(item, details)
                bulk_operations.append(ReplaceOne({"_id": doc["_id"]}, doc, upsert=True))
                processed_ids.add(tmdb_id)

            if len(bulk_operations) >= self.batch_size:
                self._flush_bulk_operations(bulk_operations)
                bulk_operations.clear()
                self.checkpoint.state["movie_page"] = page + 1
                self.checkpoint.state["processed_movie_ids"] = list(processed_ids)
                self.checkpoint.save()

            logger.info("Movie page %d/%d processed (%d movies queued)", page, target_page - 1, len(bulk_operations))

        if bulk_operations:
            self._flush_bulk_operations(bulk_operations)
            bulk_operations.clear()

        self.checkpoint.state["movie_page"] = page if self.interrupted else target_page
        self.checkpoint.state["processed_movie_ids"] = list(processed_ids)
        self.checkpoint.save()
        logger.info("Movie ingestion batch finished. Total unique movies processed: %d", len(processed_ids))

    def ingest_tv_shows(self, max_pages: int = 50, fetch_details: bool = True, episodes_for_top: int = 100):
        """Fetch and bulk upsert TV shows and optionally top seasons/episodes."""
        start_page = self.checkpoint.state.get("tv_page", 1)
        target_page = start_page + max_pages
        logger.info(">>> Starting TV Show ingestion from page %d to %d", start_page, target_page - 1)

        processed_ids = set(self.checkpoint.state.get("processed_tv_ids", []))
        episodes_processed_ids = set(self.checkpoint.state.get("episodes_processed_tv_ids", []))
        bulk_operations: List[UpdateOne] = []

        for page in range(start_page, target_page):
            if self.interrupted:
                break

            data = self.tmdb.get_popular_tv(page=page)
            if not data or not data.get("results"):
                logger.info("Reached end of popular TV shows at page %d", page)
                break

            results = data.get("results", [])
            for item in results:
                if self.interrupted:
                    break

                tmdb_id = item["id"]
                if tmdb_id in processed_ids:
                    continue

                details = self.tmdb.get_tv_details(tmdb_id) if fetch_details else None
                doc = self._build_tv_document(item, details)

                # TV doc update
                update_fields = {k: v for k, v in doc.items() if k != "_id"}
                bulk_operations.append(UpdateOne({"_id": doc["_id"]}, {"$set": update_fields}, upsert=True))
                processed_ids.add(tmdb_id)

                # Episode ingestion for top shows (or if within episodes_for_top limit)
                if details and len(episodes_processed_ids) < episodes_for_top and tmdb_id not in episodes_processed_ids:
                    seasons = details.get("seasons", [])
                    seasons_update = {}
                    for s in seasons:
                        s_num = s.get("season_number", 0)
                        if s_num == 0:
                            continue  # Skip specials to save space
                        s_data = self.tmdb.get_season_details(tmdb_id, s_num)
                        if s_data:
                            ep_docs = self._build_episode_documents(s_data)
                            seasons_update[f"seasons.season_{s_num}"] = ep_docs

                    if seasons_update:
                        bulk_operations.append(UpdateOne({"_id": f"TV_SHOW:{tmdb_id}"}, {"$set": seasons_update}, upsert=True))
                        episodes_processed_ids.add(tmdb_id)

            if len(bulk_operations) >= self.batch_size:
                self._flush_bulk_operations(bulk_operations)
                bulk_operations.clear()
                self.checkpoint.state["tv_page"] = page + 1
                self.checkpoint.state["processed_tv_ids"] = list(processed_ids)
                self.checkpoint.state["episodes_processed_tv_ids"] = list(episodes_processed_ids)
                self.checkpoint.save()

            logger.info("TV page %d/%d processed (%d items queued, %d shows with episodes)", page, target_page - 1, len(bulk_operations), len(episodes_processed_ids))

        if bulk_operations:
            self._flush_bulk_operations(bulk_operations)
            bulk_operations.clear()

        self.checkpoint.state["tv_page"] = page if self.interrupted else target_page
        self.checkpoint.state["processed_tv_ids"] = list(processed_ids)
        self.checkpoint.state["episodes_processed_tv_ids"] = list(episodes_processed_ids)
        self.checkpoint.save()
        logger.info("TV ingestion batch finished. Total unique shows: %d, Shows with episodes: %d", len(processed_ids), len(episodes_processed_ids))

    def _flush_bulk_operations(self, operations: List[Any]):
        if not operations:
            return
        try:
            result = self.catalog_col.bulk_write(operations, ordered=False)
            logger.info(
                "MongoDB Bulk Write: %d matched, %d modified, %d upserted",
                result.matched_count,
                result.modified_count,
                len(result.upserted_ids),
            )
        except Exception as e:
            logger.error("MongoDB bulk write error: %s", e)

    def print_stats(self):
        """Print current MongoDB collection statistics and storage estimate."""
        try:
            count = self.catalog_col.count_documents({})
            movie_count = self.catalog_col.count_documents({"contentType": "MOVIE"})
            tv_count = self.catalog_col.count_documents({"contentType": "TV_SHOW"})
            ep_count = self.catalog_col.count_documents({"seasons": {"$exists": True}})

            stats = self.db.command("collStats", "catalog")
            size_mb = stats.get("size", 0) / (1024 * 1024)
            storage_mb = stats.get("storageSize", 0) / (1024 * 1024)

            logger.info("=========================================")
            logger.info(" VISTARO CATALOG STATS")
            logger.info("=========================================")
            logger.info(" Total Documents in Atlas: %d", count)
            logger.info(" - Movies:                  %d", movie_count)
            logger.info(" - TV Shows:                %d", tv_count)
            logger.info(" - Shows with Full Seasons: %d", ep_count)
            logger.info(" Uncompressed Data Size:    %.2f MB", size_mb)
            logger.info(" Atlas Disk Storage Size:   %.2f MB / 512 MB Free Tier limit (%.1f%% used)", storage_mb, (storage_mb / 512.0) * 100)
            logger.info("=========================================")
        except Exception as e:
            logger.warning("Could not retrieve collection stats: %s", e)


def main():
    parser = argparse.ArgumentParser(description="Vistaro TMDB to MongoDB Atlas Ingestion Job")
    parser.add_argument("--mode", choices=["popular", "all", "stats", "reset"], default="popular", help="Ingestion mode")
    parser.add_argument("--movie-pages", type=int, default=25, help="Number of movie pages to fetch (20 items/page)")
    parser.add_argument("--tv-pages", type=int, default=25, help="Number of TV pages to fetch (20 items/page)")
    parser.add_argument("--episodes-for-top", type=int, default=100, help="Number of top TV shows to fetch full seasons for")
    parser.add_argument("--batch-size", type=int, default=50, help="Bulk upsert batch size")
    parser.add_argument("--rps", type=float, default=25.0, help="TMDB rate limit requests per second")
    args = parser.parse_args()

    checkpoint = CheckpointManager(CHECKPOINT_FILE)

    if args.mode == "reset":
        checkpoint.reset()
        logger.info("Checkpoint cleared. Ready for fresh run.")
        return

    secrets = load_secrets()
    mongo_uri = secrets.get("MONGODB_URI")
    tmdb_key = secrets.get("TMDB_API_KEY")
    tmdb_token = secrets.get("TMDB_ACCESS_TOKEN")

    if not mongo_uri:
        logger.error("MONGODB_URI not found in secrets.properties or environment!")
        sys.exit(1)
    if not tmdb_key and not tmdb_token:
        logger.error("TMDB_API_KEY or TMDB_ACCESS_TOKEN not found in secrets.properties or environment!")
        sys.exit(1)

    tmdb_client = TmdbClient(api_key=tmdb_key or "", access_token=tmdb_token, rps=args.rps)
    job = CatalogIngestJob(
        mongo_uri=mongo_uri,
        tmdb_client=tmdb_client,
        checkpoint=checkpoint,
        batch_size=args.batch_size,
    )

    if args.mode == "stats":
        job.print_stats()
        return

    logger.info("Starting ingestion job in '%s' mode...", args.mode)
    start_time = time.time()

    # Ingest Movies
    job.ingest_movies(max_pages=args.movie_pages, fetch_details=False)

    # Ingest TV Shows (with episodes for top shows)
    if not job.interrupted:
        job.ingest_tv_shows(max_pages=args.tv_pages, fetch_details=True, episodes_for_top=args.episodes_for_top)

    elapsed = time.time() - start_time
    logger.info("Ingestion run completed in %.1f seconds.", elapsed)
    job.print_stats()


if __name__ == "__main__":
    main()
