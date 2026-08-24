from collections.abc import AsyncIterator

import pytest
from google.genai import types

from vextis_agents.app.config import Settings
from vextis_agents.workflows.order_to_cash import gemini_planner
from vextis_agents.workflows.order_to_cash.gemini_planner import AdkGeminiPlanGenerator
from vextis_agents.workflows.order_to_cash.planning import (
    PlanGenerationUnavailableError,
    PlanningContext,
)


class FakeEvent:
    def __init__(self, output: str) -> None:
        self.content = types.Content(parts=[types.Part(text=output)])

    def is_final_response(self) -> bool:
        return True


class FakeRunner:
    def __init__(self, output: str) -> None:
        self.output = output
        self.message: types.Content | None = None
        self.closed = False

    async def run_async(self, **kwargs: object) -> AsyncIterator[FakeEvent]:
        message = kwargs["new_message"]
        assert isinstance(message, types.Content)
        self.message = message
        yield FakeEvent(self.output)

    async def close(self) -> None:
        self.closed = True


@pytest.mark.asyncio
async def test_adk_planner_attaches_document_and_parses_structured_output(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    runner = FakeRunner(
        """
        {
          "summary": "Validate customer and availability.",
          "steps": [
            {
              "sequence": 1,
              "department": "CRM_SALES",
              "objective": "Validate customer context.",
              "requires_approval": false
            }
          ],
          "order_lines": [{"sku": "VXT-CHAIR-01", "quantity": 10}],
          "requested_payment_terms_days": 30
        }
        """
    )
    monkeypatch.setattr(gemini_planner, "InMemoryRunner", lambda **_: runner)
    generator = AdkGeminiPlanGenerator(settings())

    plan = await generator.generate(context())

    assert plan.steps[0].department == "CRM_SALES"
    assert runner.message is not None
    assert runner.message.parts is not None
    assert runner.message.parts[1].file_data is not None
    assert runner.message.parts[1].file_data.file_uri == context().document_uri
    assert runner.closed is True


@pytest.mark.asyncio
async def test_adk_planner_rejects_invalid_model_output(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    runner = FakeRunner('{"summary":"Unsafe","steps":[]}')
    monkeypatch.setattr(gemini_planner, "InMemoryRunner", lambda **_: runner)
    generator = AdkGeminiPlanGenerator(settings())

    with pytest.raises(PlanGenerationUnavailableError, match="invalid plan"):
        await generator.generate(context())

    assert runner.closed is True


def settings() -> Settings:
    return Settings(
        gemini_model="gemini-3.5-flash",
        google_cloud_project="vextis-test",
        google_cloud_location="us-central1",
    )


def context() -> PlanningContext:
    return PlanningContext(
        id="8d3f290d-1322-44a2-8bd7-3b325f170e07",
        state="PLANNING",
        correlationId="corr-001",
        updatedAt="2026-08-21T03:30:02Z",
        goal="Process purchase order",
        purchaseOrderNumber="PO-2026-001",
        customerName="Acme Colombia",
        documentUri="gs://vextis-demo/orders/po-2026-001.pdf",
        readinessEvaluated=False,
    )
