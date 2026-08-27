from collections.abc import Callable
from typing import Any

from google.adk.agents import LlmAgent
from google.adk.models import BaseLlm
from google.adk.tools.base_tool import BaseTool
from google.adk.tools.base_toolset import BaseToolset

from vextis_agents.tools.core_api.business_reads import BusinessReadTool


def build_crm_agent(model: str | BaseLlm, core_reads: BusinessReadTool | None = None) -> LlmAgent:
    """Build the CRM and Sales specialist used by the fleet coordinator."""
    tools: list[Callable[..., Any] | BaseTool | BaseToolset] = []
    if core_reads is not None:

        async def lookup_customer(legal_name: str) -> dict[str, object]:
            """Look up one customer by exact legal name in authoritative Enterprise Core data."""
            customer = await core_reads.lookup_customer(legal_name)
            if customer is None:
                return {"found": False, "legalName": legal_name}
            return {"found": True, **customer.model_dump(by_alias=True, mode="json")}

        tools.append(lookup_customer)

    return LlmAgent(
        name="vextis_crm_agent",
        model=model,
        description=(
            "CRM and Sales specialist for customer, opportunity, quote, and commercial-context "
            "questions. Delegate here when the request is primarily about the sales lifecycle."
        ),
        instruction=(
            "You are Vextis's CRM and Sales specialist. Stay within customers, opportunities, "
            "quotes, and commercial context. Use only facts present in the conversation or in "
            "authorized tool results. Use lookup_customer for current customer records and clearly "
            "state when no matching record exists. Never invent customer state, claim "
            "that a quote or order was changed, or make inventory or credit decisions. Enterprise "
            "Core is the sole business authority. Return to the coordinator when another "
            "department owns the request. Do not expose hidden reasoning."
        ),
        tools=tools,
    )
