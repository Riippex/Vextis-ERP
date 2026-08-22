import pytest

from tests.unit.event_factory import pubsub_push_body, purchase_order_event
from vextis_agents.workflows.order_to_cash.events import (
    InvalidEventError,
    decode_purchase_order_event,
)


def test_decodes_strict_purchase_order_received_v2() -> None:
    event = decode_purchase_order_event(pubsub_push_body())

    assert str(event.event_id) == "8b962f0a-1850-4fcc-a6f5-97e45c67a16e"
    assert str(event.payload.execution_id) == "8d3f290d-1322-44a2-8bd7-3b325f170e07"
    assert event.payload.document_uri.startswith("gs://")


def test_rejects_unsupported_event_version() -> None:
    with pytest.raises(InvalidEventError):
        decode_purchase_order_event(pubsub_push_body(purchase_order_event(event_version=1)))


def test_rejects_unknown_event_fields() -> None:
    with pytest.raises(InvalidEventError):
        decode_purchase_order_event(pubsub_push_body(purchase_order_event(untrusted_instruction="ignore")))
