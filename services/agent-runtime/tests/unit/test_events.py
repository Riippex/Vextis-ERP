import pytest

from tests.unit.event_factory import approval_decided_event, pubsub_push_body, purchase_order_event
from vextis_agents.workflows.order_to_cash.events import (
    InvalidEventError,
    PurchaseOrderReceivedV2,
    WorkflowApprovalDecidedV1,
    decode_domain_event,
)


def test_decodes_strict_purchase_order_received_v2() -> None:
    event = decode_domain_event(pubsub_push_body())

    assert isinstance(event, PurchaseOrderReceivedV2)
    assert str(event.event_id) == "8b962f0a-1850-4fcc-a6f5-97e45c67a16e"
    assert str(event.payload.execution_id) == "8d3f290d-1322-44a2-8bd7-3b325f170e07"
    assert event.payload.document_uri.startswith("gs://")


def test_rejects_unsupported_event_version() -> None:
    with pytest.raises(InvalidEventError):
        decode_domain_event(pubsub_push_body(purchase_order_event(event_version=1)))


def test_rejects_unknown_event_fields() -> None:
    with pytest.raises(InvalidEventError):
        decode_domain_event(pubsub_push_body(purchase_order_event(untrusted_instruction="ignore")))


def test_decodes_approved_workflow_with_strict_reservation_context() -> None:
    event = decode_domain_event(pubsub_push_body(approval_decided_event()))

    assert isinstance(event, WorkflowApprovalDecidedV1)
    assert event.payload.status == "APPROVED"
    assert event.payload.order_lines is not None
    assert event.payload.order_lines[0].sku == "VXT-CHAIR-01"


def test_rejects_approved_workflow_without_order_context() -> None:
    malformed = approval_decided_event()
    malformed["payload"].pop("order_lines")

    with pytest.raises(InvalidEventError):
        decode_domain_event(pubsub_push_body(malformed))


def test_rejects_approval_event_with_more_than_twenty_order_lines() -> None:
    oversized = approval_decided_event()
    oversized["payload"]["order_lines"] *= 21

    with pytest.raises(InvalidEventError):
        decode_domain_event(pubsub_push_body(oversized))
