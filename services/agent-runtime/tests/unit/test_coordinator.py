import pytest

from vextis_agents.app.config import Settings
from vextis_agents.coordinator.agent import build_coordinator, build_planning_agent
from vextis_agents.workflows.order_to_cash.planning import GeneratedPlan


def test_coordinator_requires_explicit_model_configuration() -> None:
    with pytest.raises(ValueError, match="VEXTIS_GEMINI_MODEL"):
        build_coordinator(Settings(gemini_model=None))


def test_coordinator_uses_configured_gemini_model() -> None:
    coordinator = build_coordinator(Settings(gemini_model="gemini-test-model"))

    assert coordinator.name == "vextis_coordinator"
    assert coordinator.model == "gemini-test-model"


def test_planning_agent_enforces_structured_output() -> None:
    planner = build_planning_agent(Settings(gemini_model="gemini-3.5-flash"))

    assert planner.model == "gemini-3.5-flash"
    assert planner.output_schema is GeneratedPlan
    assert planner.output_key == "workflow_plan"
