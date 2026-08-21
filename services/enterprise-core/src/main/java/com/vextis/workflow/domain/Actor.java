package com.vextis.workflow.domain;

public record Actor(Type type, String id) {

    public Actor {
        if (type == null) {
            throw new IllegalArgumentException("Actor type is required");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Actor id is required");
        }
    }

    public enum Type {
        USER,
        AGENT,
        SYSTEM
    }
}
