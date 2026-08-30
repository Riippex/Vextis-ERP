from decimal import Decimal

import pytest
from google.genai import _transformers
from pydantic import ValidationError

from vextis_agents.workflows.order_to_cash.planning import ExtractedOrderLine, GeneratedPlan


def test_generated_plan_schema_is_accepted_by_google_genai() -> None:
    schema = _transformers.t_schema(None, GeneratedPlan)

    assert schema is not None


@pytest.mark.parametrize("unit_price", [Decimal("0"), Decimal("-0.01")])
def test_order_line_rejects_non_positive_unit_price(unit_price: Decimal) -> None:
    with pytest.raises(ValidationError, match="greater than zero"):
        ExtractedOrderLine(sku="VXT-CHAIR-01", quantity=1, unit_price=unit_price)


def test_order_line_accepts_positive_unit_price() -> None:
    line = ExtractedOrderLine(
        sku="VXT-CHAIR-01",
        quantity=1,
        unit_price=Decimal("129.99"),
    )

    assert line.unit_price == Decimal("129.99")
