from fastapi import FastAPI
from pydantic import BaseModel

from vextis_agents.app.chat import create_chat_router
from vextis_agents.app.config import Settings, get_settings
from vextis_agents.app.pubsub import create_pubsub_router
from vextis_agents.live.ws_router import create_live_router
from vextis_agents.memory import AgentMemory, create_agent_memory
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
    agent_memory: AgentMemory | None = None,
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
        tool: PlanningTool = (
            planning_tool if planning_tool is not None else EnterpriseCorePlanningClient(settings)
        )
        generator = plan_generator or AdkGeminiPlanGenerator(settings)
        application.include_router(
            create_pubsub_router(
                PurchaseOrderReceivedHandler(tool, generator),
                WorkflowApprovalDecidedHandler(tool),
            )
        )

    if settings.chat_enabled:
        memory = agent_memory if agent_memory is not None else create_agent_memory(settings)
        application.include_router(create_chat_router(settings, memory))

    if settings.live_enabled:
        application.include_router(create_live_router(settings))

    return application


app = create_app()
