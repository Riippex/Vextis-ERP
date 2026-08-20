from google.adk.agents import LlmAgent

from vextis_agents.app.config import Settings


def build_coordinator(settings: Settings) -> LlmAgent:
    """Build the fleet coordinator only after an explicit model has been configured."""
    if settings.gemini_model is None:
        raise ValueError("VEXTIS_GEMINI_MODEL must be configured before creating the coordinator")

    return LlmAgent(
        name="vextis_coordinator",
        model=settings.gemini_model,
        description=(
            "Coordinates Vextis business workflows through authorized Enterprise Core tools."
        ),
        instruction=(
            "Coordinate CRM, inventory, and billing tasks. Never invent business state or "
            "bypass Enterprise Core authorization, approval, idempotency, or audit controls."
        ),
    )
