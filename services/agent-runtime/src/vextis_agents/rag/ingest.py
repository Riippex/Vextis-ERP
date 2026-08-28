"""
Reproducible entry point for governed document ingestion.

    uv run python -m vextis_agents.rag.ingest \
        --tenant demo-tenant \
        --document-uri urn:vextis:policy:commercial \
        --file docs/commercial_policy.md

Chunks and embeds the file with the same embedder the agents query with, then
posts it to Enterprise Core, which enforces the tenant and the
ingest_knowledge_document allowlist entry before storing anything. There is no
end-user upload UI: this command is the ingestion path.
"""

import argparse
import asyncio
import logging
import mimetypes
import sys
from pathlib import Path

from vextis_agents.app.config import get_settings
from vextis_agents.rag.embedding import EmbeddingConfigurationError, EmbeddingUnavailableError
from vextis_agents.rag.ingestion import DocumentIngestor
from vextis_agents.tools.core_api.planning import CoreToolRejectedError, CoreToolUnavailableError


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="python -m vextis_agents.rag.ingest",
        description="Chunk, embed and ingest one document into the tenant knowledge base.",
    )
    parser.add_argument("--tenant", required=True, help="Tenant that will own the document.")
    parser.add_argument(
        "--document-uri",
        required=True,
        help="Stable identifier, gs://bucket/key or urn:vextis:...",
    )
    parser.add_argument("--file", required=True, type=Path, help="UTF-8 text file to ingest.")
    parser.add_argument(
        "--file-name",
        default=None,
        help="Display name stored with the document; defaults to the file name.",
    )
    parser.add_argument(
        "--content-type",
        default=None,
        help="Content type stored with the document; guessed from the file name by default.",
    )
    parser.add_argument("--correlation-id", default=None, help="Correlation id for audit trails.")
    return parser


async def _run(args: argparse.Namespace) -> int:
    path: Path = args.file
    if not path.is_file():
        print(f"No such file: {path}", file=sys.stderr)
        return 2

    text = path.read_text(encoding="utf-8")
    file_name = args.file_name or path.name
    content_type = args.content_type or mimetypes.guess_type(file_name)[0] or "text/plain"

    try:
        ingestor = DocumentIngestor(
            get_settings(),
            tenant_id=args.tenant,
            correlation_id=args.correlation_id,
        )
    except (EmbeddingConfigurationError, ValueError) as exc:
        print(f"Ingestion is not configured: {exc}", file=sys.stderr)
        return 2

    try:
        document = await ingestor.ingest(
            document_uri=args.document_uri,
            file_name=file_name,
            content_type=content_type,
            text=text,
        )
    except EmbeddingUnavailableError as exc:
        print(f"Embedding failed, nothing was ingested: {exc}", file=sys.stderr)
        return 1
    except CoreToolRejectedError as exc:
        print(f"Enterprise Core rejected the document: {exc}", file=sys.stderr)
        return 1
    except CoreToolUnavailableError as exc:
        print(f"Enterprise Core is unavailable: {exc}", file=sys.stderr)
        return 1

    print(
        f"Ingested {document.document_uri} as version {document.version} "
        f"({document.chunk_count} chunks, {ingestor.embedding_space}, status {document.status})"
    )
    return 0


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")
    args = _build_parser().parse_args(argv)
    return asyncio.run(_run(args))


if __name__ == "__main__":
    raise SystemExit(main())
