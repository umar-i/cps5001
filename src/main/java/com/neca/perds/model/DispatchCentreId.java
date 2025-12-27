package com.neca.perds.model;

import java.util.Objects;

public record DispatchCentreId(String value) {
    public DispatchCentreId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        IdValidation.validateAlphanumericId(value, "DispatchCentreId");
    }

    @Override
    public String toString() {
        return value;
    }
}

