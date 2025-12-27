package com.neca.perds.model;

import java.util.Objects;

public record NodeId(String value) {
    public NodeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        IdValidation.validateAlphanumericId(value, "NodeId");
    }

    @Override
    public String toString() {
        return value;
    }
}

