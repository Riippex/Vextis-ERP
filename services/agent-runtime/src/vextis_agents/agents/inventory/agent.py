from collections.abc import Callable
from typing import Any

from google.adk.agents import LlmAgent
from google.adk.models import BaseLlm
from google.adk.tools.base_tool import BaseTool
from google.adk.tools.base_toolset import BaseToolset

from vextis_agents.tools.core_api.business_reads import BusinessReadTool


def build_inventory_agent(
    model: str | BaseLlm, core_reads: BusinessReadTool | None = None
) -> LlmAgent:
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

        async def search_inventory(query: str = "", limit: int = 20) -> dict[str, object]:
            """List inventory or search tenant stock by a partial SKU, bounded to 50 results."""
            results = await core_reads.search_inventory(query, limit)
            return {"count": len(results), "items": [
                item.model_dump(by_alias=True, mode="json") for item in results
            ]}

        tools.append(search_inventory)

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
            "conversation or in authorized tool results. Use get_stock for an exact SKU. Use "
            "search_inventory with an empty query to answer what inventory exists, or with a "
            "partial SKU when the user provides a product-like term. Do not repeatedly ask for an "
            "exact SKU before trying the bounded search, and clearly state when no match exists. "
            "Never invent "
            "availability, claim that stock was reserved, or make customer or credit decisions. "
            "Enterprise Core is the sole business authority. Return to the coordinator when "
            "another department owns the request. Do not expose hidden reasoning."
        ),
        tools=tools,
    )
