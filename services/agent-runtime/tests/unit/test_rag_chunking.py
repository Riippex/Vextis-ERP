from vextis_agents.rag.chunking import approximate_tokens, chunk_text


def test_chunk_empty_text() -> None:
    assert chunk_text("") == []
    assert chunk_text("   \n\t  ") == []


def test_chunk_short_text() -> None:
    text = "Short policy document text."
    chunks = chunk_text(text, chunk_size=100)
    assert len(chunks) == 1
    assert chunks[0].chunk_index == 0
    assert chunks[0].chunk_text == text
    assert chunks[0].token_count > 0


def test_chunk_multi_paragraph() -> None:
    p1 = "Paragraph 1: Vextis is an enterprise ERP system."
    p2 = "Paragraph 2: Billing invoices are issued after stock is reserved."
    p3 = "Paragraph 3: All mutations require idempotent keys."
    text = f"{p1}\n\n{p2}\n\n{p3}"

    chunks = chunk_text(text, chunk_size=80)
    assert len(chunks) >= 2
    assert all(c.token_count > 0 for c in chunks)
    for idx, c in enumerate(chunks):
        assert c.chunk_index == idx


def test_chunk_metadata_propagation() -> None:
    text = "Terms and conditions."
    chunks = chunk_text(text, metadata={"source": "terms.pdf", "author": "legal"})
    assert len(chunks) == 1
    assert chunks[0].metadata["source"] == "terms.pdf"
    assert chunks[0].metadata["author"] == "legal"


def test_approximate_tokens() -> None:
    assert approximate_tokens("abcd") == 1
    assert approximate_tokens("abcdefghijklmnop") == 4
