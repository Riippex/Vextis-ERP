package com.vextis.conversation.application;

public interface AskVextisUseCase {
    AskVextisResult postMessage(AskVextisCommand command);
}
