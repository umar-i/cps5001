package com.neca.perds.prediction;

import com.neca.perds.model.Incident;
import com.neca.perds.model.ZoneId;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ExponentialSmoothingDemandPredictor implements DemandPredictor {
    private final ZoneAssigner zoneAssigner;
    private final double alpha;
    private final Map<ZoneId, Double> smoothed = new HashMap<>();

    public ExponentialSmoothingDemandPredictor() {
        this(nodeId -> new ZoneId(nodeId.value()), 0.3);
    }

    public ExponentialSmoothingDemandPredictor(ZoneAssigner zoneAssigner, double alpha) {
        this.zoneAssigner = Objects.requireNonNull(zoneAssigner, "zoneAssigner");
        if (Double.isNaN(alpha) || alpha <= 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be in (0, 1]");
        }
        this.alpha = alpha;
    }

    @Override
    public void observe(Incident incident) {
        Objects.requireNonNull(incident, "incident");

        ZoneId zone = zoneAssigner.zoneFor(incident.locationNodeId());

        double decay = 1.0 - alpha;
        for (var entry : smoothed.entrySet()) {
            entry.setValue(entry.getValue() * decay);
        }
        smoothed.merge(zone, alpha, Double::sum);
    }

    @Override
    public DemandForecast forecast(Instant at, Duration horizon) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(horizon, "horizon");
        return new DemandForecast(at, horizon, Map.copyOf(smoothed));
    }
}
