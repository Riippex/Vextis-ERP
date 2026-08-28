import json
from typing import Any

import httpx
import pytest
from pydantic import SecretStr

from vextis_agents.app.config import Settings
from vextis_agents.rag.embedding import (
    DeterministicMockEmbedder,
    EmbeddingConfigurationError,
    EmbeddingSpace,
)
from vextis_agents.rag.retriever import KnowledgeRetriever
from vextis_agents.tools.core_api.planning import CoreToolRejectedError


@pytest.mark.asyncio
async def test_retriever_search_success() -> None:
    captured_request: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured_request["url"] = str(request.url)
        captured_request["headers"] = dict(request.headers)
        captured_request["body"] = request.read().decode("utf-8")
        return httpx.Response(
            200,
            json={
                "matches": [
                    {
                        "documentId": "44cc63cc-3c91-4d80-a918-605b7f231cf8",
                        "fileName": "commercial_policy.pdf",
                        "documentUri": "gs://vextis-demo/docs/commercial_policy.pdf",
                        "chunkIndex": 0,
                        "chunkText": "Net 30 days is standard payment terms.",
                        "similarityScore": 0.94,
                        "metadata": {"section": "billing"},
                    }
                ]
            },
        )

    transport = httpx.MockTransport(handler)
    settings = Settings(
        enterprise_core_url="https://core.vextis.local",
        agent_tools_token=SecretStr("test-tools-token"),
        gemini_model="gemini-3.5-flash",
    )
    retriever = KnowledgeRetriever(
        settings=settings,
        tenant_id="demo-tenant",
        correlation_id="corr-123",
        embedder=DeterministicMockEmbedder(dimension=768),
        transport=transport,
    )

    matches = await retriever.search("payment terms", limit=5, min_score=0.5)

    assert len(matches) == 1
    assert matches[0].file_name == "commercial_policy.pdf"
    assert matches[0].chunk_text == "Net 30 days is standard payment terms."
    assert matches[0].similarity_score == 0.94

    headers = captured_request["headers"]
    assert headers["authorization"] == "Bearer test-tools-token"
    assert headers["x-tenant-id"] == "demo-tenant"
    assert headers["x-agent-id"] == "vextis_coordinator"
    assert headers["x-correlation-id"] == "corr-123"

    body = json.loads(captured_request["body"])
    assert body["query"] == "payment terms"
    # The space travels with every query so Core can scope the search to it.
    assert body["embeddingSpace"] == "mock-sha256:sha256-v1:768"
    assert body["minScore"] == 0.5


@pytest.mark.asyncio
async def test_retriever_evidence_formatting() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "matches": [
                    {
                        "documentId": "44cc63cc-3c91-4d80-a918-605b7f231cf8",
                        "fileName": "commercial_policy.pdf",
                        "documentUri": "gs://vextis-demo/docs/commercial_policy.pdf",
                        "chunkIndex": 1,
                        "chunkText": "Standard discounts cannot exceed 15% without CFO approval.",
                        "similarityScore": 0.88,
                        "metadata": {},
                    }
                ]
            },
        )

    transport = httpx.MockTransport(handler)
    settings = Settings(
        enterprise_core_url="https://core.vextis.local",
        agent_tools_token=SecretStr("test-tools-token"),
        gemini_model="gemini-3.5-flash",
        rag_mock_embeddings_enabled=True,
    )
    retriever = KnowledgeRetriever(
        settings=settings,
        tenant_id="demo-tenant",
        transport=transport,
    )

    evidence = await retriever.retrieve_evidence("discount policy")
    assert "<untrusted_knowledge_evidence>" in evidence
    assert "Standard discounts cannot exceed 15%" in evidence
    assert "</untrusted_knowledge_evidence>" in evidence


@pytest.mark.asyncio
async def test_retriever_error_handling() -> None:
    def handler_403(request: httpx.Request) -> httpx.Response:
        return httpx.Response(403, json={"error": "forbidden"})

    transport = httpx.MockTransport(handler_403)
    settings = Settings(
        enterprise_core_url="https://core.vextis.local",
        agent_tools_token=SecretStr("test-tools-token"),
        gemini_model="gemini-3.5-flash",
        rag_mock_embeddings_enabled=True,
    )
    retriever = KnowledgeRetriever(
        settings=settings,
        tenant_id="demo-tenant",
        transport=transport,
    )

    with pytest.raises(CoreToolRejectedError):
        await retriever.search("forbidden query")


@pytest.mark.asyncio
async def test_retriever_applies_a_configured_similarity_floor() -> None:
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = json.loads(request.read().decode("utf-8"))
        return httpx.Response(200, json={"matches": []})

    settings = Settings(
        enterprise_core_url="https://core.vextis.local",
        agent_tools_token=SecretStr("test-tools-token"),
        rag_mock_embeddings_enabled=True,
        rag_min_similarity=0.62,
    )
    retriever = KnowledgeRetriever(
        settings=settings,
        tenant_id="demo-tenant",
        transport=httpx.MockTransport(handler),
    )

    await retriever.search("payment terms")

    # Not 0.0: an unfiltered nearest-neighbour list reads downstream as if every
    # chunk it returns were relevant evidence.
    assert captured["body"]["minScore"] == 0.62


@pytest.mark.asyncio
async def test_retriever_declares_the_vertex_space_when_vertex_is_configured() -> None:
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = json.loads(request.read().decode("utf-8"))
        return httpx.Response(200, json={"matches": []})

    settings = Settings(
        enterprise_core_url="https://core.vextis.local",
        agent_tools_token=SecretStr("test-tools-token"),
        google_cloud_project="vextis-erp",
        rag_embedding_model="text-embedding-004",
    )
    retriever = KnowledgeRetriever(
        settings=settings,
        tenant_id="demo-tenant",
        embedder=_StubVertexEmbedder(),
        transport=httpx.MockTransport(handler),
    )

    await retriever.search("payment terms")

    assert captured["body"]["embeddingSpace"] == "vertex:text-embedding-004:768"
    assert retriever.embedding_space != DeterministicMockEmbedder().space.identifier


class _StubVertexEmbedder:
    """A Vertex-shaped embedder with no network calls."""

    @property
    def space(self) -> EmbeddingSpace:
        return EmbeddingSpace(provider="vertex", model="text-embedding-004", dimension=768)

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        return [[0.01] * 768 for _ in texts]

    async def embed_query(self, query: str) -> list[float]:
        return [0.01] * 768


@pytest.mark.asyncio
async def test_retriever_without_a_configured_provider_refuses_to_build() -> None:
    settings = Settings(
        enterprise_core_url="https://core.vextis.local",
        agent_tools_token=SecretStr("test-tools-token"),
        google_cloud_project=None,
        rag_mock_embeddings_enabled=False,
    )

    with pytest.raises(EmbeddingConfigurationError):
        KnowledgeRetriever(settings=settings, tenant_id="demo-tenant")
