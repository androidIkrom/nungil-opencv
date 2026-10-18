"""One question about one picture to Amazon Bedrock (Converse API), for a blind user."""
import os

DEFAULT_MODEL = "amazon.nova-lite-v1:0"
MAX_TOKENS = 200
TEMPERATURE = 0.2
LANGUAGES = {"en": "English", "ko": "Korean"}

SYSTEM = (
    "You describe what a phone camera sees for a blind user who is holding the phone. Answer in {language}. "
    "Answer in at most two sentences, plainly. Use spatial words the user can act on: left, right, ahead, near, far, "
    "above, below. If there is readable text or a sign, read it verbatim. Never guess who a person is or describe "
    "their identity; say 'a person' and where they are. When you are not sure, say 'I cannot tell' rather than guess. "
    "Do not mention that you are an AI or that this is an image."
)


def system_prompt(lang: str) -> str:
    return SYSTEM.format(language=LANGUAGES.get(lang, "English"))


def describe(image_jpeg: bytes, question: str, lang: str, facts: dict, client=None) -> str:
    """The model's answer, one or two sentences in `lang`. `facts["qr"]`, when present, is passed on verbatim."""
    if client is None:
        import boto3

        client = boto3.client("bedrock-runtime")
    content = [
        {"image": {"format": "jpeg", "source": {"bytes": image_jpeg}}},
        {"text": question},
    ]
    qr = facts.get("qr") if facts else None
    if qr:
        content.append({"text": "A QR code says: " + qr})
    response = client.converse(
        modelId=os.environ.get("MODEL_ID", DEFAULT_MODEL),
        system=[{"text": system_prompt(lang)}],
        messages=[{"role": "user", "content": content}],
        inferenceConfig={"maxTokens": MAX_TOKENS, "temperature": TEMPERATURE},
    )
    parts = response["output"]["message"]["content"]
    return "".join(p.get("text", "") for p in parts).strip()
