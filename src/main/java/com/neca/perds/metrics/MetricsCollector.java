package com.neca.perds.metrics;

import com.neca.perds.dispatch.DispatchDecision;
import com.neca.perds.dispatch.DispatchCommand;

import java.time.Duration;
import java.time.Instant;

public interface MetricsCollector {
    void recordDispatchComputation(Instant at, Duration elapsed, int incidentsConsidered, int unitsConsidered);

    void recordDispatchDecision(Instant at, DispatchDecision decision);

    void recordDispatchCommandApplied(Instant at, DispatchCommand command);
}

