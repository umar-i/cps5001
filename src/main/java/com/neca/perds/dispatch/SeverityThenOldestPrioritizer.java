package com.neca.perds.dispatch;

import com.neca.perds.model.Incident;

import java.util.Comparator;

public final class SeverityThenOldestPrioritizer implements IncidentPrioritizer {
    @Override
    public Comparator<Incident> comparator() {
        return Comparator
                .comparingInt((Incident i) -> i.severity().level()).reversed()
                .thenComparing(Incident::reportedAt);
    }
}

