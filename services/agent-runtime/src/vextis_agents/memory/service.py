from __future__ import annotations

import hashlib
import logging
import re
from dataclasses import dataclass
from typing import Protocol

from google.adk.memory import VertexAiMemoryBankService
from google.adk.memory.memory_entry import MemoryEntry
from google.genai import types

from vextis_agents.app.config import Settings

logger = logging.getLogger(__name__)

MEMORY_PROVIDER = "VERTEX_AI_MEMORY_BANK"
_MEMORY_COMMAND = re.compile(
    r"^\s*(?:remember(?:\s+that|\s+preference)?|recuerda(?:\s+que|\s+preferencia)?)\s*[:,-]?\s+(.+?)\s*$",
    re.IGNORECASE,
)
_ALLOWED_PREFERENCE_WORDS = frozenset(
    {
        "a",
        "and",
        "answer",
        "answers",
        "brief",
        "breve",
        "breves",
        "complete",
        "completa",
        "completas",
        "concise",
        "concisa",
        "concisas",
        "corta",
        "cortas",
        "default",
        "detailed",
        "detallada",
        "detalladas",
        "english",
        "en",
        "español",
        "finance",
        "finanzas",
        "facturación",
        "i",
        "in",
        "inglés",
        "inventory",
        "inventario",
        "language",
        "más",
        "operations",
        "operaciones",
        "prefer",
        "prefiero",
        "preference",
        "responde",
        "response",
        "responses",
        "respuesta",
        "respuestas",
        "sales",
        "short",
        "spanish",
        "style",
        "thorough",
        "ventas",
        "workspace",
        "y",
    }
)
_SAFE_MEMORY_VALUES = frozenset(
    {
        "Response style preference: concise.",
        "Response style preference: detailed.",
        "Language preference: English.",
        "Language preference: Spanish.",
        "Workspace preference: CRM and Sales.",
        "Workspace preference: Inventory and Operations.",
        "Workspace preference: Finance and Billing.",
    }
)


class UnsafePreferenceError(ValueError):
    """The user requested memory, but the value is outside the safe preference policy."""


class MemoryWriteUnavailableError(RuntimeError):
    """A requested preference could not be durably written."""


@dataclass(frozen=True)
class MemoryTurn:
    provider: str
    available: bool
    context: tuple[str, ...]
    preference_stored: bool


class AgentMemory(Protocol):
    @property
    def provider(self) -> str: ...

    async def prepare_turn(self, tenant_id: str, actor_id: str, message: str) -> MemoryTurn: ...


def _memory_scope(tenant_id: str, actor_id: str) -> str:
    """Create a stable pseudonymous scope without sending Firebase UIDs to Memory Bank."""
    digest = hashlib.sha256(f"{tenant_id}\0{actor_id}".encode()).hexdigest()
    return f"vextis_{digest}"


def _extract_preference(message: str) -> str | None:
    match = _MEMORY_COMMAND.fullmatch(message)
    if match is None:
        return None
    requested = match.group(1).strip().casefold()
    words = set(re.findall(r"[^\W\d_]+", requested, re.UNICODE))
    if not words or not words.issubset(_ALLOWED_PREFERENCE_WORDS):
        raise UnsafePreferenceError(
            "Only response style, language, or default workspace preferences can be remembered"
        )

    if re.search(r"\b(?:concise|brief|short|concisas?|breves?|cortas?)\b", requested):
        return "Response style preference: concise."
    if re.search(r"\b(?:detailed|thorough|detalladas?|completas?)\b", requested):
        return "Response style preference: detailed."
    if re.search(r"\b(?:english|ingl[eé]s)\b", requested):
        return "Language preference: English."
    if re.search(r"\b(?:spanish|espa[nñ]ol)\b", requested):
        return "Language preference: Spanish."
    if re.search(r"\b(?:crm|sales|ventas?)\b", requested):
        return "Workspace preference: CRM and Sales."
    if re.search(r"\b(?:inventory|inventario|operations|operaciones)\b", requested):
        return "Workspace preference: Inventory and Operations."
    if re.search(r"\b(?:finance|finanzas?|billing|facturaci[oó]n)\b", requested):
        return "Workspace preference: Finance and Billing."

    raise UnsafePreferenceError(
        "Only response style, language, or default workspace preferences can be remembered"
    )


def _is_memory_command(message: str) -> bool:
    return _MEMORY_COMMAND.fullmatch(message) is not None


class VertexAgentMemory:
    """Bounded preference memory backed by Vertex AI Agent Engine Memory Bank."""

    def __init__(self, service: VertexAiMemoryBankService) -> None:
        self._service = service

    @property
    def provider(self) -> str:
        return MEMORY_PROVIDER

    async def prepare_turn(self, tenant_id: str, actor_id: str, message: str) -> MemoryTurn:
        scope = _memory_scope(tenant_id, actor_id)
        preference = _extract_preference(message)
        stored = False
        if preference is not None:
            try:
                await self._service.add_memory(
                    app_name="vextis_ask_vextis",
                    user_id=scope,
                    memories=[
                        MemoryEntry(
                            author="user",
                            content=types.Content(
                                role="user", parts=[types.Part.from_text(text=preference)]
                            ),
                        )
                    ],
                    custom_metadata={"ttl": "2592000s", "wait_for_completion": True},
                )
                stored = True
            except Exception as exception:
                logger.warning("Memory Bank preference write failed: %s", type(exception).__name__)
                raise MemoryWriteUnavailableError(
                    "The preference could not be saved to durable memory"
                ) from exception

        try:
            response = await self._service.search_memory(
                app_name="vextis_ask_vextis", user_id=scope, query=message
            )
        except Exception as exception:
            logger.warning("Memory Bank retrieval failed: %s", type(exception).__name__)
            return MemoryTurn(self.provider, False, (), stored)

        context: list[str] = []
        for memory in response.memories[:5]:
            for part in memory.content.parts or []:
                if part.text and (text := part.text.strip()) in _SAFE_MEMORY_VALUES:
                    context.append(text[:500])
                    break
        return MemoryTurn(self.provider, True, tuple(context), stored)


def create_agent_memory(settings: Settings) -> AgentMemory | None:
    if not settings.memory_bank_enabled:
        return None
    if not settings.google_cloud_project or not settings.memory_bank_agent_engine_id:
        raise ValueError(
            "Memory Bank requires GOOGLE_CLOUD_PROJECT and VEXTIS_MEMORY_BANK_AGENT_ENGINE_ID"
        )
    return VertexAgentMemory(
        VertexAiMemoryBankService(
            project=settings.google_cloud_project,
            location=settings.memory_bank_location,
            agent_engine_id=settings.memory_bank_agent_engine_id,
        )
    )


def is_memory_command(message: str) -> bool:
    return _is_memory_command(message)
