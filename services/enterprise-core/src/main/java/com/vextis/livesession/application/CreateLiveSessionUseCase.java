package com.vextis.livesession.application;

public interface CreateLiveSessionUseCase {
    LiveSessionCredential create(CreateLiveSessionCommand command);
}
