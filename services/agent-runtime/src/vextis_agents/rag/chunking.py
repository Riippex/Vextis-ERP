import re
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class DocumentChunk(BaseModel):
    model_config = ConfigDict(extra="forbid")

    chunk_index: int = Field(ge=0)
    chunk_text: str = Field(min_length=1)
    token_count: int = Field(ge=0)
    metadata: dict[str, Any] = Field(default_factory=dict)


def approximate_tokens(text: str) -> int:
    """Rough estimation: ~4 chars per token for English text."""
    return max(1, len(text) // 4)


def chunk_text(
    text: str,
    chunk_size: int = 500,
    chunk_overlap: int = 50,
    metadata: dict[str, Any] | None = None,
) -> list[DocumentChunk]:
    """
    Splits text into overlapping chunks, attempting to break at paragraphs or sentences
    whenever possible.
    """
    clean_text = text.strip()
    if not clean_text:
        return []

    base_metadata = metadata.copy() if metadata else {}

    if len(clean_text) <= chunk_size:
        return [
            DocumentChunk(
                chunk_index=0,
                chunk_text=clean_text,
                token_count=approximate_tokens(clean_text),
                metadata=base_metadata,
            )
        ]

    # Split by double newline (paragraphs) first
    paragraphs = re.split(r"(\n\s*\n)", clean_text)
    chunks: list[str] = []
    current_chunk = ""

    for segment in paragraphs:
        if not segment:
            continue
        if len(current_chunk) + len(segment) <= chunk_size:
            current_chunk += segment
        else:
            if current_chunk.strip():
                chunks.append(current_chunk.strip())
            # If segment itself is larger than chunk_size, split by sentences or window
            if len(segment) > chunk_size:
                sentences = re.split(r"((?<=[.!?])\s+)", segment)
                sub_chunk = ""
                for s in sentences:
                    if len(sub_chunk) + len(s) <= chunk_size:
                        sub_chunk += s
                    else:
                        if sub_chunk.strip():
                            chunks.append(sub_chunk.strip())
                        # If single sentence exceeds chunk_size, hard slice
                        if len(s) > chunk_size:
                            start = 0
                            step = max(1, chunk_size - chunk_overlap)
                            while start < len(s):
                                part = s[start : start + chunk_size].strip()
                                if part:
                                    chunks.append(part)
                                start += step
                            sub_chunk = ""
                        else:
                            sub_chunk = s
                current_chunk = sub_chunk
            else:
                current_chunk = segment

    if current_chunk.strip():
        chunks.append(current_chunk.strip())

    # Build overlapping list
    result: list[DocumentChunk] = []
    for idx, chunk_str in enumerate(chunks):
        result.append(
            DocumentChunk(
                chunk_index=idx,
                chunk_text=chunk_str,
                token_count=approximate_tokens(chunk_str),
                metadata=base_metadata,
            )
        )
    return result
