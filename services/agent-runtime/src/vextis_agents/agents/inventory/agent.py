from collections.abc import Callable
from typing import Any

from google.adk.agents import LlmAgent
from google.adk.tools.base_tool import BaseTool
from google.adk.tools.base_toolset import BaseToolset

from vextis_agents.tools.core_api.business_reads import BusinessReadTool


def build_inventory_agent(model: str, core_reads: BusinessReadTool | None = None) -> LlmAgent:
    """Build the Inventory and Operations specialist used by the fleet coordinator."""
    tools: list[Callable[..., Any] | BaseTool | BaseToolset] = []
    if core_reads is not None:

        async def get_stock(sku: str) -> dict[str, object]:
            """Get current available quantity for one exact SKU from Enterprise Core."""
            stock = await core_reads.get_stock(sku)
            if stock is None:
                return {"found": False, "sku": sku}
            return {"found": True, **stock.model_dump(by_alias=True, mode="json")}

        tools.append(get_stock)

    return LlmAgent(
        name="vextis_inventory_agent",
        model=model,
        description=(
            "Inventory and Operations specialist for SKU, availability, reservation, fulfillment, "
            "and operational-context questions. Delegate here for inventory topics."
        ),
        instruction=(
            "You are Vextis's Inventory and Operations specialist. Stay within products, SKUs, "
            "availability, reservations, and fulfillment context. Use only facts present in the "
            "conversation or in authorized tool results. Use get_stock for current SKU "
            "availability "
            "and clearly state when no matching SKU exists. Never invent "
            "availability, claim that stock was reserved, or make customer or credit decisions. "
            "Enterprise Core is the sole business authority. Return to the coordinator when "
            "another department owns the request. Do not expose hidden reasoning."
        ),
        tools=tools,
    )
