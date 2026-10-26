"""Turns SAM 3.1 Responses API output into full-frame boolean masks.

Decoding is delegated to Meta's official ``meta-sam-parser``. We never decode the wire format ourselves.
"""

from dataclasses import dataclass

import cv2
import numpy as np
from meta_sam_parser import (
    CompletedOutcome,
    SegmentationBoxRecord,
    SegmentationMaskRecord,
    decode_mask_to_raster,
    image_segmentation_format,
)


@dataclass(frozen=True)
class Box:
    left: int
    top: int
    right: int
    bottom: int


@dataclass(frozen=True)
class Detection:
    object_id: str
    box: Box
    mask: np.ndarray  # bool, shape (frame_height, frame_width)
    confidence: float | None

    @property
    def area_px(self) -> int:
        return int(self.mask.sum())


def output_text(response: dict) -> str:
    """Concatenates the ``output_text`` parts of a non-streamed Responses API reply."""
    parts = []
    for item in response.get("output", []):
        for content in item.get("content", []):
            if content.get("type") == "output_text":
                parts.append(content.get("text", ""))
    return "\n".join(p for p in parts if p)


def parse_detections(text: str, frame_width: int, frame_height: int) -> list[Detection]:
    """Parses SAM output text for one image. Zero matches → empty list."""
    if not text.strip():
        return []
    parser = image_segmentation_format().create_parser()
    parser.push(text, emit=False)
    result = parser.finish(CompletedOutcome()).result

    boxes = {r.object_id: r for r in result.records if isinstance(r, SegmentationBoxRecord)}
    detections = []
    for record in result.records:
        if not isinstance(record, SegmentationMaskRecord):
            continue
        mask = _place_in_frame(record, frame_width, frame_height)
        box_record = boxes.get(record.object_id)
        bounds = box_record or record.bounds
        box = Box(
            left=int(bounds.left),
            top=int(bounds.top),
            right=int(np.ceil(bounds.right)),
            bottom=int(np.ceil(bounds.bottom)),
        )
        confidence = record.confidence if record.confidence is not None else getattr(box_record, "confidence", None)
        detections.append(Detection(record.object_id, box, mask, confidence))
    return detections


def _place_in_frame(record: SegmentationMaskRecord, frame_width: int, frame_height: int) -> np.ndarray:
    """Masks are box-local rasters; paste them into a frame-sized boolean array."""
    raster = np.frombuffer(decode_mask_to_raster(record.mask), dtype=np.uint8)
    raster = raster.reshape(record.mask.height, record.mask.width) > 0

    b = record.bounds
    left, top = int(np.floor(b.left)), int(np.floor(b.top))
    right, bottom = int(np.ceil(b.right)), int(np.ceil(b.bottom))
    width, height = max(right - left, 1), max(bottom - top, 1)
    if raster.shape != (height, width):
        raster = cv2.resize(raster.astype(np.uint8), (width, height), interpolation=cv2.INTER_NEAREST) > 0

    frame = np.zeros((frame_height, frame_width), dtype=bool)
    x0, y0 = max(left, 0), max(top, 0)
    x1, y1 = min(right, frame_width), min(bottom, frame_height)
    if x1 > x0 and y1 > y0:
        frame[y0:y1, x0:x1] = raster[y0 - top : y1 - top, x0 - left : x1 - left]
    return frame
