from decimal import Decimal
from enum import StrEnum
from typing import Annotated, Protocol

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class PlanningDepartment(StrEnum):
    CRM_SALES = "CRM_SALES"
    INVENTORY_OPERATIONS = "INVENTORY_OPERATIONS"
    FINANCE_BILLING = "FINANCE_BILLING"


class GeneratedPlanStep(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sequence: Annotated[int, Field(ge=1, le=5)]
    department: PlanningDepartment
    objective: Annotated[str, Field(min_length=1, max_length=500)]
    requires_approval: bool


class ExtractedOrderLine(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sku: Annotated[str, Field(min_length=1, max_length=100, pattern=r"^[A-Za-z0-9._-]+$")]
    quantity: Annotated[int, Field(ge=1, le=1_000_000)]
    unit_price: Annotated[Decimal | None, Field(max_digits=19, decimal_places=2)] = None

    @field_validator("unit_price")
    @classmethod
    def ensure_positive_unit_price(cls, value: Decimal | None) -> Decimal | None:
        # Google GenAI's structured-output schema rejects the JSON Schema
        # `exclusiveMinimum` emitted by Field(gt=0). Keep the invariant in
        # runtime validation so model output still cannot contain zero or a
        # negative price.
        if value is not None and value <= 0:
            raise ValueError("Unit price must be greater than zero")
        return value


class GeneratedPlan(BaseModel):
    model_config = ConfigDict(extra="forbid")

    summary: Annotated[str, Field(min_length=1, max_length=500)]
    steps: Annotated[list[GeneratedPlanStep], Field(min_length=1, max_length=5)]
    order_lines: Annotated[list[ExtractedOrderLine], Field(min_length=1, max_length=20)]
    requested_payment_terms_days: Annotated[int, Field(ge=0, le=365)]
    currency: Annotated[str | None, Field(pattern=r"^[A-Z]{3}$")] = None

    @model_validator(mode="after")
    def ensure_contiguous_sequence(self) -> "GeneratedPlan":
        if [step.sequence for step in self.steps] != list(range(1, len(self.steps) + 1)):
            raise ValueError("Plan step sequence must be contiguous and start at 1")
        normalized_skus = [line.sku.upper() for line in self.order_lines]
        if len(set(normalized_skus)) != len(normalized_skus):
            raise ValueError("Extracted order line SKUs must be unique")
        has_prices = [line.unit_price is not None for line in self.order_lines]
        if any(has_prices) != all(has_prices) or (all(has_prices) != (self.currency is not None)):
            raise ValueError("Plan pricing requires every unit price and one currency together")
        return self


class PlanningContext(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    id: str
    state: str
    correlation_id: str = Field(alias="correlationId")
    updated_at: str = Field(alias="updatedAt")
    goal: str
    purchase_order_number: str = Field(alias="purchaseOrderNumber")
    customer_name: str = Field(alias="customerName")
    document_uri: str = Field(alias="documentUri", pattern=r"^gs://")
    readiness_evaluated: bool = Field(alias="readinessEvaluated")
    approval_status: str | None = Field(default=None, alias="approvalStatus")


class PlanGenerator(Protocol):
    @property
    def model_id(self) -> str: ...

    async def generate(self, context: PlanningContext) -> GeneratedPlan: ...


class PlanGenerationUnavailableError(RuntimeError):
    """Gemini did not produce a validated plan and the event should be retried."""
