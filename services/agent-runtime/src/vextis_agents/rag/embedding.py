import hashlib
import math
from typing import Any, Protocol, cast

from vextis_agents.app.config import Settings


class TextEmbedder(Protocol):
    async def embed_texts(self, texts: list[str]) -> list[list[float]]: ...

    async def embed_query(self, query: str) -> list[float]: ...


class DeterministicMockEmbedder:
    """
    Generates deterministic, unit-normalized 768-dimensional float embeddings
    from text hashing. Ideal for offline test suites, CI, and local fallbacks.
    """

    def __init__(self, dimension: int = 768) -> None:
        self.dimension = dimension

    def _embed_single(self, text: str) -> list[float]:
        clean = text.strip().lower()
        if not clean:
            return [0.0] * self.dimension

        # Generate pseudo-random vector deterministically using SHA-256 rounds
        vector: list[float] = []
        seed = clean
        for i in range((self.dimension + 31) // 32):
            digest = hashlib.sha256(f"{seed}:{i}".encode()).digest()
            for b in digest:
                # Map byte 0..255 to -1.0 .. 1.0
                vector.append((float(b) / 127.5) - 1.0)
                if len(vector) == self.dimension:
                    break

        # Normalize to unit length
        norm = math.sqrt(sum(x * x for x in vector))
        if norm > 0:
            vector = [x / norm for x in vector]
        return vector

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        return [self._embed_single(t) for t in texts]

    async def embed_query(self, query: str) -> list[float]:
        return self._embed_single(query)


class VertexTextEmbedder:
    """
    Produces text embeddings using Google GenAI / Vertex AI text-embedding-004.
    """

    def __init__(
        self,
        settings: Settings,
        model: str = "text-embedding-004",
        dimension: int = 768,
    ) -> None:
        self._settings = settings
        self._model = model
        self._dimension = dimension
        self._fallback = DeterministicMockEmbedder(dimension=dimension)

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        if self._settings.google_cloud_project:
            try:
                from google import genai
                from google.genai import types

                client = genai.Client(
                    vertexai=True,
                    project=self._settings.google_cloud_project,
                    location=self._settings.gemini_location or "us-central1",
                )
                response = client.models.embed_content(
                    model=self._model,
                    contents=cast(Any, texts),
                    config=types.EmbedContentConfig(
                        output_dimensionality=self._dimension,
                    ),
                )
                if hasattr(response, "embeddings") and response.embeddings:
                    return [
                        list(e.values)
                        for e in response.embeddings
                        if e.values is not None
                    ]
            except Exception:
                # Fallback safely when credentials/network unavailable in local test mode
                pass
        return await self._fallback.embed_texts(texts)

    async def embed_query(self, query: str) -> list[float]:
        results = await self.embed_texts([query])
        return results[0] if results else [0.0] * self._dimension


def build_text_embedder(settings: Settings) -> TextEmbedder:
    if settings.google_cloud_project:
        return VertexTextEmbedder(settings)
    return DeterministicMockEmbedder()
