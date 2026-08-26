from collections.abc import Callable
from typing import Any

from google.adk.agents import LlmAgent
from google.adk.tools.base_tool import BaseTool
from google.adk.tools.base_toolset import BaseToolset

from vextis_agents.tools.core_api.business_reads import BusinessReadTool


def build_billing_agent(model: str, core_reads: BusinessReadTool | None = None) -> LlmAgent:
    """Build the Finance and Billing specialist used by the fleet coordinator."""
    tools: list[Callable[..., Any] | BaseTool | BaseToolset] = []
    if core_reads is not None:

        async def get_credit(customer_id: str) -> dict[str, object]:
            """Get current credit standing and payment-term limit for one customer UUID."""
            from uuid import UUID

            try:
                parsed_customer_id = UUID(customer_id)
            except ValueError:
                return {"found": False, "error": "customer_id must be a UUID"}
            credit = await core_reads.get_credit(parsed_customer_id)
            if credit is None:
                return {"found": False, "customerId": customer_id}
            return {"found": True, **credit.model_dump(by_alias=True, mode="json")}

        tools.append(get_credit)

    return LlmAgent(
        name="vextis_billing_agent",
        model=model,
        description=(
            "Finance and Billing specialist for credit, payment terms, invoices, and collections. "
            "Delegate here when the request is primarily financial."
        ),
        instruction=(
            "You are Vextis's Finance and Billing specialist. Stay within credit, commercial "
            "payment terms, invoices, and collections context. Use only facts present in the "
            "conversation or in authorized tool results. Use get_credit for current credit records "
            "and clearly state when no matching profile exists. Never invent "
            "balances or credit "
            "standing, claim that an invoice was issued, or make customer or inventory decisions. "
            "Enterprise Core is the sole business authority. Return to the coordinator when "
            "another department owns the request. Do not expose hidden reasoning."
        ),
        tools=tools,
    )
