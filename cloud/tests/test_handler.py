import base64
import json

import pytest

import handler


def event(method, path, body=None, headers=None):
    e = {
        "version": "2.0",
        "rawPath": path,
        "requestContext": {"http": {"method": method, "path": path}},
        "headers": headers or {},
        "isBase64Encoded": False,
    }
    if body is not None:
        e["body"] = json.dumps(body)
    return e


@pytest.fixture(autouse=True)
def key(monkeypatch):
    monkeypatch.setenv("NUNGIL_KEY", "secret-key")


def test_get_root_serves_the_page():
    r = handler.handler(event("GET", "/"), None)
    assert r["statusCode"] == 200
    assert r["headers"]["content-type"].startswith("text/html")
    assert "<form" in r["body"] or "<input" in r["body"]


def test_unknown_route_is_404():
    assert handler.handler(event("GET", "/nothing"), None)["statusCode"] == 404


def test_look_rejects_a_bad_key(desk_jpeg):
    body = {"image": base64.b64encode(desk_jpeg).decode(), "question": "q", "lang": "en"}
    r = handler.handler(event("POST", "/look", body, {"x-nungil-key": "wrong"}), None)
    assert r["statusCode"] == 403
    r = handler.handler(event("POST", "/look", body, {}), None)
    assert r["statusCode"] == 403


def test_look_rejects_a_large_image():
    blob = base64.b64encode(b"x" * (5 * 1024 * 1024)).decode()
    r = handler.handler(event("POST", "/look", {"image": blob, "question": "q", "lang": "en"}, {"x-nungil-key": "secret-key"}), None)
    assert r["statusCode"] == 413


def test_look_rejects_bad_json_and_a_missing_image():
    e = event("POST", "/look", None, {"x-nungil-key": "secret-key"})
    e["body"] = "{not json"
    assert handler.handler(e, None)["statusCode"] == 400
    assert handler.handler(event("POST", "/look", {"question": "q"}, {"x-nungil-key": "secret-key"}), None)["statusCode"] == 400


def test_look_rejects_a_picture_that_does_not_decode():
    body = {"image": base64.b64encode(b"not a picture").decode(), "question": "q", "lang": "en"}
    assert handler.handler(event("POST", "/look", body, {"x-nungil-key": "secret-key"}), None)["statusCode"] == 400


def test_look_answers(desk_jpeg, monkeypatch):
    seen = {}

    def fake_describe(image, question, lang, facts, client=None):
        seen.update(question=question, lang=lang, facts=facts, size=len(image))
        return "A laptop ahead on a desk."

    monkeypatch.setattr(handler, "describe", fake_describe)
    body = {"image": base64.b64encode(desk_jpeg).decode(), "question": "What is here?", "lang": "ko", "box": [0.2, 0.2, 0.8, 0.8]}
    r = handler.handler(event("POST", "/look", body, {"X-Nungil-Key": "secret-key"}), None)
    assert r["statusCode"] == 200
    out = json.loads(r["body"])
    assert out["answer"] == "A laptop ahead on a desk."
    assert out["qr"] is None
    assert isinstance(out["ms"], int)
    assert out["model"] == "amazon.nova-lite-v1:0"
    assert seen["question"] == "What is here?" and seen["lang"] == "ko"
    assert seen["facts"]["cropped"] is True


def test_default_question_and_language(desk_jpeg, monkeypatch):
    seen = {}

    def fake_describe(image, question, lang, facts, client=None):
        seen.update(question=question, lang=lang)
        return "ok"

    monkeypatch.setattr(handler, "describe", fake_describe)
    body = {"image": base64.b64encode(desk_jpeg).decode()}
    r = handler.handler(event("POST", "/look", body, {"x-nungil-key": "secret-key"}), None)
    assert r["statusCode"] == 200
    assert seen["lang"] == "en" and seen["question"]


def test_describe_failure_is_a_502(desk_jpeg, monkeypatch):
    def boom(*a, **k):
        raise RuntimeError("bedrock down")

    monkeypatch.setattr(handler, "describe", boom)
    body = {"image": base64.b64encode(desk_jpeg).decode(), "question": "q", "lang": "en"}
    r = handler.handler(event("POST", "/look", body, {"x-nungil-key": "secret-key"}), None)
    assert r["statusCode"] == 502
    assert "bedrock down" not in r["body"]
