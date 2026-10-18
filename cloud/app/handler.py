"""The look-closer Lambda behind a Function URL.

GET  /      the judges' page (web/index.html)
POST /look  {"image": base64 JPEG, "question": str, "lang": "en"|"ko", "box": [l, t, r, b]} with header
            x-nungil-key -> {"answer": str, "qr": str|null, "ms": int, "model": str}

No image is stored or logged; one log line per request carries the route, status, time and size only.
"""
import base64
import json
import logging
import os
import pathlib
import time

from describe import DEFAULT_MODEL, describe
from prep import prepare

log = logging.getLogger("nungil")
log.setLevel(logging.INFO)

MAX_IMAGE_BYTES = 4 * 1024 * 1024
DEFAULT_QUESTION = {"en": "Describe what is in front of me.", "ko": "앞에 무엇이 있는지 설명해 주세요."}
HERE = pathlib.Path(__file__).resolve().parent
PAGE_CANDIDATES = [HERE / "web" / "index.html", HERE.parent / "web" / "index.html"]


def handler(event, context):
    http = (event.get("requestContext") or {}).get("http") or {}
    method = (http.get("method") or "GET").upper()
    path = http.get("path") or event.get("rawPath") or "/"
    headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
    started = time.monotonic()
    if method == "GET" and path in ("", "/"):
        return respond(200, page(), "text/html; charset=utf-8", started, 0)
    if method == "POST" and path == "/look":
        return look(event, headers, started)
    return respond(404, json.dumps({"error": "no such route"}), "application/json", started, 0)


def look(event, headers, started):
    key = os.environ.get("NUNGIL_KEY", "")
    if not key or headers.get("x-nungil-key") != key:
        return error(403, "bad key", started, 0)
    raw = event.get("body") or ""
    if event.get("isBase64Encoded"):
        raw = base64.b64decode(raw).decode("utf-8", "replace")
    try:
        data = json.loads(raw)
    except ValueError:
        return error(400, "body is not JSON", started, len(raw))
    image = data.get("image") if isinstance(data, dict) else None
    if not isinstance(image, str) or not image:
        return error(400, "image missing", started, len(raw))
    if len(image) * 3 // 4 > MAX_IMAGE_BYTES:
        return error(413, "image over 4 MB", started, len(raw))
    try:
        jpeg = base64.b64decode(image, validate=True)
    except (ValueError, TypeError):
        return error(400, "image is not base64", started, len(raw))
    if len(jpeg) > MAX_IMAGE_BYTES:
        return error(413, "image over 4 MB", started, len(jpeg))
    lang = data.get("lang") if data.get("lang") in ("en", "ko") else "en"
    question = data.get("question") or DEFAULT_QUESTION[lang]
    box = data.get("box")
    if not (isinstance(box, list) and len(box) == 4 and all(isinstance(v, (int, float)) for v in box)):
        box = None
    try:
        prepared, facts = prepare(jpeg, box)
    except ValueError:
        return error(400, "image does not decode", started, len(jpeg))
    try:
        answer = describe(prepared, str(question), lang, facts)
    except Exception as e:  # noqa: BLE001 - the user hears "the cloud did not answer", the log keeps the cause
        log.warning("describe failed: %s", type(e).__name__)
        return error(502, "the model did not answer", started, len(jpeg))
    body = {
        "answer": answer,
        "qr": facts.get("qr"),
        "ms": int((time.monotonic() - started) * 1000),
        "model": os.environ.get("MODEL_ID", DEFAULT_MODEL),
    }
    return respond(200, json.dumps(body, ensure_ascii=False), "application/json; charset=utf-8", started, len(jpeg))


def page():
    for candidate in PAGE_CANDIDATES:
        if candidate.exists():
            return candidate.read_text(encoding="utf-8")
    return "<!doctype html><title>Nungil</title><p>Nungil look closer. POST /look."


def error(status, message, started, size):
    return respond(status, json.dumps({"error": message}), "application/json", started, size)


def respond(status, body, content_type, started, size):
    log.info("status=%d ms=%d bytes_in=%d", status, int((time.monotonic() - started) * 1000), size)
    return {"statusCode": status, "headers": {"content-type": content_type}, "body": body}
