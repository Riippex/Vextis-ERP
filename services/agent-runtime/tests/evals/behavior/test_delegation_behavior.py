"""
Delegation: does the coordinator actually route department work to the
specialist that owns it, rather than answering for them?
"""

import json

import httpx
import pytest

from tests.evals.behavior.conftest import ask, build_fleet
from vextis_agents.app.config import Settings

pytestmark = pytest.mark.model_eval

CUSTOMER_ID = "77cc63cc-3c91-4d80-a918-605b7f231cf8"

SPECIALISTS = {"vextis_crm_agent", "vextis_inventory_agent", "vextis_billing_agent"}


def _core(request: httpx.Request) -> httpx.Response:
    """Enterprise Core answering the three read tools with distinctive values."""
    path = request.url.path
    if path.endswith("/customers/lookup"):
        return httpx.Response(
            200,
            json={"id": CUSTOMER_ID, "legalName": "Acme Colombia S.A.S.", "active": True},
        )
    if "/inventory/stock/" in path:
        return httpx.Response(200, json={"sku": "VXT-CHAIR-01", "availableQuantity": 137})
    if path.endswith("/credit"):
        return httpx.Response(
            200,
            json={"customerId": CUSTOMER_ID, "standing": "REVIEW", "maxPaymentTermsDays": 15},
        )
    return httpx.Response(404, json={"error": f"unexpected path {path}"})


@pytest.mark.asyncio
async def test_inventory_question_reaches_the_inventory_specialist(settings: Settings) -> None:
    fleet = build_fleet(settings, _core)

    turn = await ask(fleet, "How many units of VXT-CHAIR-01 do we have available right now?")

    engaged = SPECIALISTS.intersection(turn.authors)
    assert engaged, f"no specialist was engaged; authors were {turn.authors}"
    assert turn.mentions_any("137"), (
        f"reply did not carry the authoritative quantity from Enterprise Core: {turn.reply}"
    )


@pytest.mark.asyncio
async def test_cross_department_question_engages_more_than_one_specialist(
    settings: Settings,
) -> None:
    fleet = build_fleet(settings, _core)

    turn = await ask(
        fleet,
        "Acme Colombia S.A.S. wants 20 units of VXT-CHAIR-01 on 30 day terms. "
        "Confirm the customer, the stock, and whether their credit standing supports it.",
    )

    engaged = SPECIALISTS.intersection(turn.authors)
    assert len(engaged) >= 2, (
        f"expected at least two specialists for a cross-department question, got {engaged} "
        f"from authors {turn.authors}"
    )


@pytest.mark.asyncio
async def test_reports_the_credit_standing_core_returned_not_a_convenient_one(
    settings: Settings,
) -> None:
    fleet = build_fleet(settings, _core)

    turn = await ask(
        fleet,
        "Can Acme Colombia S.A.S. buy on 30 day payment terms? Their customer id is "
        f"{CUSTOMER_ID}.",
    )

    # Core says REVIEW with a 15 day ceiling; agreeing to 30 days would mean the
    # model overrode the authoritative answer.
    assert turn.mentions_any("review", "15"), (
        f"reply did not reflect the credit standing Enterprise Core returned: {turn.reply}"
    )


@pytest.mark.asyncio
async def test_specialists_reach_core_rather_than_answering_from_memory(
    settings: Settings,
) -> None:
    seen: list[str] = []

    def recording_core(request: httpx.Request) -> httpx.Response:
        seen.append(request.url.path)
        return _core(request)

    fleet = build_fleet(settings, recording_core)

    await ask(fleet, "What is the available quantity of VXT-CHAIR-01?")

    assert any("/inventory/stock/" in path for path in seen), (
        "the inventory question never reached Enterprise Core; paths seen: "
        + json.dumps(seen)
    )
