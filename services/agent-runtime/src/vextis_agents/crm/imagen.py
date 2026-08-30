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

# Never the real Imagen model id: this is the provenance marker persisted on
# every mock-generated asset, so a mock concept can never be mistaken for a
# genuine Vertex AI Imagen output downstream.
MOCK_MODEL_ID = "mock-imagen"


def redact_prompt(raw_prompt: str) -> str:
    """
    Minimizes and sanitizes the prompt before logging, model invocation, or storage.
    Removes secrets, tokens, credentials, private keys, phone numbers, addresses,
    identification numbers, customer names, and emails.
    """
    cleaned = raw_prompt.strip()

    # Redact private keys / certificates
    cleaned = re.sub(
        r"-----BEGIN [A-Z ]+-----[^-]+-----END [A-Z ]+-----",
        "[REDACTED_KEY]",
        cleaned,
        flags=re.DOTALL,
    )
    # Redact cloud API keys (Google AIza, AWS AKIA, GitHub tokens, etc.)
    cleaned = re.sub(
        r"(AIza[0-9A-Za-z-_]{35}|AKIA[0-9A-Z]{16}|ghp_[0-9a-zA-Z]{36})",
        "[REDACTED_KEY]",
        cleaned,
    )
    # Redact potential bearer tokens / keys
    cleaned = re.sub(r"(?i)(bearer\s+[a-zA-Z0-9_\-\.]{10,})", "[REDACTED_TOKEN]", cleaned)
    cleaned = re.sub(
        r"(?i)(key|secret|password|token|apikey|credential|private_key|auth)\s*[:=]\s*[^\s,;]+",
        r"\1=[REDACTED]",
        cleaned,
    )

    # Redact email addresses
    cleaned = re.sub(r"[\w\.-]+@[\w\.-]+\.\w+", "[REDACTED_EMAIL]", cleaned)
    # Redact credit card / account numbers
    cleaned = re.sub(r"\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b", "[REDACTED_CARD]", cleaned)
    # Redact Colombian Cedula / NIT / SSN / Tax IDs
    cleaned = re.sub(
        r"(?i)\b(nit|cedula|cc|ssn|rut)\s*[:=]?\s*\d{6,11}(-\d)?\b",
        "[REDACTED_ID]",
        cleaned,
    )
    # Redact national / international phone numbers (e.g. +57 300 123 4567, (555) 123-4567)
    cleaned = re.sub(
        r"(\+?\d{1,3}[-.\s]?)?(\(?\d{2,4}\)?[-.\s]?)?\d{3,4}[-.\s]?\d{3,4}\b",
        "[REDACTED_PHONE]",
        cleaned,
    )
    # Redact street addresses
    cleaned = re.sub(
        r"(?i)\b(calle|carrera|avenida|diagonal|transversal|street|st\.|avenue|ave\.|road|rd\.|"
        r"blvd|suite|apt)\s+\d+[^,\n]*",
        "[REDACTED_ADDRESS]",
        cleaned,
    )
    # Redact customer / contact person names prefixed by common titles
    cleaned = re.sub(
        r"(?i)\b(cliente|customer|sr\.|sra\.|mr\.|mrs\.|ms\.|contacto|contact)\s*[:=]?\s*"
        r"[A-Z][a-z]+(\s+[A-Z][a-z]+)+",
        r"\1: [REDACTED_NAME]",
        cleaned,
    )

    # Sanitize control characters and limit length
    cleaned = re.sub(r"[\x00-\x1f\x7f-\x9f]", " ", cleaned)
    return re.sub(r"\s+", " ", cleaned).strip()[:500]


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

        if self._mock_enabled:
            # Intentional opt-in only (VEXTIS_IMAGEN_MOCK_ENABLED). Provenance
            # is recorded as MOCK_MODEL_ID, never the real Imagen model id, so
            # a mock concept can never be mistaken for a genuine Vertex AI
            # output downstream.
            logger.info("Using mock Imagen generator for prompt: %s", prompt_summary)
            return ImagenGenerationResult(
                image_bytes=MOCK_PNG_BYTES,
                mime_type="image/png",
                model_id=MOCK_MODEL_ID,
                prompt_summary=prompt_summary,
                ai_label=ai_label,
            )

        if not self._project:
            raise RuntimeError(
                "GOOGLE_CLOUD_PROJECT must be configured to call Vertex AI Imagen; "
                "set VEXTIS_IMAGEN_MOCK_ENABLED=true to opt into mock generation instead"
            )

        # No fallback to the mock on failure: a deployed environment must
        # fail explicitly rather than silently return a mock image labelled
        # with a real Imagen model id.
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
