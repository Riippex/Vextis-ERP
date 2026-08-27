CREATE TABLE chat_message_memory_evidence (
    message_id UUID PRIMARY KEY REFERENCES chat_messages(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    available BOOLEAN NOT NULL,
    context_count INTEGER NOT NULL,
    preference_stored BOOLEAN NOT NULL,
    CONSTRAINT ck_chat_memory_provider CHECK (provider = 'VERTEX_AI_MEMORY_BANK'),
    CONSTRAINT ck_chat_memory_context_count CHECK (context_count BETWEEN 0 AND 5),
    CONSTRAINT ck_chat_memory_unavailable_context CHECK (available OR context_count = 0)
);
