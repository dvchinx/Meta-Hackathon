import asyncio
import json
from pathlib import Path

import httpx
import pytest

from app.sam.client import MetaSamClient, SamError

FIXTURE = json.loads((Path(__file__).parent / "fixtures" / "sam_pothole_960x720.json").read_text())


def client_returning(status: int, payload: dict, seen: list) -> MetaSamClient:
    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return httpx.Response(status, json=payload)

    http = httpx.AsyncClient(base_url="https://api.test/v1", transport=httpx.MockTransport(handler))
    return MetaSamClient(http, "sam-3.1")


def test_sends_one_concept_never_stores_and_parses_the_reply():
    seen: list[httpx.Request] = []
    sam = client_returning(200, FIXTURE, seen)

    result = asyncio.run(sam.segment(b"\xff\xd8jpeg", "pothole", 960, 720))

    body = json.loads(seen[0].content)
    assert seen[0].url.path == "/v1/responses"
    assert body["model"] == "sam-3.1"
    assert body["store"] is False
    assert body["stream"] is False
    assert body["metadata"] == {"include_confidence": "true", "mask_encoding": "one_bit"}
    content = body["input"][0]["content"]
    assert content[0] == {"type": "input_text", "text": "pothole"}
    assert content[1]["image_url"].startswith("data:image/jpeg;base64,")
    assert result.images_billed == 1
    assert len(result.detections) == 2


def test_http_errors_become_sam_errors():
    sam = client_returning(429, {"error": {"message": "rate limited"}}, [])

    with pytest.raises(SamError, match="429"):
        asyncio.run(sam.segment(b"jpeg", "pothole", 960, 720))


def test_incomplete_responses_are_errors():
    sam = client_returning(200, {**FIXTURE, "status": "failed"}, [])

    with pytest.raises(SamError):
        asyncio.run(sam.segment(b"jpeg", "pothole", 960, 720))
