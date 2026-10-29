"""Bridge between the Kotlin app and gallery-dl, run in-process through Chaquopy."""
import json
import logging
import os
import re
import time
import unicodedata

from gallery_dl import config, exception, extractor, job, output
from gallery_dl.extractor import common


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


_NO_ADDRESS = re.compile(r"^'([^']*):' URLs are not supported/enabled$")


def _friendly(message):
    """gallery-dl's wording for a file that has no usable address, put plainly."""
    found = _NO_ADDRESS.match(message)
    if not found:
        return message
    if not found.group(1):
        return ("Some files have no download address. The website does not give one for them, which is usual for locked, "
                "removed or members-only files. If a login is needed, import your cookies in Settings.")
    return f"Files with '{found.group(1)}:' addresses cannot be downloaded by this app."


class _ErrorCollector(logging.Handler):
    def __init__(self):
        super().__init__(logging.ERROR)
        self.messages = []

    def emit(self, record):
        try:
            self.messages.append(_friendly(record.getMessage()))
        except Exception:
            pass


PROGRESS_INTERVAL = 0.25  # seconds between progress updates sent to the app


def _normal(name):
    """A file name in one fixed spelling, so the same accented or CJK name compares equal whichever way it was stored."""
    return unicodedata.normalize("NFC", name)

# The listener of the download that is running, so a cancel reaches code that is not inside a file transfer.
_active = {"listener": None}
_original_request = common.Extractor.request


def _cancellable_request(self, *args, **kwargs):
    """Every web request an extractor makes first checks for a cancel.

    A link that expands into hundreds of pages or sub-links would otherwise keep requesting them, one after another, long
    after the user pressed stop: only the file transfers themselves looked at the cancel flag.
    """
    listener = _active["listener"]
    if listener is not None and listener.isCancelled():
        raise exception.StopExtraction()
    return _original_request(self, *args, **kwargs)


common.Extractor.request = _cancellable_request


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

    def __init__(self, url, parent=None, listener=None, known=None, known_urls=None):
        super().__init__(url, parent)
        self._listener = listener if listener is not None else parent._listener
        # Names and direct addresses of files the library already has for this link. Removing a link and adding it again
        # leaves no archive, so without this every file would be fetched and saved a second time. The address matters as
        # much as the name: a name that carries a position number changes when posts are added or removed, the address does not.
        self._known = known if known is not None else (parent._known if parent is not None else frozenset())
        self._known_urls = known_urls if known_urls is not None else (parent._known_urls if parent is not None else frozenset())
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

    def _is_known(self, url, kwdict):
        """True when the library already has this file, by its direct address or by the name it would be saved as."""
        try:
            self.pathfmt.set_filename(kwdict)
            # The file name is only worked out by build_path(); set_filename() leaves it empty.
            self.pathfmt.build_path()
        except Exception:
            return url in self._known_urls
        name = getattr(self.pathfmt, "filename", None)
        return url in self._known_urls or (name is not None and _normal(name) in self._known)

    def _folder_name(self):
        """The innermost folder gallery-dl would save the current file in, or "" when it would sit directly in the root."""
        try:
            directory = os.path.normpath(getattr(self.pathfmt, "realdirectory", "") or "")
            base = os.path.normpath(os.path.expanduser(config.get(("extractor",), "base-directory") or ""))
            if not directory or directory == "." or directory == base:
                return ""
            return os.path.basename(directory)
        except Exception:
            return ""

    def _report_skipped(self, url):
        name = getattr(self.pathfmt, "filename", None)
        if name:
            # The folder comes along so a file saved under an older folder name can be moved to the one gallery-dl uses now.
            self._listener.onSkipped(name, url, self._folder_name())

    def handle_queue(self, url, kwdict):
        # A sub-link that is cancelled ends only its own job; without this the parent carried on with the next one.
        if self._listener.isCancelled():
            raise exception.StopExtraction()
        super().handle_queue(url, kwdict)

    def handle_url(self, url, kwdict):
        if self._listener.isCancelled():
            raise exception.StopExtraction()
        self._skipped = False
        self._note_url(kwdict)
        if (self._known or self._known_urls) and self._is_known(url, kwdict):
            return self._report_skipped(url)
        super().handle_url(url, kwdict)
        path = self.pathfmt.realpath
        if path and path != self._last_path and os.path.isfile(path):
            self._last_path = path
            self._listener.onFile(path, url)
        elif self._skipped:
            # Already downloaded earlier, so nothing lands on disk. The library still learns this file's current address,
            # which is how a refreshed link gives every file a fresh one.
            self._report_skipped(url)
        else:
            # Neither saved nor skipped: gallery-dl logged "Failed to download".
            self._stats["failed"] += 1


