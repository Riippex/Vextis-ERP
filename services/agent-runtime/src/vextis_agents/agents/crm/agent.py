import logging
from collections.abc import Callable
from typing import Any

from google.adk.agents import LlmAgent
from google.adk.models import BaseLlm
from google.adk.tools.base_tool import BaseTool
from google.adk.tools.base_toolset import BaseToolset

from vextis_agents.crm.asset_generator import ProposalAssetGenerator, ProposalAssetUploadError
from vextis_agents.tools.core_api.business_reads import BusinessReadTool
from vextis_agents.tools.core_api.planning import CoreToolRejectedError, CoreToolUnavailableError

logger = logging.getLogger(__name__)


def build_crm_agent(
    model: str | BaseLlm,
    core_reads: BusinessReadTool | None = None,
    asset_generator: ProposalAssetGenerator | None = None,
) -> LlmAgent:
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

        async def list_customers(limit: int = 20) -> dict[str, object]:
            """List up to 50 customers from authoritative tenant-scoped Enterprise Core data."""
            customers = await core_reads.list_customers(limit)
            return {"count": len(customers), "customers": [
                customer.model_dump(by_alias=True, mode="json") for customer in customers
            ]}

        async def search_customer_orders(legal_name: str, limit: int = 20) -> dict[str, object]:
            """Count and list recent purchase orders for one exact customer legal name."""
            result = await core_reads.search_customer_orders(legal_name, limit)
            return result.model_dump(by_alias=True, mode="json")

        tools.extend([list_customers, search_customer_orders])

    if asset_generator is not None:

        async def generate_proposal_asset(
            quote_id: str, visual_description: str
        ) -> dict[str, object]:
            """
            Generates one AI visual concept image for a quote or order and
            registers it with Enterprise Core, which is the only place a
            proposal asset is stored. quote_id identifies the quote or order
            this asset illustrates — usually the execution id the user is
            discussing. visual_description is what the image should show;
            never include customer secrets, credentials, or account numbers
            in it, since it becomes part of the generation prompt.
            """
            try:
                asset = await asset_generator.generate_and_register(
                    quote_id=quote_id,
                    prompt=visual_description,
                )
            except (
                CoreToolRejectedError,
                CoreToolUnavailableError,
                ProposalAssetUploadError,
                RuntimeError,
                ValueError,
            ) as exception:
                logger.warning("Proposal asset generation failed: %s", exception)
                return {"registered": False, "error": str(exception)}
            return {"registered": True, **asset.model_dump(by_alias=True, mode="json")}

        tools.append(generate_proposal_asset)

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
            "authorized tool results. Use lookup_customer for one exact customer, list_customers "
            "when the user asks which customers exist, and search_customer_orders to count or list "
            "a customer's orders. Do not ask for information already present in recent "
            "conversation context. Clearly "
            "state when no matching record exists. When the user asks for a proposal visual, "
            "mockup, or concept image for a quote or order, use generate_proposal_asset with that "
            "quote or order's id; always disclose that the resulting image is AI-generated and "
            "never claim it depicts a real product photo. Never invent customer state, claim "
            "that a quote or order was changed, or make inventory or credit decisions. Enterprise "
            "Core is the sole business authority. Return to the coordinator when another "
            "department owns the request. Do not expose hidden reasoning."
        ),
        tools=tools,
    )
