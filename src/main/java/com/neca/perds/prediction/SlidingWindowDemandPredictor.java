package com.neca.perds.prediction;

import com.neca.perds.model.Incident;
import com.neca.perds.model.ZoneId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class SlidingWindowDemandPredictor implements DemandPredictor {
    private final ZoneAssigner zoneAssigner;
    private final Duration window;
    private final Map<ZoneId, Deque<Instant>> incidentTimesByZone = new HashMap<>();

    private Instant lastObservedAt;

    public SlidingWindowDemandPredictor() {
        this(nodeId -> new ZoneId(nodeId.value()), Duration.ofHours(1));
    }

    public SlidingWindowDemandPredictor(ZoneAssigner zoneAssigner, Duration window) {
        this.zoneAssigner = Objects.requireNonNull(zoneAssigner, "zoneAssigner");
        this.window = Objects.requireNonNull(window, "window");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be > 0");
        }
        if (window.toMillis() == 0) {
            throw new IllegalArgumentException("window must be >= 1ms");
        }
    }

    @Override
    public void observe(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        Instant at = incident.reportedAt();
        if (lastObservedAt != null && at.isBefore(lastObservedAt)) {
            throw new IllegalArgumentException("Incidents must be observed in non-decreasing time order");
        }
        lastObservedAt = at;

        ZoneId zone = zoneAssigner.zoneFor(incident.locationNodeId());
        Deque<Instant> times = incidentTimesByZone.computeIfAbsent(zone, ignored -> new ArrayDeque<>());
        times.addLast(at);
        prune(times, at.minus(window));
    }

    @Override
    public DemandForecast forecast(Instant at, Duration horizon) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(horizon, "horizon");

        long horizonMillis = horizon.toMillis();
        if (horizonMillis < 0) {
            throw new IllegalArgumentException("horizon must be >= 0");
        }
        if (horizonMillis == 0) {
            return new DemandForecast(at, horizon, Map.of());
        }

        Instant cutoff = at.minus(window);

        Map<ZoneId, Double> expected = new HashMap<>();
        long windowMillis = window.toMillis();
        var it = incidentTimesByZone.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Deque<Instant> times = entry.getValue();
            prune(times, cutoff);
            if (times.isEmpty()) {
                it.remove();
                continue;
            }

            double expectedIncidents = times.size() * (horizonMillis / (double) windowMillis);
            expected.put(entry.getKey(), expectedIncidents);
        }

        return new DemandForecast(at, horizon, Map.copyOf(expected));
    }

    private static void prune(Deque<Instant> times, Instant cutoffInclusive) {
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoffInclusive)) {
            times.removeFirst();
        }
    }
}

