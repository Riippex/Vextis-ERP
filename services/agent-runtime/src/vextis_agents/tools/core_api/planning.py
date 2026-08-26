import asyncio
from collections.abc import Awaitable, Callable
from typing import Protocol

import httpx
from google.auth.credentials import Credentials
from google.auth.transport.requests import Request
from google.oauth2.id_token import fetch_id_token_credentials
from pydantic import BaseModel, ConfigDict, Field

from vextis_agents.app.config import Settings
from vextis_agents.workflows.order_to_cash.events import (
    DomainEvent,
    PurchaseOrderReceivedV2,
    WorkflowApprovalDecidedV1,
)
from vextis_agents.workflows.order_to_cash.planning import GeneratedPlan, PlanningContext


class PlanningResult(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    id: str
    state: str
    correlation_id: str = Field(alias="correlationId")
    updated_at: str = Field(alias="updatedAt")


class ReservationResult(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    id: str
    order_id: str = Field(alias="orderId")
    sku: str
    quantity: int
    status: str
    created_at: str = Field(alias="createdAt")


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

    async def request_approval(
        self, event: PurchaseOrderReceivedV2, context: PlanningContext, recommendation: str
    ) -> PlanningResult: ...

    async def reserve_stock(
        self, event: WorkflowApprovalDecidedV1, sku: str, quantity: int
    ) -> ReservationResult: ...


class CoreToolRejectedError(RuntimeError):
    """Enterprise Core deterministically rejected the requested transition."""


class CoreToolUnavailableError(RuntimeError):
    """The tool could not obtain a definitive result and Pub/Sub should retry."""


IdentityTokenProvider = Callable[[], Awaitable[str]]


class GoogleIdentityTokenProvider:
    """Caches an ADC-backed ID token for one private Cloud Run audience."""

    def __init__(self, audience: str) -> None:
        self._audience = audience
        self._credentials: Credentials | None = None
        self._request = Request()
        self._lock = asyncio.Lock()

    async def __call__(self) -> str:
        async with self._lock:
            credentials = self._credentials
            if credentials is None:
                credentials = await asyncio.to_thread(
                    fetch_id_token_credentials,
                    self._audience,
                    self._request,
                )
                if credentials is None:
                    raise RuntimeError("Google identity credentials are unavailable")
                self._credentials = credentials
            if not credentials.valid:
                await asyncio.to_thread(credentials.refresh, self._request)
            token = credentials.token
            if not isinstance(token, str):
                raise RuntimeError("Google identity credentials did not produce an ID token")
            return token


class EnterpriseCorePlanningClient:
    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
        identity_token_provider: IdentityTokenProvider | None = None,
    ) -> None:
        if settings.agent_tools_token is None:
            raise ValueError("VEXTIS_AGENT_TOOLS_TOKEN is required when Pub/Sub push is enabled")
        self._base_url = settings.enterprise_core_url.rstrip("/")
        self._service_token = settings.agent_tools_token.get_secret_value()
        self._coordinator_agent_id = settings.coordinator_logical_agent_id
        self._inventory_agent_id = settings.inventory_agent_id
        self._transport = transport
        self._identity_token_provider = identity_token_provider
        if settings.enterprise_core_audience and identity_token_provider is None:
            self._identity_token_provider = GoogleIdentityTokenProvider(
                settings.enterprise_core_audience
            )

    async def _headers(
        self,
        event: DomainEvent,
        correlation_id: str,
        idempotency_key: str,
        agent_id: str | None = None,
    ) -> dict[str, str]:
        headers = {
            "Authorization": f"Bearer {self._service_token}",
            "X-Tenant-Id": event.tenant_id,
            "X-Agent-Id": agent_id or self._coordinator_agent_id,
            "X-Correlation-Id": correlation_id,
            "Idempotency-Key": idempotency_key,
        }
        if self._identity_token_provider is not None:
            try:
                identity_token = await self._identity_token_provider()
            except Exception as exception:
                raise CoreToolUnavailableError(
                    "Cloud Run identity token could not be obtained"
                ) from exception
            headers["X-Serverless-Authorization"] = f"Bearer {identity_token}"
        return headers

    async def start_planning(self, event: PurchaseOrderReceivedV2) -> PlanningContext:
        execution_id = event.payload.execution_id
        headers = await self._headers(event, event.correlation_id, str(event.event_id))
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
        headers = await self._headers(
            event,
            context.correlation_id,
            f"{event.event_id}:record-plan",
        )
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
        headers = await self._headers(
            event,
            context.correlation_id,
            f"{event.event_id}:evaluate-readiness",
        )
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

    async def request_approval(
        self, event: PurchaseOrderReceivedV2, context: PlanningContext, recommendation: str
    ) -> PlanningResult:
        headers = await self._headers(
            event,
            context.correlation_id,
            f"{event.event_id}:request-approval",
        )
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(10.0, connect=3.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    f"/internal/agent-tools/v1/workflows/{context.id}/approval",
                    headers=headers,
                    json={"recommendation": recommendation},
                )
        except httpx.HTTPError as exception:
            raise CoreToolUnavailableError("Enterprise Core could not be reached") from exception
        if 200 <= response.status_code < 300:
            return PlanningResult.model_validate(response.json())
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")
        raise CoreToolRejectedError(
            f"Enterprise Core rejected approval request with {response.status_code}"
        )

    async def reserve_stock(
        self, event: WorkflowApprovalDecidedV1, sku: str, quantity: int
    ) -> ReservationResult:
        headers = await self._headers(
            event,
            event.correlation_id,
            f"{event.event_id}:reserve:{sku.upper()}",
            self._inventory_agent_id,
        )
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(10.0, connect=3.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    "/internal/agent-tools/v1/inventory/reservations",
                    headers=headers,
                    json={"orderId": str(event.payload.order_id), "sku": sku, "quantity": quantity},
                )
        except httpx.HTTPError as exception:
            raise CoreToolUnavailableError("Enterprise Core could not be reached") from exception
        if 200 <= response.status_code < 300:
            return ReservationResult.model_validate(response.json())
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")
        raise CoreToolRejectedError(
            f"Enterprise Core rejected stock reservation with {response.status_code}"
        )
