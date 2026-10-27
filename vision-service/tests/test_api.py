import base64

import numpy as np
from fastapi.testclient import TestClient

from app import imaging
from app.main import app


def jpeg(height=300, width=400) -> bytes:
    rng = np.random.default_rng(0)
    return imaging.encode_jpeg(rng.integers(0, 256, (height, width, 3), dtype=np.uint8), 90)


def test_health_reports_the_fake_client():
    with TestClient(app) as client:
        assert client.get("/health").json() == {"status": "ok", "sam": "fake"}


def test_analyze_with_the_fake_client_costs_nothing():
    with TestClient(app) as client:
        response = client.post(
            "/v1/analyze",
            files={"image": ("photo.jpg", jpeg(), "image/jpeg")},
            data={"damage_type": "POTHOLE"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["damage"]["detections"] == 1
    assert 0 < body["damage"]["areaFraction"] < 0.2
    assert body["usage"] == {"samImages": 0, "costUsd": 0.0}


def test_fake_mode_never_publishes_an_unblurred_photo():
    original = jpeg()
    with TestClient(app) as client:
        body = client.post(
            "/v1/analyze", files={"image": ("p.jpg", original, "image/jpeg")}, data={"damage_type": "POTHOLE"}
        ).json()

    blurred = imaging.decode(base64.b64decode(body["blurredJpegBase64"])).astype(int)
    source = imaging.decode(original).astype(int)
    # Noise input: a fully anonymized frame differs strongly everywhere, not just in a region.
    assert abs(blurred - source).mean() > 40
    assert imaging.decode(base64.b64decode(body["markedJpegBase64"])).shape == (300, 400, 3)


def test_analyze_rejects_unknown_damage_types_and_broken_images():
    with TestClient(app) as client:
        unknown = client.post(
            "/v1/analyze", files={"image": ("p.jpg", jpeg(), "image/jpeg")}, data={"damage_type": "GRAFFITI"}
        )
        broken = client.post(
            "/v1/analyze", files={"image": ("p.jpg", b"nope", "image/jpeg")}, data={"damage_type": "POTHOLE"}
        )

    assert unknown.status_code == 422
    assert broken.status_code == 422


def test_side_by_side_returns_a_jpeg():
    with TestClient(app) as client:
        response = client.post(
            "/v1/side-by-side",
            files={"before": ("b.jpg", jpeg(), "image/jpeg"), "after": ("a.jpg", jpeg(200, 300), "image/jpeg")},
        )

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/jpeg"
    assert imaging.decode(response.content).shape[0] == 200
