package com.neca.perds.metrics;

import com.neca.perds.dispatch.DispatchCommand;
import com.neca.perds.dispatch.DispatchDecision;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.system.SystemSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ScenarioSummary(
        int incidentsTotal,
        long incidentsResolved,
        long incidentsQueued,
        int unitsTotal,
        long decisions,
        long assignCommands,
        long rerouteCommands,
        long cancelCommands,
        double computeAvgMillis,
        long computeP95Millis,
        long computeMaxMillis,
        double etaAvgSeconds,
        long etaP95Seconds,
        double waitAvgSeconds,
        long waitP95Seconds
) {
    public static ScenarioSummary from(SystemSnapshot snapshot, InMemoryMetricsCollector metrics) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(metrics, "metrics");

        long resolved = snapshot.incidents().stream().filter(i -> i.status() == IncidentStatus.RESOLVED).count();
        long queued = snapshot.incidents().stream().filter(i -> i.status() == IncidentStatus.QUEUED).count();

        long assigns = 0;
        long reroutes = 0;
        long cancels = 0;
        for (var record : metrics.commandsApplied()) {
            switch (record.command()) {
                case DispatchCommand.AssignUnitCommand ignored -> assigns++;
                case DispatchCommand.RerouteUnitCommand ignored -> reroutes++;
                case DispatchCommand.CancelAssignmentCommand ignored -> cancels++;
            }
        }

        List<Long> computeMillis = new ArrayList<>();
        for (var record : metrics.computations()) {
            computeMillis.add(record.elapsed().toMillis());
        }

        Map<IncidentId, Instant> reportedAtByIncidentId = new HashMap<>();
        for (Incident incident : snapshot.incidents()) {
            reportedAtByIncidentId.put(incident.id(), incident.reportedAt());
        }

        List<Long> etaSeconds = new ArrayList<>();
        List<Long> waitSeconds = new ArrayList<>();
        for (var record : metrics.decisions()) {
            DispatchDecision decision = record.decision();
            var assignment = decision.assignment();
            etaSeconds.add(assignment.route().totalTravelTime().toSeconds());

            Instant reportedAt = reportedAtByIncidentId.get(assignment.incidentId());
            if (reportedAt != null && !record.at().isBefore(reportedAt)) {
                waitSeconds.add(Duration.between(reportedAt, record.at()).toSeconds());
            }
        }

        return new ScenarioSummary(
                snapshot.incidents().size(),
                resolved,
                queued,
                snapshot.units().size(),
                metrics.decisions().size(),
                assigns,
                reroutes,
                cancels,
                average(computeMillis),
                percentile(computeMillis, 0.95),
                max(computeMillis),
                average(etaSeconds),
                percentile(etaSeconds, 0.95),
                average(waitSeconds),
                percentile(waitSeconds, 0.95)
        );
    }

    private static double average(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return sum / (double) values.size();
    }

    private static long max(List<Long> values) {
        long max = 0L;
        for (long value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) {
            return 0L;
        }
        if (Double.isNaN(p) || p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("p must be in [0, 1]");
        }

        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());

        int index = (int) Math.ceil(p * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}

