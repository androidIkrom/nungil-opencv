import pathlib
import sys

import cv2
import numpy as np
import pytest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app"))
FIXTURES = pathlib.Path(__file__).resolve().parent / "fixtures"

QR_TEXT = "https://nungil.example/test"


def encode(img, quality=90):
    ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, quality])
    assert ok
    return buf.tobytes()


def decode(jpeg):
    img = cv2.imdecode(np.frombuffer(jpeg, np.uint8), cv2.IMREAD_COLOR)
    assert img is not None
    return img


@pytest.fixture
def desk_jpeg():
    return (FIXTURES / "desk.jpg").read_bytes()


@pytest.fixture
def qr_jpeg():
    """A QR code drawn by OpenCV on a light gray page, 900 x 700, like a sign on a wall."""
    page = np.full((700, 900, 3), 230, np.uint8)
    encoder = cv2.QRCodeEncoder.create()
    code = encoder.encode(QR_TEXT)
    code = cv2.resize(code, (300, 300), interpolation=cv2.INTER_NEAREST)
    page[200:500, 300:600] = cv2.cvtColor(code, cv2.COLOR_GRAY2BGR)
    return encode(page)


@pytest.fixture
def big_jpeg():
    """4000 x 3000 of soft colour gradients: a phone photo's size."""
    y, x = np.mgrid[0:3000, 0:4000]
    img = np.dstack([(x // 16) % 256, (y // 12) % 256, np.full_like(x, 128)]).astype(np.uint8)
    return encode(img, 80)


class FakeBedrock:
    """Records the Converse request and answers with one sentence."""

    def __init__(self, text="Two chairs ahead, a table on your right."):
        self.text = text
        self.requests = []

    def converse(self, **kwargs):
        self.requests.append(kwargs)
        return {"output": {"message": {"role": "assistant", "content": [{"text": self.text}]}}}
