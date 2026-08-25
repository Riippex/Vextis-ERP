package com.vextis.livesession.application;

import java.util.UUID;

public interface CloseLiveSessionUseCase {
    boolean close(String tenantId, UUID sessionId);
}
