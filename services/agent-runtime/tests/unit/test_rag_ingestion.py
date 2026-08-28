import json
from typing import Any

import httpx
import pytest
from pydantic import SecretStr

from vextis_agents.app.config import Settings
from vextis_agents.rag.embedding import EmbeddingSpace
from vextis_agents.rag.ingest import main
from vextis_agents.rag.ingestion import DocumentIngestor, content_hash
from vextis_agents.rag.retriever import KnowledgeRetriever
from vextis_agents.tools.core_api.planning import CoreToolRejectedError

POLICY_TEXT = (
    "Vextis Commercial Policy. Standard payment terms for approved corporate customers "
    "are Net 30 days.\n\nThe maximum standard discount without CFO approval is 15 percent."
)

INGEST_RESPONSE = {
    "documentId": "6f1c6f2c-4a6f-4d0b-9d5e-2c3a4b5c6d7e",
    "documentUri": "urn:vextis:policy:commercial",
    "version": 1,
    "status": "INDEXED",
    "chunkCount": 1,
}


def _settings(**overrides: Any) -> Settings:
    defaults: dict[str, Any] = {
        "enterprise_core_url": "https://core.vextis.local",
        "agent_tools_token": SecretStr("test-tools-token"),
        "rag_mock_embeddings_enabled": True,
    }
    defaults.update(overrides)
    return Settings(**defaults)


class _StubVertexEmbedder:
    """A Vertex-shaped embedder with no network calls."""

    def __init__(self, model: str = "text-embedding-004") -> None:
        self._model = model

    @property
    def space(self) -> EmbeddingSpace:
        return EmbeddingSpace(provider="vertex", model=self._model, dimension=768)

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        return [[0.01] * 768 for _ in texts]

    async def embed_query(self, query: str) -> list[float]:
        return [0.01] * 768


@pytest.mark.asyncio
async def test_ingestion_chunks_embeds_and_posts_to_enterprise_core() -> None:
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["headers"] = dict(request.headers)
        captured["body"] = json.loads(request.read().decode("utf-8"))
        return httpx.Response(200, json=INGEST_RESPONSE)

    ingestor = DocumentIngestor(
        _settings(),
        tenant_id="demo-tenant",
        correlation_id="corr-ingest-1",
        transport=httpx.MockTransport(handler),
    )

    document = await ingestor.ingest(
        document_uri="urn:vextis:policy:commercial",
        file_name="commercial_policy.md",
        content_type="text/markdown",
        text=POLICY_TEXT,
        metadata={"section": "commercial"},
    )

    assert document.version == 1
    assert document.status == "INDEXED"

    assert captured["url"].endswith("/internal/agent-tools/v1/rag/documents")
    headers = captured["headers"]
    assert headers["authorization"] == "Bearer test-tools-token"
    assert headers["x-tenant-id"] == "demo-tenant"
    # Enterprise Core authorizes writes against this agent, not the readers.
    assert headers["x-agent-id"] == "vextis_document_ingestor"

    body = captured["body"]
    assert body["contentHash"] == content_hash(POLICY_TEXT)
    assert body["embeddingSpace"] == "mock-sha256:sha256-v1:768"
    assert len(body["chunks"]) >= 1
    for index, chunk in enumerate(body["chunks"]):
        assert chunk["chunkIndex"] == index
        assert len(chunk["embedding"]) == 768
        assert chunk["metadata"]["section"] == "commercial"


