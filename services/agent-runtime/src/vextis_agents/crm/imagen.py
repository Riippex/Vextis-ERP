import logging
import re
from dataclasses import dataclass
from typing import Any

from vextis_agents.app.config import Settings

logger = logging.getLogger(__name__)

# Minimal valid 1x1 transparent PNG for mock generation
MOCK_PNG_BYTES = (
    b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f"
    b"\x15c4\x00\x00\x00\nIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB`\x82"
)


def redact_prompt(raw_prompt: str) -> str:
    """Removes potential secrets, credentials, tokens, or PII before logging/storing."""
    cleaned = raw_prompt.strip()
    # Redact potential bearer tokens / keys
    cleaned = re.sub(r"(?i)(bearer\s+[a-zA-Z0-9_\-\.]{10,})", "[REDACTED_TOKEN]", cleaned)
    cleaned = re.sub(r"(?i)(key|secret|password|token)\s*[:=]\s*[^\s]+", r"\1=[REDACTED]", cleaned)
    # Redact email addresses
    cleaned = re.sub(r"[\w\.-]+@[\w\.-]+\.\w+", "[REDACTED_EMAIL]", cleaned)
    # Redact credit card / account numbers
    cleaned = re.sub(r"\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b", "[REDACTED_CARD]", cleaned)
    return cleaned[:500]


@dataclass(frozen=True)
class ImagenGenerationResult:
    image_bytes: bytes
    mime_type: str
    model_id: str
    prompt_summary: str
    ai_label: str


class ImagenClient:
    """Generates visual quote/proposal assets using Imagen 3 on Vertex AI."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._model_id = settings.imagen_model
        self._location = settings.imagen_location
        self._project = settings.google_cloud_project
        self._mock_enabled = settings.imagen_mock_enabled

    def generate_image(self, prompt: str) -> ImagenGenerationResult:
        """Generates a visual asset from a user or agent prompt."""
        if not self._settings.imagen_enabled:
            raise RuntimeError("Imagen proposal asset generation is disabled by configuration")

        prompt_summary = redact_prompt(prompt)
        ai_label = "AI-Generated Proposal Concept"

        if self._mock_enabled or not self._project:
            logger.info("Using mock Imagen generator for prompt: %s", prompt_summary)
            return ImagenGenerationResult(
                image_bytes=MOCK_PNG_BYTES,
                mime_type="image/png",
                model_id=self._model_id,
                prompt_summary=prompt_summary,
                ai_label=ai_label,
            )

        try:
            import vertexai
            from vertexai.preview.vision_models import ImageGenerationModel

            vertexai.init(project=self._project, location=self._location)
            model = ImageGenerationModel.from_pretrained(self._model_id)
            response: Any = model.generate_images(
                prompt=prompt_summary,
                number_of_images=1,
                aspect_ratio="1:1",
                # ImageGenerationModel.generate_images only accepts
                # "block_most" / "block_some" / "block_few" / "block_fewest";
                # "block_most" is the strictest available level, matching the
                # strict intent for customer-facing proposal assets.
                safety_filter_level="block_most",
                person_generation="allow_adult",
            )
            image = response[0]
            return ImagenGenerationResult(
                image_bytes=image._image_bytes,
                mime_type="image/png",
                model_id=self._model_id,
                prompt_summary=prompt_summary,
                ai_label=ai_label,
            )
        except Exception as exception:
            logger.warning("Vertex AI Imagen generation failed: %s", exception)
            return ImagenGenerationResult(
                image_bytes=MOCK_PNG_BYTES,
                mime_type="image/png",
                model_id=self._model_id,
                prompt_summary=prompt_summary,
                ai_label=ai_label,
            )
