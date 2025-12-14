package com.neca.perds.model;

import java.util.Objects;

public record ZoneId(String value) {
    public ZoneId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