@pytest.mark.asyncio
async def test_documents_are_indexed_in_the_space_queries_will_search() -> None:
    # The mixing bug this guards: a document embedded by one provider and a
    # query embedded by another are compared as if they shared a geometry.
    ingest_body: dict[str, Any] = {}
    search_body: dict[str, Any] = {}

    def ingest_handler(request: httpx.Request) -> httpx.Response:
        ingest_body.update(json.loads(request.read().decode("utf-8")))
        return httpx.Response(200, json=INGEST_RESPONSE)

    def search_handler(request: httpx.Request) -> httpx.Response:
        search_body.update(json.loads(request.read().decode("utf-8")))
        return httpx.Response(200, json={"matches": []})

    settings = _settings(google_cloud_project="vextis-erp", rag_mock_embeddings_enabled=False)
    embedder = _StubVertexEmbedder()

    ingestor = DocumentIngestor(
        settings,
        tenant_id="demo-tenant",
        embedder=embedder,
        transport=httpx.MockTransport(ingest_handler),
    )
    retriever = KnowledgeRetriever(
        settings=settings,
        tenant_id="demo-tenant",
        embedder=embedder,
        transport=httpx.MockTransport(search_handler),
    )

    await ingestor.ingest(
        document_uri="urn:vextis:policy:commercial",
        file_name="commercial_policy.md",
        content_type="text/markdown",
        text=POLICY_TEXT,
    )
    await retriever.search("payment terms")

    assert ingest_body["embeddingSpace"] == search_body["embeddingSpace"]
    assert ingest_body["embeddingSpace"] == "vertex:text-embedding-004:768"


@pytest.mark.asyncio
async def test_a_different_model_produces_a_different_space() -> None:
    ingest_body: dict[str, Any] = {}
    search_body: dict[str, Any] = {}

    def ingest_handler(request: httpx.Request) -> httpx.Response:
        ingest_body.update(json.loads(request.read().decode("utf-8")))
        return httpx.Response(200, json=INGEST_RESPONSE)

    def search_handler(request: httpx.Request) -> httpx.Response:
        search_body.update(json.loads(request.read().decode("utf-8")))
        return httpx.Response(200, json={"matches": []})

    settings = _settings(google_cloud_project="vextis-erp", rag_mock_embeddings_enabled=False)

    ingestor = DocumentIngestor(
        settings,
        tenant_id="demo-tenant",
        embedder=_StubVertexEmbedder(model="text-embedding-004"),
        transport=httpx.MockTransport(ingest_handler),
    )
    retriever = KnowledgeRetriever(
        settings=settings,
        tenant_id="demo-tenant",
        embedder=_StubVertexEmbedder(model="text-embedding-005"),
        transport=httpx.MockTransport(search_handler),
    )

    await ingestor.ingest(
        document_uri="urn:vextis:policy:commercial",
        file_name="commercial_policy.md",
        content_type="text/markdown",
        text=POLICY_TEXT,
    )
    await retriever.search("payment terms")

    # Enterprise Core scopes the search to the space it is given, so this
    # mismatch returns nothing rather than nonsense.
    assert ingest_body["embeddingSpace"] != search_body["embeddingSpace"]


@pytest.mark.asyncio
async def test_ingestion_denial_is_reported_not_swallowed() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(403, json={"error": "Agent not allowed for tool"})

    ingestor = DocumentIngestor(
        _settings(),
        tenant_id="demo-tenant",
        transport=httpx.MockTransport(handler),
    )

    with pytest.raises(CoreToolRejectedError):
        await ingestor.ingest(
            document_uri="urn:vextis:policy:commercial",
            file_name="commercial_policy.md",
            content_type="text/markdown",
            text=POLICY_TEXT,
        )


@pytest.mark.asyncio
async def test_empty_documents_are_refused_before_any_call() -> None:
    called = False

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal called
        called = True
        return httpx.Response(200, json=INGEST_RESPONSE)

    ingestor = DocumentIngestor(
        _settings(),
        tenant_id="demo-tenant",
        transport=httpx.MockTransport(handler),
    )

    with pytest.raises(ValueError):
        await ingestor.ingest(
            document_uri="urn:vextis:policy:commercial",
            file_name="empty.md",
            content_type="text/markdown",
            text="   \n  ",
        )
    assert called is False


def test_ingestion_requires_a_tenant() -> None:
    with pytest.raises(ValueError):
        DocumentIngestor(_settings(), tenant_id="   ")


def test_cli_reports_a_missing_file_without_calling_core(tmp_path: Any) -> None:
    exit_code = main(
        [
            "--tenant",
            "demo-tenant",
            "--document-uri",
            "urn:vextis:policy:commercial",
            "--file",
            str(tmp_path / "does-not-exist.md"),
        ]
    )
    assert exit_code == 2
