from typing import cast
from uuid import UUID

import pytest
from google.adk.agents import LlmAgent
from google.adk.models import Gemini

from vextis_agents.app.config import Settings
from vextis_agents.coordinator.agent import build_coordinator, build_planning_agent
from vextis_agents.tools.core_api.business_reads import (
    CreditContext,
    CustomerContext,
    StockContext,
)
from vextis_agents.workflows.order_to_cash.planning import GeneratedPlan


def test_coordinator_requires_explicit_model_configuration() -> None:
    with pytest.raises(ValueError, match="VEXTIS_GEMINI_MODEL"):
        build_coordinator(Settings(gemini_model=None))


def test_coordinator_uses_configured_gemini_model() -> None:
    coordinator = build_coordinator(Settings(gemini_model="gemini-test-model"))

    assert coordinator.name == "vextis_coordinator"
    assert coordinator.model == "gemini-test-model"


def test_coordinator_routes_gemini_to_its_configured_vertex_location() -> None:
    coordinator = build_coordinator(
        Settings(
            gemini_model="gemini-3.5-flash",
            gemini_location="us",
            google_cloud_project="vextis-test",
        )
    )

    assert isinstance(coordinator.model, Gemini)
    assert coordinator.model.model == "gemini-3.5-flash"
    assert coordinator.model.client_kwargs == {
        "vertexai": True,
        "project": "vextis-test",
        "location": "us",
    }


def test_live_override_routes_to_its_distinct_vertex_location() -> None:
    coordinator = build_coordinator(
        Settings(
            gemini_model="gemini-3.5-flash",
            google_cloud_project="vextis-test",
        ),
        model="gemini-live-2.5-flash-native-audio",
        model_location="us-central1",
    )

    assert isinstance(coordinator.model, Gemini)
    assert coordinator.model.model == "gemini-live-2.5-flash-native-audio"
    assert coordinator.model.client_kwargs == {
        "vertexai": True,
        "project": "vextis-test",
        "location": "us-central1",
    }


def test_coordinator_registers_the_three_bounded_specialists() -> None:
    coordinator = build_coordinator(Settings(gemini_model="gemini-test-model"))
    specialists = [cast(LlmAgent, agent) for agent in coordinator.sub_agents]

    assert [agent.name for agent in specialists] == [
        "vextis_crm_agent",
        "vextis_inventory_agent",
        "vextis_billing_agent",
    ]
    assert all(agent.model == "gemini-test-model" for agent in specialists)
    assert all(agent.parent_agent is coordinator for agent in specialists)
    assert all(not agent.tools for agent in specialists)
    for agent in specialists:
        assert isinstance(agent.instruction, str)
        assert "Enterprise Core" in agent.instruction


class FakeBusinessReads:
    async def lookup_customer(self, legal_name: str) -> CustomerContext | None:
        return None

    async def get_stock(self, sku: str) -> StockContext | None:
        return None

    async def get_credit(self, customer_id: UUID) -> CreditContext | None:
        return None


def test_tenant_bound_coordinator_gives_each_specialist_one_read_tool() -> None:
    coordinator = build_coordinator(
        Settings(gemini_model="gemini-test-model"),
        "demo-tenant",
        core_reads=FakeBusinessReads(),
    )
    specialists = [cast(LlmAgent, agent) for agent in coordinator.sub_agents]

    assert [[getattr(tool, "__name__", None) for tool in agent.tools] for agent in specialists] == [
        ["lookup_customer"],
        ["get_stock"],
        ["get_credit"],
    ]


def test_planning_agent_enforces_structured_output() -> None:
    planner = build_planning_agent(Settings(gemini_model="gemini-3.5-flash"))

    assert planner.model == "gemini-3.5-flash"
    assert planner.output_schema is GeneratedPlan
    assert planner.output_key == "workflow_plan"
    assert planner.sub_agents == []
