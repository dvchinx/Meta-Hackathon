import numpy as np
import pytest

from app import imaging


def noisy_image(height=300, width=400, seed=0) -> np.ndarray:
    return np.random.default_rng(seed).integers(0, 256, (height, width, 3), dtype=np.uint8)


def test_downscale_keeps_aspect_ratio_and_never_upscales():
    assert imaging.downscale(noisy_image(1500, 2000), 1024).shape[:2] == (768, 1024)
    assert imaging.downscale(noisy_image(300, 400), 1024).shape[:2] == (300, 400)


def test_decode_rejects_garbage():
    with pytest.raises(ValueError):
        imaging.decode(b"not an image")


def test_jpeg_round_trip():
    image = noisy_image()
    assert imaging.decode(imaging.encode_jpeg(image, 85)).shape == image.shape


def test_anonymize_destroys_the_masked_region_and_leaves_the_rest():
    image = noisy_image()
    face = np.zeros(image.shape[:2], dtype=bool)
    face[100:150, 100:160] = True

    out = imaging.anonymize(image, [face])

    inside = np.abs(out[face].astype(int) - image[face].astype(int)).mean()
    assert inside > 40  # noise replaced by a smooth pixelated version
    far_away = np.zeros_like(face)
    far_away[250:, 300:] = True
    assert np.array_equal(out[far_away], image[far_away])


def test_anonymize_without_masks_is_a_copy():
    image = noisy_image()
    out = imaging.anonymize(image, [])
    assert np.array_equal(out, image) and out is not image


def test_mark_damage_only_touches_the_damage_and_its_outline():
    image = np.full((200, 200, 3), 128, dtype=np.uint8)
    damage = np.zeros((200, 200), dtype=bool)
    damage[80:120, 80:120] = True

    out = imaging.mark_damage(image, damage)

    assert out[100, 100, 2] > out[100, 100, 0]  # reddish inside
    assert np.array_equal(out[10, 10], image[10, 10])


def test_side_by_side_puts_both_photos_at_the_same_height():
    combined = imaging.side_by_side(noisy_image(300, 400), noisy_image(600, 600, seed=1))
    assert combined.shape[0] == 300
    assert combined.shape[1] > 400 + 300
