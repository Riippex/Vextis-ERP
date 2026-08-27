from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Environment-backed runtime settings without embedded credentials."""

    model_config = SettingsConfigDict(env_prefix="VEXTIS_", case_sensitive=False)

    environment: str = "local"
    enterprise_core_url: str = "http://localhost:8080"
    enterprise_core_audience: str | None = None
    agent_tools_token: SecretStr | None = None
    coordinator_agent_id: str = "coordinator-agent"
    coordinator_logical_agent_id: str = "vextis_coordinator"
    crm_agent_id: str = "vextis_crm_agent"
    inventory_agent_id: str = "vextis_inventory_agent"
    billing_agent_id: str = "vextis_billing_agent"
    pubsub_push_enabled: bool = False
    chat_enabled: bool = False
    core_callback_token: SecretStr | None = None
    live_enabled: bool = False
    live_model: str | None = None
    gemini_model: str | None = None
    memory_bank_enabled: bool = False
    memory_bank_agent_engine_id: str | None = None
    google_cloud_project: str | None = Field(default=None, validation_alias="GOOGLE_CLOUD_PROJECT")
    google_cloud_location: str = Field(
        default="us-central1",
        validation_alias="GOOGLE_CLOUD_LOCATION",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
