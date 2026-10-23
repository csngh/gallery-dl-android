"""Bridge between the Kotlin app and gallery-dl, run in-process through Chaquopy."""
import json
import logging
import os
import time

from gallery_dl import config, exception, extractor, job, output


def is_supported(url):
    """True if gallery-dl has an extractor that matches the URL."""
    try:
        return extractor.find(url) is not None
    except Exception:
        return False


def gallery_dl_version():
    from gallery_dl import version
    return version.__version__


def site_of(url):
    """The gallery-dl category (the website) behind a link, or "" if unknown."""
    try:
        extr = extractor.find(url)
        return (extr.category or "") if extr is not None else ""
    except Exception:
        return ""


class _ErrorCollector(logging.Handler):
    def __init__(self):
        super().__init__(logging.ERROR)
        self.messages = []

    def emit(self, record):
        try:
            self.messages.append(record.getMessage())
        except Exception:
            pass


PROGRESS_INTERVAL = 0.25  # seconds between progress updates sent to the app


class _ProgressOutput(output.NullOutput):
    """Replaces gallery-dl's terminal output so download progress reaches the app."""

    def __init__(self, owner):
        self._owner = owner

    def start(self, path):
        self._owner.file_started(path)

    def success(self, path):
        self._owner.file_progress(100, 100)

    def progress(self, bytes_total, bytes_downloaded, bytes_per_second):
        # gallery-dl reports progress for every chunk it receives, so this is also the moment to honour a cancel:
        # stopping here ends a large file part-way instead of waiting for it to finish.
        if self._owner.is_cancelled():
            raise exception.StopExtraction()
        self._owner.file_progress(bytes_total, bytes_downloaded)


class _ReportingJob(job.DownloadJob):
    """DownloadJob that tells the Kotlin listener about every file as it lands on disk.

    gallery-dl creates child jobs with ``self.__class__(extractor, parent)`` when a link expands into
    other links (profiles, threads, ...), so the constructor must accept that signature and share
    the listener and the failure counter with the parent.
    """

    def __init__(self, url, parent=None, listener=None):
        super().__init__(url, parent)
        self._listener = listener if listener is not None else parent._listener
        self._stats = parent._stats if parent is not None else {
            "failed": 0,        # files that could not be downloaded
            "handled": 0,       # files seen so far, including skipped ones
            "count": 0,         # size of the album, when the site reports one
            "count_ok": True,   # False once the reported count stops being trustworthy
            "name": "",
            "percent": -1,
            "last_emit": 0.0,
        }
        if parent is not None:
            # A link that expands into other links has no single total.
            self._stats["count_ok"] = False
        self._skipped = False
        self._last_path = None
        self.out = _ProgressOutput(self)

    @property
    def failed(self):
        return self._stats["failed"]

    # --- progress reporting -------------------------------------------------

    def _album_count(self):
        """Total files in this link, or 0 when unknown or unreliable."""
        stats = self._stats
        if not stats["count_ok"] or stats["count"] <= 0 or stats["handled"] > stats["count"]:
            return 0
        return stats["count"]

    def _note_url(self, kwdict):
        stats = self._stats
        stats["handled"] += 1
        stats["percent"] = -1
        count = kwdict.get("count")
        if isinstance(count, int) and not isinstance(count, bool) and count > 0:
            if stats["count"] == 0:
                stats["count"] = count
            elif stats["count"] != count:
                stats["count_ok"] = False   # per-post counts, e.g. a profile: not an album total

    def _emit(self, force=False):
        stats = self._stats
        now = time.monotonic()
        if not force and now - stats["last_emit"] < PROGRESS_INTERVAL:
            return
        stats["last_emit"] = now
        self._listener.onProgress(stats["handled"], self._album_count(), stats["percent"], stats["name"])

    def is_cancelled(self):
        return bool(self._listener.isCancelled())

    def file_started(self, path):
        self._stats["name"] = os.path.basename(path)
        self._stats["percent"] = -1
        self._emit(force=True)

    def file_progress(self, bytes_total, bytes_downloaded):
        stats = self._stats
        if bytes_total and bytes_total > 0:
            stats["percent"] = max(0, min(100, int(bytes_downloaded * 100 / bytes_total)))
        else:
            stats["percent"] = -1
        self._emit(force=stats["percent"] == 100)

    def handle_skip(self):
        self._skipped = True
        super().handle_skip()

    def handle_url(self, url, kwdict):
        if self._listener.isCancelled():
            raise exception.StopExtraction()
        self._skipped = False
        self._note_url(kwdict)
        super().handle_url(url, kwdict)
        path = self.pathfmt.realpath
        if path and path != self._last_path and os.path.isfile(path):
            self._last_path = path
            self._listener.onFile(path, url)
        elif not self._skipped:
            # Neither saved nor skipped: gallery-dl logged "Failed to download".
            self._stats["failed"] += 1


# The only gallery-dl options the app lets the user change; anything else in the JSON is ignored.
USER_OPTIONS = {"api", "sleep-request", "sleep", "retries", "timeout", "skip", "cookies"}

# Options that gallery-dl reads in the downloader section as well as the extractor section.
BOTH_SECTIONS = {"retries", "timeout"}


def download(url, base_dir, archive_path, options_json, listener):
    """Run gallery-dl for one URL.

    The per-link archive remembers saved files, so a retry only fetches what is still missing.
    options_json holds the user's switched-on options, for example {"sleep": [1.0, 3.0]}.
    Returns a JSON string: {"status": int, "failed": int, "errors": [str]}.
    """
    config.clear()
    config.set(("extractor",), "base-directory", base_dir)
    # Flat layout: the app puts each link in its own folder, so skip gallery-dl's category/title subfolders.
    config.set(("extractor",), "directory", [])
    config.set(("extractor",), "archive", archive_path)
    # Report progress as often as chunks arrive; _emit() throttles what is sent to the app.
    config.set(("downloader",), "progress", 0.0)
    # Shared storage is FAT-like: replace characters such as : ? * " in file names.
    config.set(("extractor",), "path-restrict", "windows")
    for key, value in json.loads(options_json or "{}").items():
        if key in USER_OPTIONS:
            config.set(("extractor",), key, value)
            if key in BOTH_SECTIONS:
                config.set(("downloader",), key, value)

    collector = _ErrorCollector()
    root = logging.getLogger()
    root.addHandler(collector)
    try:
        download_job = _ReportingJob(url, listener=listener)
        status = download_job.run()
    finally:
        root.removeHandler(collector)

    return json.dumps({
        "status": status,
        "failed": download_job.failed,
        "errors": collector.messages[:20],
    })