# The only gallery-dl options the app lets the user change; anything else in the JSON is ignored.
USER_OPTIONS = {"api", "sleep-request", "sleep", "retries", "timeout", "skip", "cookies"}

# Options that gallery-dl reads in the downloader section as well as the extractor section.
BOTH_SECTIONS = {"retries", "timeout"}


def _apply_options(options_json):
    for key, value in json.loads(options_json or "{}").items():
        if key in USER_OPTIONS:
            config.set(("extractor",), key, value)
            if key in BOTH_SECTIONS:
                config.set(("downloader",), key, value)


class _NoListener:
    """A listener that ignores everything, for runs that only look and save nothing."""

    def __getattr__(self, _name):
        return lambda *args, **kwargs: None

    def isCancelled(self):
        return False


class _FolderProbe(_ReportingJob):
    """Walks a link up to its first file and notes the folder gallery-dl would save it in; downloads nothing."""

    def _found(self):
        return self._stats.get("probe") is not None

    def handle_queue(self, url, kwdict):
        if self._found() or time.monotonic() > self._stats["deadline"]:
            raise exception.StopExtraction()
        super().handle_queue(url, kwdict)

    def handle_url(self, url, kwdict):
        if self._found() or time.monotonic() > self._stats["deadline"]:
            raise exception.StopExtraction()
        self.pathfmt.set_filename(kwdict)
        self.pathfmt.build_path()
        self._stats["probe"] = self._folder_name()
        raise exception.StopExtraction()


def preview_folder(url, options_json="{}", seconds=25):
    """The folder gallery-dl would save the first file of a link in ("" when unknown or at the root).

    Safe to call while a download runs: gallery-dl's settings are not cleared, and only the options the user
    chose are set again to the values a download sets.
    """
    try:
        config.set(("extractor",), "path-restrict", "windows")
        _apply_options(options_json)
        probe = _FolderProbe(url, listener=_NoListener())
        probe._stats["deadline"] = time.monotonic() + seconds
        probe.run()
        return probe._stats.get("probe") or ""
    except Exception:
        return ""


def download(url, base_dir, archive_path, options_json, listener, known_json="[]"):
    """Run gallery-dl for one URL.

    What counts as already saved is what the library holds (``known_json``), never a record kept apart from it: a record
    of its own would still say "downloaded" after the files were deleted, and a refresh would then fetch nothing.
    ``archive_path`` is only used to remove an old record left by an earlier version.
    options_json holds the user's switched-on options, for example {"sleep": [1.0, 3.0]}.
    Returns a JSON string: {"status": int, "failed": int, "errors": [str]}.
    """
    config.clear()
    config.set(("extractor",), "base-directory", base_dir)
    # gallery-dl's own folders are kept: the app names each library folder after the innermost one a file lands in.
    if archive_path:
        for suffix in ("", "-wal", "-shm", "-journal"):
            try:
                os.remove(archive_path + suffix)
            except OSError:
                pass
    # Report progress as often as chunks arrive; _emit() throttles what is sent to the app.
    config.set(("downloader",), "progress", 0.0)
    # Shared storage is FAT-like: replace characters such as : ? * " in file names.
    config.set(("extractor",), "path-restrict", "windows")
    _apply_options(options_json)

    collector = _ErrorCollector()
    root = logging.getLogger()
    root.addHandler(collector)
    _active["listener"] = listener
    known = json.loads(known_json or "[]")
    # A plain list is just names; the apps send {"names": [...], "urls": [...]}.
    names, urls = (known.get("names", []), known.get("urls", [])) if isinstance(known, dict) else (known, [])
    try:
        download_job = _ReportingJob(
            url, listener=listener, known=frozenset(_normal(n) for n in names), known_urls=frozenset(u for u in urls if u),
        )
        status = download_job.run()
    finally:
        _active["listener"] = None
        root.removeHandler(collector)

    return json.dumps({
        "status": status,
        "failed": download_job.failed,
        "errors": collector.messages[:20],
    })
