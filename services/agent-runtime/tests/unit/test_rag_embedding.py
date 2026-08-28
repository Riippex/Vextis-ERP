import math
from typing import Any

import pytest

from vextis_agents.app.config import Settings
from vextis_agents.rag.embedding import (
    DeterministicMockEmbedder,
    EmbeddingConfigurationError,
    EmbeddingUnavailableError,
    VertexTextEmbedder,
    build_text_embedder,
)


@pytest.mark.asyncio
async def test_deterministic_mock_embedder() -> None:
    embedder = DeterministicMockEmbedder(dimension=768)
    vec1 = await embedder.embed_query("payment terms net 30")
    vec2 = await embedder.embed_query("payment terms net 30")
    vec3 = await embedder.embed_query("completely different topic")

    assert len(vec1) == 768
    assert len(vec2) == 768
    assert len(vec3) == 768

    # Deterministic for exact same text
    assert vec1 == vec2

    # Different for different text
    assert vec1 != vec3

    # Unit normalized: L2 norm is ~1.0
    norm = math.sqrt(sum(x * x for x in vec1))
    assert pytest.approx(norm, rel=1e-3) == 1.0


@pytest.mark.asyncio
async def test_embed_batch_texts() -> None:
    embedder = DeterministicMockEmbedder(dimension=768)
    results = await embedder.embed_texts(["first chunk", "second chunk"])
    assert len(results) == 2
    assert len(results[0]) == 768
    assert len(results[1]) == 768


def test_mock_and_vertex_occupy_distinct_embedding_spaces() -> None:
    mock = DeterministicMockEmbedder(dimension=768)
    vertex = VertexTextEmbedder(
        Settings(google_cloud_project="vextis-erp"),
        model="text-embedding-004",
        dimension=768,
    )

    assert mock.space.identifier == "mock-sha256:sha256-v1:768"
    assert vertex.space.identifier == "vertex:text-embedding-004:768"
    assert mock.space != vertex.space


def test_embedding_space_identifier_separates_models_and_dimensions() -> None:
    settings = Settings(google_cloud_project="vextis-erp")
    small = VertexTextEmbedder(settings, model="text-embedding-004", dimension=256)
    large = VertexTextEmbedder(settings, model="text-embedding-004", dimension=768)
    other_model = VertexTextEmbedder(settings, model="text-embedding-005", dimension=768)

    identifiers = {small.space.identifier, large.space.identifier, other_model.space.identifier}
    assert len(identifiers) == 3


def test_vertex_embedder_requires_a_project() -> None:
    with pytest.raises(EmbeddingConfigurationError):
        VertexTextEmbedder(Settings(google_cloud_project=None))


@pytest.mark.asyncio
async def test_vertex_embedder_reports_failure_instead_of_falling_back(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # The previous behaviour swallowed the failure and returned SHA-256 mock
    # vectors, which then queried a space nothing was indexed in.
    embedder = VertexTextEmbedder(Settings(google_cloud_project="vextis-erp"))

    def explode(_texts: list[str]) -> Any:
        raise RuntimeError("credentials unavailable")

    monkeypatch.setattr(embedder, "_embed_sync", explode)

    with pytest.raises(EmbeddingUnavailableError):
        await embedder.embed_query("payment terms")


@pytest.mark.asyncio
async def test_vertex_embedder_rejects_a_wrong_dimension_response(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    embedder = VertexTextEmbedder(Settings(google_cloud_project="vextis-erp"), dimension=768)

    class _Embedding:
        values = [0.1] * 256

    class _Response:
        embeddings = [_Embedding()]

    monkeypatch.setattr(embedder, "_embed_sync", lambda _texts: _Response())

    with pytest.raises(EmbeddingUnavailableError):
        await embedder.embed_query("payment terms")


def test_builder_refuses_to_default_to_mock_embeddings() -> None:
    settings = Settings(google_cloud_project=None, rag_mock_embeddings_enabled=False)

    with pytest.raises(EmbeddingConfigurationError):
        build_text_embedder(settings)


def test_builder_returns_the_mock_only_when_explicitly_enabled() -> None:
    settings = Settings(google_cloud_project=None, rag_mock_embeddings_enabled=True)

    embedder = build_text_embedder(settings)

    assert isinstance(embedder, DeterministicMockEmbedder)
    assert embedder.space.identifier == "mock-sha256:sha256-v1:768"


def test_builder_prefers_vertex_when_a_project_is_configured() -> None:
    settings = Settings(google_cloud_project="vextis-erp")

    embedder = build_text_embedder(settings)

    assert isinstance(embedder, VertexTextEmbedder)
    assert embedder.space.provider == "vertex"


def test_explicit_mock_flag_wins_over_a_configured_project() -> None:
    # A developer pointing at a real project locally still gets the mock only
    # by asking for it, and gets the mock space with it, so nothing they index
    # can be confused with Vertex-embedded content.
    settings = Settings(google_cloud_project="vextis-erp", rag_mock_embeddings_enabled=True)

    embedder = build_text_embedder(settings)

    assert isinstance(embedder, DeterministicMockEmbedder)
