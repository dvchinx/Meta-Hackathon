from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # Local dev reads the repo's .env; containers get real environment variables.
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), extra="ignore")

    meta_api_key: str = ""
    meta_api_base_url: str = "https://api.meta.ai/v1"
    # false = FakeSamClient, no credits spent.
    meta_api_enabled: bool = False
    sam_model: str = "sam-3.1"
    sam_price_per_image_usd: float = 0.0025
    sam_timeout_seconds: float = 60.0

    # Images are downscaled before any model call: cheaper, faster and enough for segmentation.
    max_image_side: int = 1024
    jpeg_quality: int = 85


@lru_cache
def get_settings() -> Settings:
    return Settings()
