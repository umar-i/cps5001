package com.neca.perds.metrics;

import com.neca.perds.dispatch.DispatchDecision;
import com.neca.perds.dispatch.DispatchCommand;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InMemoryMetricsCollector implements MetricsCollector {
    public record DispatchComputationRecord(
            Instant at,
            Duration elapsed,
            int incidentsConsidered,
            int unitsConsidered
    ) {
        public DispatchComputationRecord {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(elapsed, "elapsed");
        }
    }

    public record DispatchDecisionRecord(Instant at, DispatchDecision decision) {
        public DispatchDecisionRecord {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(decision, "decision");
        }
    }

    public record DispatchCommandAppliedRecord(Instant at, DispatchCommand command) {
        public DispatchCommandAppliedRecord {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(command, "command");
        }
    }

    private final List<DispatchComputationRecord> computations = new ArrayList<>();
    private final List<DispatchDecisionRecord> decisions = new ArrayList<>();
    private final List<DispatchCommandAppliedRecord> commandsApplied = new ArrayList<>();

    @Override
    public void recordDispatchComputation(Instant at, Duration elapsed, int incidentsConsidered, int unitsConsidered) {
        computations.add(new DispatchComputationRecord(at, elapsed, incidentsConsidered, unitsConsidered));
    }

    @Override
    public void recordDispatchDecision(Instant at, DispatchDecision decision) {
        decisions.add(new DispatchDecisionRecord(at, decision));
    }

    @Override
    public void recordDispatchCommandApplied(Instant at, DispatchCommand command) {
        commandsApplied.add(new DispatchCommandAppliedRecord(at, command));
    }

    public List<DispatchComputationRecord> computations() {
        return List.copyOf(computations);
    }

    public List<DispatchDecisionRecord> decisions() {
        return List.copyOf(decisions);
    }

    public List<DispatchCommandAppliedRecord> commandsApplied() {
        return List.copyOf(commandsApplied);
    }
}
