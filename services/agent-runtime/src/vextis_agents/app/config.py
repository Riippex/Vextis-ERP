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
    live_location: str = "us-central1"
    # Unauthenticated-connection guards for the publicly reachable Live socket.
    live_auth_timeout_seconds: float = 5.0
    live_max_audio_frame_bytes: int = 65536
    live_max_text_frame_bytes: int = 4096
    # Ceiling applied on top of the expiresAt Enterprise Core returns, so a
    # misconfigured or long-lived credential still cannot hold the single
    # Agent Runtime instance indefinitely.
    live_max_session_seconds: int = 900
    gemini_model: str | None = None
    gemini_location: str = "us"
    # Documents and queries must share one embedding space; these settings
    # define it for this process. The mock is opt-in, never a fallback.
    rag_embedding_model: str = "text-embedding-004"
    rag_embedding_dimension: int = 768
    rag_embedding_location: str = "us-central1"
    rag_mock_embeddings_enabled: bool = False
    # A 0.0 floor returns the nearest chunks however unrelated they are, which
    # reads downstream as grounded evidence. Enterprise Core enforces its own
    # floor as well.
    rag_min_similarity: float = 0.55
    memory_bank_enabled: bool = False
    memory_bank_agent_engine_id: str | None = None
    memory_bank_location: str = "us-central1"
    google_cloud_project: str | None = Field(default=None, validation_alias="GOOGLE_CLOUD_PROJECT")
    google_cloud_location: str = Field(
        default="us-central1",
        validation_alias="GOOGLE_CLOUD_LOCATION",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
