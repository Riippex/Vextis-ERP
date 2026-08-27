import logging
from uuid import uuid4

import httpx

from vextis_agents.app.config import Settings
from vextis_agents.rag.embedding import TextEmbedder, build_text_embedder
from vextis_agents.rag.security import KnowledgeMatch, format_untrusted_knowledge_evidence
from vextis_agents.tools.core_api.planning import (
    CoreToolRejectedError,
    CoreToolUnavailableError,
    GoogleIdentityTokenProvider,
    IdentityTokenProvider,
)

logger = logging.getLogger(__name__)


class KnowledgeRetriever:
    """
    Tenant-scoped client that queries Enterprise Core's RAG search endpoint
    with query vector embeddings and returns grounded knowledge matches.
    """

    def __init__(
        self,
        settings: Settings,
        tenant_id: str,
        correlation_id: str | None = None,
        agent_id: str = "vextis_coordinator",
        embedder: TextEmbedder | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
        identity_token_provider: IdentityTokenProvider | None = None,
    ) -> None:
        if settings.agent_tools_token is None:
            raise ValueError("VEXTIS_AGENT_TOOLS_TOKEN is required for knowledge retrieval")
        if not tenant_id.strip():
            raise ValueError("A trusted tenant is required for knowledge retrieval")

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

    async def search(
        self,
        query: str,
        limit: int = 5,
        min_score: float = 0.0,
    ) -> list[KnowledgeMatch]:
        clean_query = query.strip()
        if not clean_query:
            return []

        embedding = await self._embedder.embed_query(clean_query)
        if not embedding:
            return []

        payload = {
            "query": clean_query[:1000],
            "embedding": embedding,
            "limit": max(1, min(limit, 20)),
            "minScore": max(0.0, min(min_score, 1.0)),
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
                headers["X-Serverless-Authorization"] = f"Bearer {identity_token}"
            except Exception as exc:
                raise CoreToolUnavailableError(
                    "Cloud Run identity token could not be obtained for RAG search"
                ) from exc

        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(10.0, connect=3.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    "/internal/agent-tools/v1/rag/search",
                    headers=headers,
                    json=payload,
                )
        except httpx.HTTPError as exc:
            raise CoreToolUnavailableError("Enterprise Core RAG tool could not be reached") from exc

        if response.status_code == 404:
            return []
        if 200 <= response.status_code < 300:
            data = response.json()
            matches_data = data.get("matches", [])
            return [KnowledgeMatch.model_validate(m) for m in matches_data]
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")

        raise CoreToolRejectedError(
            f"Enterprise Core rejected RAG search with status {response.status_code}"
        )

    async def retrieve_evidence(
        self,
        query: str,
        limit: int = 5,
        min_score: float = 0.0,
    ) -> str:
        """
        Retrieves knowledge matches and returns a safe, structured, untrusted
        context string ready for injection into prompt context.
        """
        matches = await self.search(query, limit=limit, min_score=min_score)
        return format_untrusted_knowledge_evidence(matches)
