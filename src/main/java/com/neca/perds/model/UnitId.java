package com.neca.perds.model;

import java.util.Objects;

public record UnitId(String value) {
    public UnitId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        IdValidation.validateUnitId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

