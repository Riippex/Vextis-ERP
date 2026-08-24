import base64
import binascii
from datetime import datetime
from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, TypeAdapter, ValidationError, model_validator

MAX_PUSH_BODY_BYTES = 256 * 1024
MAX_EVENT_BYTES = 128 * 1024


class EventActor(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["USER", "AGENT", "SYSTEM"]
    id: Annotated[str, Field(min_length=1, max_length=150)]


class PurchaseOrderPayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    purchase_order_id: UUID
    execution_id: UUID
    document_uri: Annotated[str, Field(pattern=r"^gs://", max_length=1000)]


class PurchaseOrderReceivedV2(BaseModel):
    model_config = ConfigDict(extra="forbid")

    event_id: UUID
    event_type: Literal["purchase_order.received"]
    event_version: Literal[2]
    occurred_at: datetime
    producer: Literal["enterprise-core"]
    tenant_id: Annotated[str, Field(min_length=1, max_length=100)]
    correlation_id: Annotated[str, Field(min_length=1, max_length=100)]
    causation_id: str | None = None
    actor: EventActor
    payload: PurchaseOrderPayload


class ApprovedOrderLine(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sku: Annotated[str, Field(min_length=1, max_length=100, pattern=r"^[A-Za-z0-9._-]+$")]
    quantity: Annotated[int, Field(ge=1, le=1_000_000)]


class ApprovalDecidedPayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    approval_id: UUID
    execution_id: UUID
    order_id: UUID | None = None
    order_lines: Annotated[list[ApprovedOrderLine], Field(max_length=20)] | None = None
    status: Literal["APPROVED", "REJECTED"]
    recommendation: Annotated[str, Field(min_length=1, max_length=500)]
    evidence: list[dict[str, str]]
    decided_by: Annotated[str, Field(min_length=1, max_length=150)]
    reason: Annotated[str | None, Field(max_length=500)] = None

    @model_validator(mode="after")
    def approved_event_has_reservation_context(self) -> "ApprovalDecidedPayload":
        if self.status == "APPROVED" and (self.order_id is None or not self.order_lines):
            raise ValueError("Approved workflow event requires order reservation context")
        return self


class WorkflowApprovalDecidedV1(BaseModel):
    model_config = ConfigDict(extra="forbid")

    event_id: UUID
    event_type: Literal["workflow.approval.decided"]
    event_version: Literal[1]
    occurred_at: datetime
    producer: Literal["enterprise-core"]
    tenant_id: Annotated[str, Field(min_length=1, max_length=100)]
    correlation_id: Annotated[str, Field(min_length=1, max_length=100)]
    causation_id: UUID
    actor: EventActor
    payload: ApprovalDecidedPayload


DomainEvent = PurchaseOrderReceivedV2 | WorkflowApprovalDecidedV1
DOMAIN_EVENT_ADAPTER = TypeAdapter(DomainEvent)


class PubSubPushMessage(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    data: str
    message_id: str | None = Field(default=None, alias="messageId")


class PubSubPushEnvelope(BaseModel):
    model_config = ConfigDict(extra="ignore")

    message: PubSubPushMessage
    subscription: str | None = None


class InvalidEventError(ValueError):
    """Raised for malformed or unsupported events that must never reach a tool."""


def decode_domain_event(body: bytes) -> DomainEvent:
    if len(body) > MAX_PUSH_BODY_BYTES:
        raise InvalidEventError("Pub/Sub push body exceeds the accepted size")
    try:
        envelope = PubSubPushEnvelope.model_validate_json(body)
        encoded = envelope.message.data.encode("ascii")
        decoded = base64.b64decode(encoded, validate=True)
        if len(decoded) > MAX_EVENT_BYTES:
            raise InvalidEventError("Event payload exceeds the accepted size")
        return DOMAIN_EVENT_ADAPTER.validate_json(decoded)
    except (ValidationError, ValueError, UnicodeError, binascii.Error) as exception:
        if isinstance(exception, InvalidEventError):
            raise
        raise InvalidEventError(
            "Pub/Sub push does not contain a supported domain event"
        ) from exception
