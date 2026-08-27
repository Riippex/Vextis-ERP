from google.adk.models import BaseLlm, Gemini

from vextis_agents.app.config import Settings


def build_gemini_model(
    settings: Settings,
    model_id: str,
    location: str,
) -> str | BaseLlm:
    """Bind a Gemini model to its Vertex project and supported serving location."""
    if settings.google_cloud_project is None:
        return model_id
    return Gemini(
        model=model_id,
        client_kwargs={
            "vertexai": True,
            "project": settings.google_cloud_project,
            "location": location,
        },
    )
