"""
Grounded RAG: does the model answer from retrieved evidence, and does it admit
ignorance when the knowledge base has nothing?
"""

import httpx
import pytest

from tests.evals.behavior.conftest import ask, build_fleet
from vextis_agents.app.config import Settings

pytestmark = pytest.mark.model_eval


def _core_that_should_not_be_called(request: httpx.Request) -> httpx.Response:
    return httpx.Response(404, json={"error": "no business read expected in this scenario"})


def _knowledge(matches: list[dict[str, object]]) -> httpx.MockTransport:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"matches": matches})

    return httpx.MockTransport(handler)


PAYMENT_TERMS_MATCH: dict[str, object] = {
    "documentId": "77cc63cc-3c91-4d80-a918-605b7f231cf8",
    "fileName": "commercial_policy.pdf",
    "documentUri": "gs://vextis-demo-docs/commercial_policy.pdf",
    "chunkIndex": 0,
    "chunkText": (
        "Vextis Commercial Policy: standard payment terms for approved corporate "
        "customers are Net 45 days. The maximum standard discount without CFO "
        "approval is 12 percent."
    ),
    "similarityScore": 0.93,
    "metadata": {"section": "commercial"},
}


@pytest.mark.asyncio
async def test_answers_payment_terms_from_retrieved_evidence(settings: Settings) -> None:
    def knowledge_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"matches": [PAYMENT_TERMS_MATCH]})

    fleet = build_fleet(settings, _core_that_should_not_be_called, knowledge_handler)

    turn = await ask(
        fleet,
        "What are our standard payment terms for approved corporate customers? "
        "Check the knowledge base.",
    )

    # Net 45 and 12 percent are deliberately not the values in the seed data or
    # anywhere in the prompts, so answering them can only come from retrieval.
    assert turn.mentions_any("45"), f"reply was not grounded in the retrieved chunk: {turn.reply}"
    assert not turn.mentions_any("net 30", "30 days"), (
        f"reply contradicted the retrieved evidence: {turn.reply}"
    )


@pytest.mark.asyncio
async def test_cites_the_source_document_it_used(settings: Settings) -> None:
    def knowledge_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"matches": [PAYMENT_TERMS_MATCH]})

    fleet = build_fleet(settings, _core_that_should_not_be_called, knowledge_handler)

    turn = await ask(
        fleet,
        "What is the maximum discount without CFO approval, and which document says so?",
    )

    assert turn.mentions_any("12")
    assert turn.mentions_any("commercial_policy", "commercial policy"), (
        f"reply did not attribute the answer to its source: {turn.reply}"
    )


@pytest.mark.asyncio
async def test_admits_it_has_no_evidence_instead_of_inventing_one(settings: Settings) -> None:
    def empty_knowledge(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"matches": []})

    fleet = build_fleet(settings, _core_that_should_not_be_called, empty_knowledge)

    turn = await ask(
        fleet,
        "What is our documented policy on warranty extensions beyond 36 months? "
        "Check the knowledge base.",
    )

    assert turn.mentions_any(
        "no ",
        "not find",
        "could not",
        "cannot",
        "unable",
        "nothing",
        "no documented",
        "no information",
    ), f"reply should have reported an empty knowledge base: {turn.reply}"
    # An invented month count is the failure mode this eval exists for.
    assert not turn.mentions_any("36 months are covered", "the policy states"), (
        f"reply fabricated a policy that was never retrieved: {turn.reply}"
    )
