import re
from typing import Literal, Protocol
from uuid import UUID, uuid4

import httpx
from pydantic import BaseModel, ConfigDict, Field

from vextis_agents.app.config import Settings
from vextis_agents.tools.core_api.planning import (
    CoreToolRejectedError,
    CoreToolUnavailableError,
    GoogleIdentityTokenProvider,
    IdentityTokenProvider,
)


class CustomerContext(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    id: UUID
    legal_name: str = Field(alias="legalName", max_length=200)
    active: bool


class StockContext(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    sku: str = Field(max_length=100)
    available_quantity: int = Field(alias="availableQuantity", ge=0)


class CreditContext(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    customer_id: UUID = Field(alias="customerId")
    standing: Literal["GOOD", "REVIEW", "BLOCKED"]
    max_payment_terms_days: int = Field(alias="maxPaymentTermsDays", ge=0, le=365)


class BusinessReadTool(Protocol):
    async def lookup_customer(self, legal_name: str) -> CustomerContext | None: ...

    async def get_stock(self, sku: str) -> StockContext | None: ...

    async def get_credit(self, customer_id: UUID) -> CreditContext | None: ...


class EnterpriseCoreBusinessReadClient:
    """Tenant-bound, authenticated adapter for the specialist read-only API."""

    def __init__(
        self,
        settings: Settings,
        tenant_id: str,
        correlation_id: str | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
        identity_token_provider: IdentityTokenProvider | None = None,
    ) -> None:
        if settings.agent_tools_token is None:
            raise ValueError("VEXTIS_AGENT_TOOLS_TOKEN is required for specialist tools")
        if not tenant_id.strip():
            raise ValueError("A trusted tenant is required for specialist tools")
        self._base_url = settings.enterprise_core_url.rstrip("/")
        self._tenant_id = tenant_id
        self._correlation_id = correlation_id or str(uuid4())
        self._service_token = settings.agent_tools_token.get_secret_value()
        self._agent_id = settings.coordinator_agent_id
        self._transport = transport
        self._identity_token_provider = identity_token_provider
        if settings.enterprise_core_audience and identity_token_provider is None:
            self._identity_token_provider = GoogleIdentityTokenProvider(
                settings.enterprise_core_audience
            )

    async def lookup_customer(self, legal_name: str) -> CustomerContext | None:
        if not 1 <= len(legal_name.strip()) <= 200:
            raise ValueError("legal_name must contain between 1 and 200 characters")
        response = await self._get(
            "/internal/agent-tools/v1/crm/customers/lookup",
            params={"legalName": legal_name},
        )
        return None if response is None else CustomerContext.model_validate(response.json())

    async def get_stock(self, sku: str) -> StockContext | None:
        if not re.fullmatch(r"[A-Za-z0-9._-]{1,100}", sku):
            raise ValueError("sku must use 1-100 letters, digits, dots, underscores, or hyphens")
        response = await self._get(f"/internal/agent-tools/v1/inventory/stock/{sku}")
        return None if response is None else StockContext.model_validate(response.json())

    async def get_credit(self, customer_id: UUID) -> CreditContext | None:
        response = await self._get(
            f"/internal/agent-tools/v1/billing/customers/{customer_id}/credit"
        )
        return None if response is None else CreditContext.model_validate(response.json())

    async def _get(
        self,
        path: str,
        *,
        params: dict[str, str] | None = None,
    ) -> httpx.Response | None:
        headers = {
            "Authorization": f"Bearer {self._service_token}",
            "X-Tenant-Id": self._tenant_id,
            "X-Agent-Id": self._agent_id,
            "X-Correlation-Id": self._correlation_id,
        }
        if self._identity_token_provider is not None:
            try:
                identity_token = await self._identity_token_provider()
            except Exception as exception:
                raise CoreToolUnavailableError(
                    "Cloud Run identity token could not be obtained"
                ) from exception
            headers["X-Serverless-Authorization"] = f"Bearer {identity_token}"
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(10.0, connect=3.0),
                transport=self._transport,
            ) as client:
                response = await client.get(path, headers=headers, params=params)
        except httpx.HTTPError as exception:
            raise CoreToolUnavailableError("Enterprise Core could not be reached") from exception
        if response.status_code == 404:
            return None
        if 200 <= response.status_code < 300:
            return response
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")
        raise CoreToolRejectedError(
            f"Enterprise Core rejected the read-only tool with {response.status_code}"
        )
