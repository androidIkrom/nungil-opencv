from conftest import FakeBedrock
from describe import describe


def test_sends_the_picture_the_language_and_the_qr_fact(desk_jpeg):
    client = FakeBedrock()
    describe(desk_jpeg, "What is in front of me?", "ko", {"qr": "https://nungil.example/test", "dark": False}, client=client)
    req = client.requests[0]
    content = req["messages"][0]["content"]
    images = [c for c in content if "image" in c]
    assert images and images[0]["image"]["source"]["bytes"] == desk_jpeg
    assert images[0]["image"]["format"] == "jpeg"
    texts = " ".join(c["text"] for c in content if "text" in c)
    assert "What is in front of me?" in texts
    assert "QR code says: https://nungil.example/test" in texts
    system = " ".join(s["text"] for s in req["system"])
    assert "Korean" in system
    assert req["inferenceConfig"]["maxTokens"] == 200
    assert req["inferenceConfig"]["temperature"] == 0.2


def test_english_system_prompt_fixes_the_behaviour(desk_jpeg):
    client = FakeBedrock()
    describe(desk_jpeg, "Describe what is in front of me.", "en", {"qr": None, "dark": False}, client=client)
    system = " ".join(s["text"] for s in client.requests[0]["system"])
    assert "English" in system
    assert "two sentences" in system
    assert "left" in system and "right" in system and "ahead" in system
    assert "verbatim" in system
    assert "who a person is" in system
    assert "QR code says" not in " ".join(c.get("text", "") for c in client.requests[0]["messages"][0]["content"])


def test_returns_the_text(desk_jpeg):
    client = FakeBedrock("A door ahead.")
    assert describe(desk_jpeg, "q", "en", {"qr": None}, client=client) == "A door ahead."


def test_model_id_comes_from_the_environment(desk_jpeg, monkeypatch):
    monkeypatch.setenv("MODEL_ID", "amazon.nova-pro-v1:0")
    client = FakeBedrock()
    describe(desk_jpeg, "q", "en", {"qr": None}, client=client)
    assert client.requests[0]["modelId"] == "amazon.nova-pro-v1:0"


def test_default_model_is_nova_lite(desk_jpeg, monkeypatch):
    monkeypatch.delenv("MODEL_ID", raising=False)
    client = FakeBedrock()
    describe(desk_jpeg, "q", "en", {"qr": None}, client=client)
    assert client.requests[0]["modelId"] == "amazon.nova-lite-v1:0"
