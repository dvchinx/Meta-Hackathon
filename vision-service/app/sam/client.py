"""SAM 3.1 clients. ``MetaSamClient`` spends credits; ``FakeSamClient`` is for tests and offline dev."""

import base64
from dataclasses import dataclass
from typing import Protocol

import cv2
import httpx
import numpy as np

from app.sam.masks import Box, Detection, output_text, parse_detections


class SamError(Exception):
    pass


@dataclass(frozen=True)
class SamResult:
    detections: list[Detection]
    images_billed: int


class SamClient(Protocol):
    async def segment(self, jpeg: bytes, concept: str, width: int, height: int) -> SamResult: ...


class MetaSamClient:
    """One concept per call: the API does not accept several noun phrases in one request."""

    def __init__(self, http: httpx.AsyncClient, model: str):
        self._http = http
        self._model = model

    async def segment(self, jpeg: bytes, concept: str, width: int, height: int) -> SamResult:
        data_url = "data:image/jpeg;base64," + base64.b64encode(jpeg).decode()
        body = {
            "model": self._model,
            "input": [
                {
                    "type": "message",
                    "role": "user",
                    "content": [
                        {"type": "input_text", "text": concept},
                        {"type": "input_image", "image_url": data_url},
                    ],
                }
            ],
            "stream": False,
            # Citizens' photos: never let the API keep them.
            "store": False,
            "metadata": {"include_confidence": "true", "mask_encoding": "one_bit"},
        }
        try:
            response = await self._http.post("/responses", json=body)
        except httpx.HTTPError as e:
            raise SamError(f"SAM request failed: {e}") from e
        if response.status_code != 200:
            raise SamError(f"SAM returned {response.status_code}: {response.text[:300]}")

        payload = response.json()
        if payload.get("status") != "completed":
            raise SamError(f"SAM response not completed: {payload.get('status')} {payload.get('error')}")
        billed = int(payload.get("usage", {}).get("images_processed", 1))
        return SamResult(parse_detections(output_text(payload), width, height), billed)


class FakeSamClient:
    """Deterministic stand-in: finds the damage as a centered ellipse and never finds faces or plates."""

    PRIVACY_CONCEPTS = {"face", "license plate"}

    async def segment(self, jpeg: bytes, concept: str, width: int, height: int) -> SamResult:
        if concept in self.PRIVACY_CONCEPTS:
            return SamResult([], 0)
        mask = np.zeros((height, width), dtype=np.uint8)
        center, axes = (width // 2, height // 2), (width // 6, height // 8)
        cv2.ellipse(mask, center, axes, 0, 0, 360, 1, thickness=-1)
        box = Box(center[0] - axes[0], center[1] - axes[1], center[0] + axes[0] + 1, center[1] + axes[1] + 1)
        return SamResult([Detection("0", box, mask.astype(bool), 0.9)], 0)
