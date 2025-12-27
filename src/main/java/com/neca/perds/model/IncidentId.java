package com.neca.perds.model;

import java.util.Objects;

public record IncidentId(String value) {
    public IncidentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        IdValidation.validateIncidentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

