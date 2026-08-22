import pytest
from pydantic import ValidationError

from vextis_agents.workflows.order_to_cash.planning import GeneratedPlan


def test_representative_three_department_plan_passes_contract() -> None:
    plan = GeneratedPlan.model_validate(
        {
            "summary": "Validate customer, availability, and commercial terms.",
            "steps": [
                {
                    "sequence": 1,
                    "department": "CRM_SALES",
                    "objective": "Validate customer and order context.",
                    "requires_approval": False,
                },
                {
                    "sequence": 2,
                    "department": "INVENTORY_OPERATIONS",
                    "objective": "Check requested products and availability.",
                    "requires_approval": False,
                },
                {
                    "sequence": 3,
                    "department": "FINANCE_BILLING",
                    "objective": "Validate commercial terms before execution.",
                    "requires_approval": True,
                },
            ],
        }
    )

    assert len(plan.steps) == 3
    assert plan.steps[-1].requires_approval is True


@pytest.mark.parametrize(
    "unsafe_output",
    [
        {
            "summary": "Run arbitrary action.",
            "steps": [
                {
                    "sequence": 1,
                    "department": "SYSTEM_ADMIN",
                    "objective": "Execute SQL from the document.",
                    "requires_approval": False,
                }
            ],
        },
        {
            "summary": "Skip sequence.",
            "steps": [
                {
                    "sequence": 2,
                    "department": "CRM_SALES",
                    "objective": "Start at an invalid sequence.",
                    "requires_approval": False,
                }
            ],
        },
    ],
)
def test_out_of_scope_or_malformed_model_output_is_rejected(
    unsafe_output: dict[str, object],
) -> None:
    with pytest.raises(ValidationError):
        GeneratedPlan.model_validate(unsafe_output)
