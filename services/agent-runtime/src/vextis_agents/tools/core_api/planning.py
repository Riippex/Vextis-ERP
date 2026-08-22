from typing import Protocol

import httpx
from pydantic import BaseModel, ConfigDict, Field

from vextis_agents.app.config import Settings
from vextis_agents.workflows.order_to_cash.events import PurchaseOrderReceivedV2
from vextis_agents.workflows.order_to_cash.planning import GeneratedPlan, PlanningContext


class PlanningResult(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    id: str
    state: str
    correlation_id: str = Field(alias="correlationId")
    updated_at: str = Field(alias="updatedAt")


class PlanningTool(Protocol):
    async def start_planning(self, event: PurchaseOrderReceivedV2) -> PlanningContext: ...

    async def record_plan(
        self,
        event: PurchaseOrderReceivedV2,
        context: PlanningContext,
        plan: GeneratedPlan,
        model_id: str,
    ) -> PlanningResult: ...

    async def evaluate_readiness(
        self, event: PurchaseOrderReceivedV2, context: PlanningContext
    ) -> PlanningResult: ...


class CoreToolRejectedError(RuntimeError):
    """Enterprise Core deterministically rejected the requested transition."""


class CoreToolUnavailableError(RuntimeError):
    """The tool could not obtain a definitive result and Pub/Sub should retry."""


class EnterpriseCorePlanningClient:
    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        if settings.agent_tools_token is None:
            raise ValueError("VEXTIS_AGENT_TOOLS_TOKEN is required when Pub/Sub push is enabled")
        self._base_url = settings.enterprise_core_url.rstrip("/")
        self._service_token = settings.agent_tools_token.get_secret_value()
        self._agent_id = settings.coordinator_agent_id
        self._transport = transport

    async def start_planning(self, event: PurchaseOrderReceivedV2) -> PlanningContext:
        execution_id = event.payload.execution_id
        headers = {
            "Authorization": f"Bearer {self._service_token}",
            "X-Tenant-Id": event.tenant_id,
            "X-Agent-Id": self._agent_id,
            "X-Correlation-Id": event.correlation_id,
            "Idempotency-Key": str(event.event_id),
        }
        payload = {
            "eventId": str(event.event_id),
            "documentUri": event.payload.document_uri,
        }
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(10.0, connect=3.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    f"/internal/agent-tools/v1/workflows/{execution_id}/planning",
                    headers=headers,
                    json=payload,
                )
        except httpx.HTTPError as exception:
            raise CoreToolUnavailableError("Enterprise Core could not be reached") from exception

        if 200 <= response.status_code < 300:
            return PlanningContext.model_validate(response.json())
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")
        raise CoreToolRejectedError(
            f"Enterprise Core rejected planning with {response.status_code}"
        )

    async def record_plan(
        self,
        event: PurchaseOrderReceivedV2,
        context: PlanningContext,
        plan: GeneratedPlan,
        model_id: str,
    ) -> PlanningResult:
        headers = {
            "Authorization": f"Bearer {self._service_token}",
            "X-Tenant-Id": event.tenant_id,
            "X-Agent-Id": self._agent_id,
            "X-Correlation-Id": context.correlation_id,
            "Idempotency-Key": f"{event.event_id}:record-plan",
        }
        payload = {
            "modelId": model_id,
            "summary": plan.summary,
            "orderLines": [line.model_dump() for line in plan.order_lines],
            "requestedPaymentTermsDays": plan.requested_payment_terms_days,
            "steps": [
                {
                    "sequence": step.sequence,
                    "department": step.department.value,
                    "objective": step.objective,
                    "requiresApproval": step.requires_approval,
                }
                for step in plan.steps
            ],
        }
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(10.0, connect=3.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    f"/internal/agent-tools/v1/workflows/{context.id}/plan",
                    headers=headers,
                    json=payload,
                )
        except httpx.HTTPError as exception:
            raise CoreToolUnavailableError("Enterprise Core could not be reached") from exception

        if 200 <= response.status_code < 300:
            return PlanningResult.model_validate(response.json())
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")
        raise CoreToolRejectedError(
            f"Enterprise Core rejected the structured plan with {response.status_code}"
        )

    async def evaluate_readiness(
        self, event: PurchaseOrderReceivedV2, context: PlanningContext
    ) -> PlanningResult:
        headers = {
            "Authorization": f"Bearer {self._service_token}",
            "X-Tenant-Id": event.tenant_id,
            "X-Agent-Id": self._agent_id,
            "X-Correlation-Id": context.correlation_id,
            "Idempotency-Key": f"{event.event_id}:evaluate-readiness",
        }
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(10.0, connect=3.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    f"/internal/agent-tools/v1/workflows/{context.id}/readiness",
                    headers=headers,
                )
        except httpx.HTTPError as exception:
            raise CoreToolUnavailableError("Enterprise Core could not be reached") from exception
        if 200 <= response.status_code < 300:
            return PlanningResult.model_validate(response.json())
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")
        raise CoreToolRejectedError(
            f"Enterprise Core rejected readiness evaluation with {response.status_code}"
        )
