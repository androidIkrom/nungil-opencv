"""OpenCV 5 preprocessing for one "look closer" frame, on the Lambda (Graviton).

The phone sends an upright JPEG. Here the picture is cropped to the thing the user pointed at (when a box came
along), shrunk to a long side of 1280 so the model gets a clean 1-2 MB at most, lifted with CLAHE when it is dark,
and searched for a QR code, which the model is then told about verbatim.
"""
import cv2
import numpy as np

MAX_SIDE = 1280
MARGIN = 0.15
DARK_L = 90
CLAHE_CLIP = 2.0
CLAHE_TILES = 8
JPEG_QUALITY = 85


def prepare(jpeg: bytes, box):
    """Return (jpeg bytes, facts). `box` is (left, top, right, bottom) in 0..1 or None.

    facts: {"qr": text or None, "size": [w, h], "dark": bool, "cropped": bool}. Raises ValueError when the
    bytes are not a picture.
    """
    img = cv2.imdecode(np.frombuffer(jpeg, np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("not a picture")
    cropped = False
    if box is not None:
        img = crop(img, box)
        cropped = True
    h, w = img.shape[:2]
    if max(h, w) > MAX_SIDE:
        scale = MAX_SIDE / max(h, w)
        img = cv2.resize(img, (max(1, round(w * scale)), max(1, round(h * scale))), interpolation=cv2.INTER_AREA)
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    dark = bool(lab[:, :, 0].mean() < DARK_L)
    if dark:
        clahe = cv2.createCLAHE(clipLimit=CLAHE_CLIP, tileGridSize=(CLAHE_TILES, CLAHE_TILES))
        lab[:, :, 0] = clahe.apply(lab[:, :, 0])
        img = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
    qr = read_qr(img)
    ok, out = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY])
    if not ok:
        raise ValueError("could not encode")
    h, w = img.shape[:2]
    return out.tobytes(), {"qr": qr, "size": [w, h], "dark": dark, "cropped": cropped}


def crop(img, box):
    """The box grown by MARGIN of its own size on every side, clamped to the picture, at least one pixel."""
    h, w = img.shape[:2]
    left, top, right, bottom = (float(v) for v in box)
    bw, bh = right - left, bottom - top
    left, right = left - bw * MARGIN, right + bw * MARGIN
    top, bottom = top - bh * MARGIN, bottom + bh * MARGIN
    x0 = min(max(int(round(left * w)), 0), w - 1)
    y0 = min(max(int(round(top * h)), 0), h - 1)
    x1 = min(max(int(round(right * w)), x0 + 1), w)
    y1 = min(max(int(round(bottom * h)), y0 + 1), h)
    return img[y0:y1, x0:x1]


def read_qr(img):
    try:
        text, points, _ = cv2.QRCodeDetector().detectAndDecode(img)
    except cv2.error:
        return None
    return text or None
