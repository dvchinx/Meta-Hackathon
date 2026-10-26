"""Pure image operations on BGR uint8 arrays. No model calls here."""

import cv2
import numpy as np

DAMAGE_COLOR = (0, 0, 255)  # red, BGR


def decode(data: bytes) -> np.ndarray:
    image = cv2.imdecode(np.frombuffer(data, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("not a decodable image")
    return image


def encode_jpeg(image: np.ndarray, quality: int) -> bytes:
    ok, buffer = cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, quality])
    if not ok:
        raise ValueError("could not encode JPEG")
    return buffer.tobytes()


def downscale(image: np.ndarray, max_side: int) -> np.ndarray:
    height, width = image.shape[:2]
    scale = max_side / max(height, width)
    if scale >= 1:
        return image
    return cv2.resize(image, (round(width * scale), round(height * scale)), interpolation=cv2.INTER_AREA)


def anonymize(image: np.ndarray, masks: list[np.ndarray]) -> np.ndarray:
    """Pixelates and blurs every masked region (faces, plates), with a margin so edges do not leak."""
    if not masks:
        return image.copy()
    region = np.zeros(image.shape[:2], dtype=np.uint8)
    for mask in masks:
        region[mask] = 255
    margin = max(3, max(image.shape[:2]) // 100)
    region = cv2.dilate(region, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * margin + 1, 2 * margin + 1)))

    height, width = image.shape[:2]
    block = max(8, max(height, width) // 60)
    pixelated = cv2.resize(
        cv2.resize(image, (max(1, width // block), max(1, height // block)), interpolation=cv2.INTER_AREA),
        (width, height),
        interpolation=cv2.INTER_NEAREST,
    )
    pixelated = cv2.GaussianBlur(pixelated, (0, 0), sigmaX=block / 2)

    out = image.copy()
    out[region > 0] = pixelated[region > 0]
    return out


def mark_damage(image: np.ndarray, damage: np.ndarray) -> np.ndarray:
    """Semi-transparent fill plus outline, for the confirmation message and the public map."""
    out = image.copy()
    if not damage.any():
        return out
    layer = out.copy()
    layer[damage] = DAMAGE_COLOR
    out = cv2.addWeighted(layer, 0.4, out, 0.6, 0)
    contours, _ = cv2.findContours(damage.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    cv2.drawContours(out, contours, -1, DAMAGE_COLOR, thickness=max(2, max(image.shape[:2]) // 300))
    return out


def side_by_side(before: np.ndarray, after: np.ndarray) -> np.ndarray:
    """Both photos at the same height with labels, for Muse Spark's before/after judgement."""
    height = min(before.shape[0], after.shape[0])

    def fit(image: np.ndarray) -> np.ndarray:
        scale = height / image.shape[0]
        return cv2.resize(image, (round(image.shape[1] * scale), height), interpolation=cv2.INTER_AREA)

    left, right = fit(before), fit(after)
    gap = np.full((height, max(4, height // 100), 3), 255, dtype=np.uint8)
    combined = np.hstack([left, gap, right])
    font_scale = max(0.6, height / 600)
    for text, x in (("ANTES", 10), ("DESPUES", left.shape[1] + gap.shape[1] + 10)):
        org = (x, round(30 * font_scale))
        cv2.putText(combined, text, org, cv2.FONT_HERSHEY_SIMPLEX, font_scale, (0, 0, 0), 4, cv2.LINE_AA)
        cv2.putText(combined, text, org, cv2.FONT_HERSHEY_SIMPLEX, font_scale, (255, 255, 255), 2, cv2.LINE_AA)
    return combined
