"""Entry points the iOS app calls. Every function takes and returns a JSON string.

The app runs download() on one thread and polls poll() from another, so progress needs no
Python-to-Swift callback. The listener below is what gdl_bridge.py expects from the host app.
"""
import json
import threading
import traceback

import gdl_bridge

_lock = threading.Lock()
_cancel = threading.Event()
_progress = {"handled": 0, "total": 0, "percent": -1, "name": ""}
_files = []


class _Listener:
    def onProgress(self, handled, total, percent, name):
        with _lock:
            _progress.update(handled=handled, total=total, percent=percent, name=name)

    def onFile(self, path, url):
        with _lock:
            _files.append({"path": path, "url": url})

    def onSkipped(self, name, url, folder):
        with _lock:
            _files.append({"skipped": name, "url": url, "folder": folder})

    def isCancelled(self):
        return _cancel.is_set()


def info(_arg):
    return json.dumps({"gallery_dl": gdl_bridge.gallery_dl_version()})


def check(arg):
    url = json.loads(arg)["url"]
    return json.dumps({"supported": gdl_bridge.is_supported(url), "site": gdl_bridge.site_of(url)})


def preview(arg):
    """The folder gallery-dl would save the first file of a link in, found without downloading anything."""
    args = json.loads(arg)
    try:
        return json.dumps({"folder": gdl_bridge.preview_folder(args["url"], json.dumps(args.get("options", {})))})
    except Exception:
        return json.dumps({"folder": ""})


def download(arg):
    args = json.loads(arg)
    _cancel.clear()
    with _lock:
        _progress.update(handled=0, total=0, percent=-1, name="")
        _files.clear()
    try:
        return gdl_bridge.download(
            args["url"], args["base_dir"], args["archive_path"], json.dumps(args.get("options", {})), _Listener(),
            json.dumps(args.get("known", [])),
        )
    except Exception:
        return json.dumps({"status": -1, "failed": 0, "errors": [traceback.format_exc()]})


def poll(_arg):
    """Current progress plus the files saved since the last poll."""
    with _lock:
        out = dict(_progress)
        out["files"] = list(_files)
        _files.clear()
    return json.dumps(out)


def cancel(_arg):
    _cancel.set()
    return "{}"
