from fastapi import FastAPI
from pydantic import BaseModel

from vextis_agents.app.config import Settings, get_settings
from vextis_agents.app.pubsub import create_pubsub_router
from vextis_agents.tools.core_api.planning import EnterpriseCorePlanningClient, PlanningTool
from vextis_agents.workflows.order_to_cash.gemini_planner import AdkGeminiPlanGenerator
from vextis_agents.workflows.order_to_cash.handler import (
    PurchaseOrderReceivedHandler,
    WorkflowApprovalDecidedHandler,
)
from vextis_agents.workflows.order_to_cash.planning import PlanGenerator


class HealthResponse(BaseModel):
    status: str
    service: str
    environment: str


def create_app(
    settings: Settings | None = None,
    planning_tool: PlanningTool | None = None,
    plan_generator: PlanGenerator | None = None,
) -> FastAPI:
    settings = settings or get_settings()
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

    if settings.pubsub_push_enabled:
        tool = planning_tool or EnterpriseCorePlanningClient(settings)
        generator = plan_generator or AdkGeminiPlanGenerator(settings)
        application.include_router(
            create_pubsub_router(
                PurchaseOrderReceivedHandler(tool, generator),
                WorkflowApprovalDecidedHandler(tool),
            )
        )

    return application


app = create_app()
