import hashlib
import logging
from typing import Any
from uuid import uuid4

import httpx
from pydantic import BaseModel, ConfigDict, Field

from vextis_agents.app.config import Settings
from vextis_agents.rag.chunking import chunk_text
from vextis_agents.rag.embedding import TextEmbedder, build_text_embedder
from vextis_agents.tools.core_api.planning import (
    CoreToolRejectedError,
    CoreToolUnavailableError,
    GoogleIdentityTokenProvider,
    IdentityTokenProvider,
)

logger = logging.getLogger(__name__)

INGESTOR_AGENT_ID = "vextis_document_ingestor"
MAX_CHUNKS_PER_DOCUMENT = 500


class IngestedDocument(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    document_id: str = Field(alias="documentId")
    document_uri: str = Field(alias="documentUri")
    version: int
    status: str
    chunk_count: int = Field(alias="chunkCount")


class DocumentIngestor:
    """
    Chunks a document, embeds it, and hands the result to Enterprise Core.

    The embedder is the same one the retriever queries with, so a document is
    always indexed in the space its future queries will search. Enterprise Core
    remains the only writer: it re-checks the tenant and the
    ingest_knowledge_document allowlist entry before storing anything.
    """

    def __init__(
        self,
        settings: Settings,
        tenant_id: str,
        correlation_id: str | None = None,
        agent_id: str = INGESTOR_AGENT_ID,
        embedder: TextEmbedder | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
        identity_token_provider: IdentityTokenProvider | None = None,
    ) -> None:
        if settings.agent_tools_token is None:
            raise ValueError("VEXTIS_AGENT_TOOLS_TOKEN is required for document ingestion")
        if not tenant_id.strip():
            raise ValueError("A trusted tenant is required for document ingestion")

        self._base_url = settings.enterprise_core_url.rstrip("/")
        self._tenant_id = tenant_id
        self._correlation_id = correlation_id or str(uuid4())
        self._agent_id = agent_id
        self._service_token = settings.agent_tools_token.get_secret_value()
        self._embedder = embedder or build_text_embedder(settings)
        self._transport = transport
        self._identity_token_provider = identity_token_provider

        if settings.enterprise_core_audience and identity_token_provider is None:
            self._identity_token_provider = GoogleIdentityTokenProvider(
                settings.enterprise_core_audience
            )

    @property
    def embedding_space(self) -> str:
        return self._embedder.space.identifier

    async def ingest(
        self,
        *,
        document_uri: str,
        file_name: str,
        content_type: str,
        text: str,
        metadata: dict[str, Any] | None = None,
    ) -> IngestedDocument:
        body = text.strip()
        if not body:
            raise ValueError("Cannot ingest an empty document")

        chunks = chunk_text(body, metadata=metadata)
        if not chunks:
            raise ValueError("Chunking produced no content for this document")
        if len(chunks) > MAX_CHUNKS_PER_DOCUMENT:
            raise ValueError(
                f"Document produced {len(chunks)} chunks, above the {MAX_CHUNKS_PER_DOCUMENT} "
                "Enterprise Core accepts in one request"
            )

        vectors = await self._embedder.embed_texts([chunk.chunk_text for chunk in chunks])
        if len(vectors) != len(chunks):
            raise CoreToolUnavailableError(
                f"Embedder returned {len(vectors)} vectors for {len(chunks)} chunks"
            )

        payload = {
            "documentUri": document_uri,
            "fileName": file_name,
            "contentType": content_type,
            "contentHash": content_hash(body),
            "embeddingSpace": self.embedding_space,
            "chunks": [
                {
                    "chunkIndex": chunk.chunk_index,
                    "chunkText": chunk.chunk_text,
                    "tokenCount": chunk.token_count,
                    "embedding": vector,
                    "metadata": chunk.metadata,
                }
                for chunk, vector in zip(chunks, vectors, strict=True)
            ],
        }

        headers = {
            "Authorization": f"Bearer {self._service_token}",
            "X-Tenant-Id": self._tenant_id,
            "X-Agent-Id": self._agent_id,
            "X-Correlation-Id": self._correlation_id,
        }
        if self._identity_token_provider is not None:
            try:
                identity_token = await self._identity_token_provider()
            except Exception as exc:
                raise CoreToolUnavailableError(
                    "Cloud Run identity token could not be obtained for document ingestion"
                ) from exc
            headers["X-Serverless-Authorization"] = f"Bearer {identity_token}"

        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(60.0, connect=3.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    "/internal/agent-tools/v1/rag/documents",
                    headers=headers,
                    json=payload,
                )
        except httpx.HTTPError as exc:
            raise CoreToolUnavailableError(
                "Enterprise Core document ingestion could not be reached"
            ) from exc

        if 200 <= response.status_code < 300:
            ingested = IngestedDocument.model_validate(response.json())
            logger.info(
                "Ingested %s as version %s with %s chunks in %s",
                ingested.document_uri,
                ingested.version,
                ingested.chunk_count,
                self.embedding_space,
            )
            return ingested
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")

        raise CoreToolRejectedError(
            f"Enterprise Core rejected document ingestion with status {response.status_code}"
        )


def content_hash(text: str) -> str:
    """SHA-256 of the document body; Enterprise Core uses it to version and dedupe."""
    return hashlib.sha256(text.encode("utf-8")).hexdigest()
