"""Evidence retrieval boundary."""

from vextis_agents.rag.chunking import DocumentChunk, chunk_text
from vextis_agents.rag.embedding import (
    DeterministicMockEmbedder,
    TextEmbedder,
    VertexTextEmbedder,
    build_text_embedder,
)
from vextis_agents.rag.retriever import KnowledgeRetriever
from vextis_agents.rag.security import (
    KnowledgeMatch,
    format_untrusted_knowledge_evidence,
    sanitize_untrusted_text,
)

__all__ = [
    "DeterministicMockEmbedder",
    "DocumentChunk",
    "KnowledgeMatch",
    "KnowledgeRetriever",
    "TextEmbedder",
    "VertexTextEmbedder",
    "build_text_embedder",
    "chunk_text",
    "format_untrusted_knowledge_evidence",
    "sanitize_untrusted_text",
]
