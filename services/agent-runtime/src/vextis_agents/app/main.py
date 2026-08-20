from fastapi import FastAPI
from pydantic import BaseModel

from vextis_agents.app.config import get_settings


class HealthResponse(BaseModel):
    status: str
    service: str
    environment: str


def create_app() -> FastAPI:
    settings = get_settings()
    application = FastAPI(
        title="Vextis Agent Runtime",
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
    )

    @application.get("/health", response_model=HealthResponse, tags=["platform"])
    async def health() -> HealthResponse:
        return HealthResponse(
            status="UP",
            service="agent-runtime",
            environment=settings.environment,
        )

    return application


app = create_app()
