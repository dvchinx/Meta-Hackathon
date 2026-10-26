import base64
from contextlib import asynccontextmanager
from typing import Annotated

import httpx
from fastapi import Depends, FastAPI, File, Form, HTTPException, Request, Response, UploadFile
from pydantic import BaseModel

from app import imaging
from app.analysis import DamageType, analyze
from app.config import Settings, get_settings
from app.sam.client import FakeSamClient, MetaSamClient, SamClient, SamError

MAX_UPLOAD_BYTES = 25 * 1024 * 1024


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    if settings.meta_api_enabled:
        if not settings.meta_api_key:
            raise RuntimeError("META_API_ENABLED=true but META_API_KEY is empty")
        http = httpx.AsyncClient(
            base_url=settings.meta_api_base_url,
            headers={"Authorization": f"Bearer {settings.meta_api_key}"},
            timeout=settings.sam_timeout_seconds,
        )
        app.state.sam = MetaSamClient(http, settings.sam_model)
        yield
        await http.aclose()
    else:
        app.state.sam = FakeSamClient()
        yield


app = FastAPI(title="civic-repairs vision-service", lifespan=lifespan)


def get_sam(request: Request) -> SamClient:
    return request.app.state.sam


async def read_upload(upload: UploadFile) -> bytes:
    data = await upload.read(MAX_UPLOAD_BYTES + 1)
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, "image too large")
    return data


class BoxOut(BaseModel):
    left: int
    top: int
    right: int
    bottom: int


class DamageOut(BaseModel):
    detections: int
    areaPx: int
    areaFraction: float
    confidence: float | None
    boxes: list[BoxOut]


class UsageOut(BaseModel):
    samImages: int
    costUsd: float


class AnalysisOut(BaseModel):
    width: int
    height: int
    damage: DamageOut
    faces: int
    plates: int
    blurredJpegBase64: str
    markedJpegBase64: str
    usage: UsageOut


@app.get("/health")
def health(settings: Annotated[Settings, Depends(get_settings)]) -> dict:
    return {"status": "ok", "sam": "meta" if settings.meta_api_enabled else "fake"}


@app.post("/v1/analyze")
async def analyze_photo(
    image: Annotated[UploadFile, File()],
    damage_type: Annotated[DamageType, Form()],
    sam: Annotated[SamClient, Depends(get_sam)],
    settings: Annotated[Settings, Depends(get_settings)],
    blur_faces: Annotated[bool, Form()] = True,
    blur_plates: Annotated[bool, Form()] = True,
) -> AnalysisOut:
    """Segments the damage, anonymizes faces and plates, and returns the public (blurred) and marked images."""
    photo = await read_upload(image)
    try:
        result = await analyze(
            photo,
            damage_type,
            sam,
            max_side=settings.max_image_side,
            jpeg_quality=settings.jpeg_quality,
            blur_faces=blur_faces,
            blur_plates=blur_plates,
        )
    except ValueError as e:
        raise HTTPException(422, str(e)) from e
    except SamError as e:
        raise HTTPException(502, str(e)) from e

    return AnalysisOut(
        width=result.width,
        height=result.height,
        damage=DamageOut(
            detections=len(result.damage_boxes),
            areaPx=result.damage_area_px,
            areaFraction=round(result.damage_area_fraction, 6),
            confidence=result.damage_confidence,
            boxes=[BoxOut(**vars(b)) for b in result.damage_boxes],
        ),
        faces=result.faces,
        plates=result.plates,
        blurredJpegBase64=base64.b64encode(result.blurred_jpeg).decode(),
        markedJpegBase64=base64.b64encode(result.marked_jpeg).decode(),
        usage=UsageOut(
            samImages=result.sam_images_billed,
            costUsd=round(result.sam_images_billed * settings.sam_price_per_image_usd, 6),
        ),
    )


@app.post("/v1/side-by-side", response_class=Response)
async def side_by_side(
    before: Annotated[UploadFile, File()],
    after: Annotated[UploadFile, File()],
    settings: Annotated[Settings, Depends(get_settings)],
) -> Response:
    """Composes before/after photos (already blurred) for Muse Spark. No model calls."""
    try:
        combined = imaging.side_by_side(
            imaging.downscale(imaging.decode(await read_upload(before)), settings.max_image_side),
            imaging.downscale(imaging.decode(await read_upload(after)), settings.max_image_side),
        )
    except ValueError as e:
        raise HTTPException(422, str(e)) from e
    return Response(imaging.encode_jpeg(combined, settings.jpeg_quality), media_type="image/jpeg")
