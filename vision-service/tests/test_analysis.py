import asyncio

import numpy as np
import pytest

from app import imaging
from app.analysis import DamageType, analyze
from app.sam.client import SamError, SamResult
from app.sam.masks import Box, Detection


def photo(height=300, width=400) -> bytes:
    rng = np.random.default_rng(0)
    return imaging.encode_jpeg(rng.integers(0, 256, (height, width, 3), dtype=np.uint8), 90)


def rect(height, width, top, left, bottom, right) -> Detection:
    mask = np.zeros((height, width), dtype=bool)
    mask[top:bottom, left:right] = True
    return Detection("0", Box(left, top, right, bottom), mask, 0.8)


class StubSam:
    def __init__(self, by_concept: dict[str, list[Detection]], fail_on: str | None = None):
        self.by_concept = by_concept
        self.fail_on = fail_on
        self.calls: list[str] = []
        self.sent: list[bytes] = []

    async def segment(self, jpeg, concept, width, height):
        self.calls.append(concept)
        self.sent.append(jpeg)
        if concept == self.fail_on:
            raise SamError("boom")
        return SamResult(self.by_concept.get(concept, []), 1)


def run(coro):
    return asyncio.run(coro)


def test_segments_damage_faces_and_plates_and_bills_each_call():
    sam = StubSam({"pothole": [rect(300, 400, 100, 100, 150, 200)], "face": [rect(300, 400, 10, 10, 40, 40)]})

    result = run(analyze(photo(), DamageType.POTHOLE, sam, max_side=1024, jpeg_quality=85))

    assert sorted(sam.calls) == ["face", "license plate", "pothole"]
    assert result.damage_area_px == 50 * 100
    assert result.faces == 1 and result.plates == 0
    assert result.sam_images_billed == 3
    assert imaging.decode(result.blurred_jpeg).shape == (300, 400, 3)


def test_damage_area_is_the_union_of_instances():
    parts = [rect(300, 400, 100, 100, 150, 150), rect(300, 400, 100, 150, 150, 200)]
    sam = StubSam({"pothole": parts})

    result = run(analyze(photo(), DamageType.POTHOLE, sam, max_side=1024, jpeg_quality=85))

    assert result.damage_area_px == 50 * 100
    assert len(result.damage_boxes) == 2


def test_skips_privacy_segmentation_when_asked():
    sam = StubSam({})

    run(analyze(photo(), DamageType.POTHOLE, sam, max_side=1024, jpeg_quality=85, blur_faces=False, blur_plates=False))

    assert sam.calls == ["pothole"]


def test_other_damage_is_not_segmented_but_still_anonymized():
    sam = StubSam({})

    result = run(analyze(photo(), DamageType.OTHER, sam, max_side=1024, jpeg_quality=85))

    assert sorted(sam.calls) == ["face", "license plate"]
    assert result.damage_area_px == 0


def test_fails_when_faces_cannot_be_checked():
    sam = StubSam({}, fail_on="face")

    with pytest.raises(SamError):
        run(analyze(photo(), DamageType.POTHOLE, sam, max_side=1024, jpeg_quality=85))


def test_large_photos_are_downscaled_before_segmentation():
    sam = StubSam({})

    result = run(analyze(photo(1500, 2000), DamageType.POTHOLE, sam, max_side=1024, jpeg_quality=85))

    assert (result.width, result.height) == (1024, 768)


def test_small_jpegs_reach_sam_byte_for_byte():
    original = photo()
    sam = StubSam({})

    run(analyze(original, DamageType.POTHOLE, sam, max_side=1024, jpeg_quality=85))

    assert all(sent == original for sent in sam.sent)


def test_large_or_non_jpeg_photos_are_re_encoded():
    big = photo(1500, 2000)
    png = imaging.cv2.imencode(".png", imaging.decode(photo()))[1].tobytes()
    sam = StubSam({})

    run(analyze(big, DamageType.POTHOLE, sam, max_side=1024, jpeg_quality=85))
    run(analyze(png, DamageType.POTHOLE, sam, max_side=1024, jpeg_quality=85))

    assert all(sent.startswith(b"\xff\xd8\xff") and sent not in (big, png) for sent in sam.sent)
