from vextis_agents.app.config import Settings
from vextis_agents.coordinator.agent import build_coordinator
from vextis_agents.workflows.order_to_cash.planning import GeneratedPlan


def test_coordinator_agent_enforces_three_department_specialist_delegation() -> None:
    settings = Settings(gemini_model="gemini-3.5-flash")
    coordinator = build_coordinator(settings)

    instruction_text = (
        coordinator.instruction if isinstance(coordinator.instruction, str) else ""
    )

    # The coordinator prompt must reference all three specialists
    assert "CRM" in instruction_text or "crm" in instruction_text.lower()
    assert "inventory" in instruction_text.lower()
    assert "billing" in instruction_text.lower()

    # The coordinator prompt must require explicit boundaries and Core truth
    assert "Enterprise Core" in instruction_text

    # Must contain exactly the 3 specialist sub-agents
    assert len(coordinator.sub_agents) == 3
    sub_agent_names = [sa.name for sa in coordinator.sub_agents]
    assert "vextis_crm_agent" in sub_agent_names
    assert "vextis_inventory_agent" in sub_agent_names
    assert "vextis_billing_agent" in sub_agent_names


def test_valid_three_department_delegation_plan_is_ordered_and_bounded() -> None:
    plan_dict = {
        "summary": "Process commercial purchase order across CRM, Inventory, and Finance.",
        "steps": [
            {
                "sequence": 1,
                "department": "CRM_SALES",
                "objective": "Verify customer account active status.",
                "requires_approval": False,
            },
            {
                "sequence": 2,
                "department": "INVENTORY_OPERATIONS",
                "objective": "Check warehouse inventory stock levels for requested chair.",
                "requires_approval": False,
            },
            {
                "sequence": 3,
                "department": "FINANCE_BILLING",
                "objective": "Verify payment terms and credit limit before order reservation.",
                "requires_approval": True,
            },
        ],
        "order_lines": [{"sku": "VXT-CHAIR-01", "quantity": 10}],
        "requested_payment_terms_days": 30,
    }

    validated = GeneratedPlan.model_validate(plan_dict)
    assert len(validated.steps) == 3
    departments = [s.department for s in validated.steps]
    assert departments == ["CRM_SALES", "INVENTORY_OPERATIONS", "FINANCE_BILLING"]
    assert validated.steps[2].requires_approval is True
