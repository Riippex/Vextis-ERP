from google.adk.agents import LlmAgent


def build_crm_agent(model: str) -> LlmAgent:
    """Build the CRM and Sales specialist used by the fleet coordinator."""
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
            "authorized tool results. You currently have no live ERP lookup tools, so say clearly "
            "when current customer records cannot be verified. Never invent customer state, claim "
            "that a quote or order was changed, or make inventory or credit decisions. Enterprise "
            "Core is the sole business authority. Return to the coordinator when another "
            "department owns the request. Do not expose hidden reasoning."
        ),
    )
