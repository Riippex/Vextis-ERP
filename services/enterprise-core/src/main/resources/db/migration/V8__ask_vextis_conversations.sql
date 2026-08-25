CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_conversations_tenant ON conversations (tenant_id);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender VARCHAR(20) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_chat_messages_sender CHECK (sender IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_chat_messages_kind CHECK (kind IN ('TEXT', 'VOICE_TRANSCRIPT'))
);

CREATE INDEX ix_chat_messages_conversation ON chat_messages (conversation_id, occurred_at);
