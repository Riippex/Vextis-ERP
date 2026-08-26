CREATE TABLE chat_message_agent_activities (
    message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    sequence SMALLINT NOT NULL,
    agent_id VARCHAR(150) NOT NULL,
    agent_version VARCHAR(30) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(30) NOT NULL,
    tool_names TEXT[] NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, sequence),
    CONSTRAINT ck_chat_agent_activity_sequence CHECK (sequence BETWEEN 0 AND 3),
    CONSTRAINT ck_chat_agent_activity_tool_count CHECK (cardinality(tool_names) <= 8)
);

CREATE INDEX ix_chat_agent_activity_recent
    ON chat_message_agent_activities (occurred_at DESC);
