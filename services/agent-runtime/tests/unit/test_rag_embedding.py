import math

import pytest

from vextis_agents.app.config import Settings
from vextis_agents.rag.embedding import DeterministicMockEmbedder, VertexTextEmbedder


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


@pytest.mark.asyncio
async def test_vertex_embedder_fallback() -> None:
    settings = Settings(
        google_cloud_project=None,
        gemini_model="gemini-3.5-flash",
    )
    embedder = VertexTextEmbedder(settings, dimension=768)
    vec = await embedder.embed_query("fallback test")
    assert len(vec) == 768
    norm = math.sqrt(sum(x * x for x in vec))
    assert pytest.approx(norm, rel=1e-3) == 1.0
