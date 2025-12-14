package com.neca.perds.dispatch;

public record DispatchConfig(
        double severityWeight,
        double etaSecondsWeight,
        double distanceKmWeight,
        double resourceAvailabilityWeight,
        double fairnessWeight
) {}

