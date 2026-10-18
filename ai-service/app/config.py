from pathlib import Path

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=Path(__file__).resolve().parents[1] / '.env',
        env_file_encoding='utf-8', extra='ignore',
    )
    gemini_api_key: SecretStr = SecretStr('')
    gemini_model: str = 'gemini-3.1-flash-lite'
    ai_enabled: bool = True
    spring_boot_internal_url: str = 'http://127.0.0.1:8080'
    ai_internal_token: SecretStr = SecretStr('')
    max_tool_calls: int = Field(default=6, ge=1, le=6)
    max_rounds: int = Field(default=4, ge=1, le=4)
    max_output_tokens: int = Field(default=1200, ge=128, le=2048)
    max_total_tokens: int = Field(default=20000, ge=1000, le=30000)
    request_timeout: float = Field(default=70, ge=1, le=75)
