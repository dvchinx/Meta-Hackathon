"""Report photo analysis: damage segmentation, anonymization and the marked image."""

import asyncio
from dataclasses import dataclass
from enum import StrEnum

import numpy as np

from app import imaging
from app.sam.client import SamClient
from app.sam.masks import Box


class DamageType(StrEnum):
    POTHOLE = "POTHOLE"
    OPEN_MANHOLE = "OPEN_MANHOLE"
    DAMAGED_POLE = "DAMAGED_POLE"
    RISKY_TREE = "RISKY_TREE"
    OTHER = "OTHER"


JPEG_MAGIC = b"\xff\xd8\xff"

# SAM noun phrases. OTHER has no concept: it goes to manual review without damage segmentation.
DAMAGE_CONCEPTS: dict[DamageType, str | None] = {
    DamageType.POTHOLE: "pothole",
    DamageType.OPEN_MANHOLE: "open manhole",
    DamageType.DAMAGED_POLE: "utility pole",
    DamageType.RISKY_TREE: "tree",
    DamageType.OTHER: None,
}


@dataclass(frozen=True)
class Analysis:
    width: int
    height: int
    damage_mask: np.ndarray
    damage_boxes: list[Box]
    damage_confidence: float | None
    faces: int
    plates: int
    blurred_jpeg: bytes
    marked_jpeg: bytes
    sam_images_billed: int

    @property
    def damage_area_px(self) -> int:
        return int(self.damage_mask.sum())

    @property
    def damage_area_fraction(self) -> float:
        return self.damage_area_px / (self.width * self.height)


async def analyze(
    photo: bytes,
    damage_type: DamageType,
    sam: SamClient,
    *,
    max_side: int,
    jpeg_quality: int,
    blur_faces: bool = True,
    blur_plates: bool = True,
) -> Analysis:
    original = imaging.decode(photo)
    image = imaging.downscale(original, max_side)
    height, width = image.shape[:2]
    # Re-encoding is not free: SAM dropped a borderline detection (c≈0.66) on a re-encoded copy of a photo it
    # segmented fine as sent. JPEGs that need no downscaling go to SAM byte for byte.
    send_as_is = image is original and photo.startswith(JPEG_MAGIC)
    jpeg = photo if send_as_is else imaging.encode_jpeg(image, jpeg_quality)

    damage_concept = DAMAGE_CONCEPTS[damage_type]
    concepts = [damage_concept] if damage_concept else []
    if blur_faces:
        concepts.append("face")
    if blur_plates:
        concepts.append("license plate")
    # Any failure aborts the whole analysis: we never publish a photo we could not anonymize.
    segmented = await asyncio.gather(*(sam.segment(jpeg, c, width, height) for c in concepts))
    results = dict(zip(concepts, segmented, strict=True))

    damage = results[damage_concept].detections if damage_concept else []
    faces = results["face"].detections if "face" in results else []
    plates = results["license plate"].detections if "license plate" in results else []

    # Instance masks do not overlap (e.g. a deep hole and the worn area around it), so the damage is their union.
    damage_mask = np.zeros((height, width), dtype=bool)
    for d in damage:
        damage_mask |= d.mask
    confidences = [d.confidence for d in damage if d.confidence is not None]

    blurred = imaging.anonymize(image, [d.mask for d in faces + plates])
    marked = imaging.mark_damage(blurred, damage_mask)
    return Analysis(
        width=width,
        height=height,
        damage_mask=damage_mask,
        damage_boxes=[d.box for d in damage],
        damage_confidence=max(confidences) if confidences else None,
        faces=len(faces),
        plates=len(plates),
        blurred_jpeg=imaging.encode_jpeg(blurred, jpeg_quality),
        marked_jpeg=imaging.encode_jpeg(marked, jpeg_quality),
        sam_images_billed=sum(r.images_billed for r in results.values()),
    )
