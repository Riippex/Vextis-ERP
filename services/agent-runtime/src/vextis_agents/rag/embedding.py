import asyncio
import hashlib
import logging
import math
from dataclasses import dataclass
from typing import Any, Protocol, cast

from vextis_agents.app.config import Settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class EmbeddingSpace:
    """
    The provider, model and dimension a vector was produced with.

    Cosine similarity between vectors from different spaces is arithmetic, not
    meaning: it returns a confident-looking number for embeddings that share no
    geometry at all. Every stored chunk and every query therefore carries its
    space, and Enterprise Core only compares vectors within one.
    """

    provider: str
    model: str
    dimension: int

    @property
    def identifier(self) -> str:
        return f"{self.provider}:{self.model}:{self.dimension}"


# Must match DemoSeedingService.MOCK_EMBEDDING_SPACE in Enterprise Core.
MOCK_EMBEDDING_SPACE = EmbeddingSpace(provider="mock-sha256", model="sha256-v1", dimension=768)


class EmbeddingConfigurationError(RuntimeError):
    """No embedding provider is configured for this deployment."""


class EmbeddingUnavailableError(RuntimeError):
    """The configured embedding provider could not produce usable vectors."""


class TextEmbedder(Protocol):
    @property
    def space(self) -> EmbeddingSpace: ...

    async def embed_texts(self, texts: list[str]) -> list[list[float]]: ...

    async def embed_query(self, query: str) -> list[float]: ...


class DeterministicMockEmbedder:
    """
    Deterministic, unit-normalized embeddings derived from text hashing. These
    vectors carry no semantics; they exist so offline tests and local runs can
    exercise the retrieval path end to end. They occupy their own embedding
    space and can never be matched against a real provider's vectors.
    """

    def __init__(self, dimension: int = 768) -> None:
        self.dimension = dimension

    @property
    def space(self) -> EmbeddingSpace:
        return EmbeddingSpace(
            provider=MOCK_EMBEDDING_SPACE.provider,
            model=MOCK_EMBEDDING_SPACE.model,
            dimension=self.dimension,
        )

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
    Produces text embeddings using Vertex AI. A failure here is reported, never
    papered over with mock vectors: silently degrading would write or query the
    wrong embedding space and return retrieval results that look grounded and
    are not.
    """

    def __init__(
        self,
        settings: Settings,
        model: str | None = None,
        dimension: int | None = None,
    ) -> None:
        if not settings.google_cloud_project:
            raise EmbeddingConfigurationError(
                "GOOGLE_CLOUD_PROJECT is required to produce Vertex AI embeddings"
            )
        self._project = settings.google_cloud_project
        self._location = settings.rag_embedding_location
        self._model = model or settings.rag_embedding_model
        self._dimension = dimension or settings.rag_embedding_dimension

    @property
    def space(self) -> EmbeddingSpace:
        return EmbeddingSpace(provider="vertex", model=self._model, dimension=self._dimension)

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []

        try:
            response = await asyncio.to_thread(self._embed_sync, texts)
        except Exception as exception:
            raise EmbeddingUnavailableError(
                f"Vertex AI embedding request failed for model {self._model}"
            ) from exception

        embeddings = getattr(response, "embeddings", None)
        if not embeddings:
            raise EmbeddingUnavailableError(
                f"Vertex AI returned no embeddings for model {self._model}"
            )

        vectors = [list(item.values) for item in embeddings if item.values is not None]
        if len(vectors) != len(texts):
            raise EmbeddingUnavailableError(
                f"Vertex AI returned {len(vectors)} embeddings for {len(texts)} inputs"
            )
        for vector in vectors:
            if len(vector) != self._dimension:
                raise EmbeddingUnavailableError(
                    f"Vertex AI returned {len(vector)} dimensions, expected {self._dimension}"
                )
        return vectors

    def _embed_sync(self, texts: list[str]) -> Any:
        from google import genai
        from google.genai import types

        client = genai.Client(
            vertexai=True,
            project=self._project,
            location=self._location,
        )
        return client.models.embed_content(
            model=self._model,
            contents=cast(Any, texts),
            config=types.EmbedContentConfig(output_dimensionality=self._dimension),
        )

    async def embed_query(self, query: str) -> list[float]:
        results = await self.embed_texts([query])
        if not results:
            raise EmbeddingUnavailableError("Vertex AI returned no embedding for the query")
        return results[0]


def build_text_embedder(settings: Settings) -> TextEmbedder:
    """
    Resolves the single embedder this process uses for both documents and
    queries. The mock is opt-in only: without the flag, a deployment with no
    Vertex configuration fails here rather than quietly indexing or querying
    with hash vectors.
    """
    if settings.rag_mock_embeddings_enabled:
        logger.warning(
            "Using deterministic mock embeddings (%s). Retrieval results carry no semantics; "
            "this is only valid for local and test runs.",
            MOCK_EMBEDDING_SPACE.identifier,
        )
        return DeterministicMockEmbedder(dimension=settings.rag_embedding_dimension)

    if not settings.google_cloud_project:
        raise EmbeddingConfigurationError(
            "Text embeddings require GOOGLE_CLOUD_PROJECT. Set "
            "VEXTIS_RAG_MOCK_EMBEDDINGS_ENABLED=true only for local or test runs."
        )

    return VertexTextEmbedder(settings)
