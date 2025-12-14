package com.neca.perds.dispatch;

import java.util.Map;
import java.util.Objects;

public record DispatchRationale(double score, Map<String, Double> components) {
    public DispatchRationale {
        Objects.requireNonNull(components, "components");
    }
}

