import cv2
import numpy as np

from conftest import QR_TEXT, decode, encode
from prep import prepare


def test_resizes_long_side_to_1280(big_jpeg):
    out, facts = prepare(big_jpeg, None)
    img = decode(out)
    assert img.shape[1] == 1280
    assert img.shape[0] == 960
    assert facts["size"] == [1280, 960]
    assert facts["cropped"] is False


def test_small_picture_is_not_enlarged(desk_jpeg):
    before = decode(desk_jpeg).shape
    out, facts = prepare(desk_jpeg, None)
    after = decode(out).shape
    assert after[1] <= max(before[1], 1) and after[1] <= 1280
    assert facts["size"] == [after[1], after[0]]


def test_crop_grows_box_by_15_percent():
    img = np.full((1000, 1000, 3), 200, np.uint8)
    out, facts = prepare(encode(img), (0.4, 0.4, 0.6, 0.6))
    h, w = decode(out).shape[:2]
    # 200 px box grown by 15 % on each side: 260 px, give or take rounding.
    assert abs(w - 260) <= 2 and abs(h - 260) <= 2
    assert facts["cropped"] is True


def test_crop_is_clamped_at_the_edge():
    img = np.full((500, 800, 3), 200, np.uint8)
    out, facts = prepare(encode(img), (0.9, 0.9, 1.0, 1.0))
    h, w = decode(out).shape[:2]
    assert w <= 800 and h <= 500 and w > 0 and h > 0


def test_dark_image_gets_clahe_and_the_flag(desk_jpeg):
    dark = (decode(desk_jpeg) * 0.2).astype(np.uint8)
    out, facts = prepare(encode(dark), None)
    assert facts["dark"] is True
    lab_in = cv2.cvtColor(dark, cv2.COLOR_BGR2LAB)[:, :, 0].mean()
    lab_out = cv2.cvtColor(decode(out), cv2.COLOR_BGR2LAB)[:, :, 0].mean()
    assert lab_out > lab_in + 5


def test_bright_image_is_left_alone(desk_jpeg):
    out, facts = prepare(desk_jpeg, None)
    assert facts["dark"] is False


def test_reads_a_qr_code(qr_jpeg):
    out, facts = prepare(qr_jpeg, None)
    assert facts["qr"] == QR_TEXT


def test_no_qr_gives_none(desk_jpeg):
    out, facts = prepare(desk_jpeg, None)
    assert facts["qr"] is None


def test_garbage_raises_value_error():
    import pytest

    with pytest.raises(ValueError):
        prepare(b"not a picture", None)
