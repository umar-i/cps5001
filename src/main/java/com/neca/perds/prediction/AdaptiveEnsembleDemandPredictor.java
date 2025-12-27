package com.neca.perds.prediction;

import com.neca.perds.model.Incident;
import com.neca.perds.model.ZoneId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AdaptiveEnsembleDemandPredictor implements DemandPredictor {
    public record Model(String name, DemandPredictor predictor) {
        public Model {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(predictor, "predictor");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    private record ForecastRecord(
            Instant at,
            Instant untilExclusive,
            Map<String, Map<ZoneId, Double>> expectedByModel
    ) {
        private ForecastRecord {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(untilExclusive, "untilExclusive");
            Objects.requireNonNull(expectedByModel, "expectedByModel");
        }
    }

    private final ZoneAssigner zoneAssigner;
    private final List<Model> models;
    private final double learningRate;

    private final Map<String, Double> weightsByModel = new LinkedHashMap<>();
    private final Map<ZoneId, List<Instant>> incidentTimesByZone = new HashMap<>();
    private final Deque<ForecastRecord> pendingForecasts = new ArrayDeque<>();

    private Instant lastObservedAt;

    public AdaptiveEnsembleDemandPredictor() {
        this(
                nodeId -> new ZoneId(nodeId.value()),
                List.of(
                        new Model("slidingWindow", new SlidingWindowDemandPredictor(nodeId -> new ZoneId(nodeId.value()), Duration.ofHours(1))),
                        new Model("expSmoothing", new ExponentialSmoothingDemandPredictor(nodeId -> new ZoneId(nodeId.value()), 0.3))
                ),
                0.25
        );
    }

    public AdaptiveEnsembleDemandPredictor(ZoneAssigner zoneAssigner, List<Model> models, double learningRate) {
        this.zoneAssigner = Objects.requireNonNull(zoneAssigner, "zoneAssigner");
        this.models = List.copyOf(Objects.requireNonNull(models, "models"));
        if (this.models.isEmpty()) {
            throw new IllegalArgumentException("models must not be empty");
        }
        if (Double.isNaN(learningRate) || learningRate <= 0.0) {
            throw new IllegalArgumentException("learningRate must be > 0");
        }
        this.learningRate = learningRate;

        Set<String> names = new HashSet<>();
        for (Model model : this.models) {
            if (!names.add(model.name())) {
                throw new IllegalArgumentException("Duplicate model name: " + model.name());
            }
        }

        double initialWeight = 1.0 / this.models.size();
        for (Model model : this.models) {
            weightsByModel.put(model.name(), initialWeight);
        }
    }

    public Map<String, Double> weights() {
        return Map.copyOf(weightsByModel);
    }

    @Override
    public void observe(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        Instant at = incident.reportedAt();

        if (lastObservedAt != null && at.isBefore(lastObservedAt)) {
            throw new IllegalArgumentException("Incidents must be observed in non-decreasing time order");
        }
        lastObservedAt = at;

        ZoneId zoneId = zoneAssigner.zoneFor(incident.locationNodeId());
        incidentTimesByZone.computeIfAbsent(zoneId, ignored -> new ArrayList<>()).add(at);

        for (Model model : models) {
            model.predictor().observe(incident);
        }

        updateWeights(at);
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

        updateWeights(at);

        Map<String, Map<ZoneId, Double>> expectedByModel = new HashMap<>();
        Map<ZoneId, Double> combined = new HashMap<>();
        for (Model model : models) {
            DemandForecast forecast = model.predictor().forecast(at, horizon);
            Map<ZoneId, Double> expected = sanitizeExpected(forecast.expectedIncidents());
            expectedByModel.put(model.name(), expected);

            double weight = weightsByModel.getOrDefault(model.name(), 0.0);
            if (weight <= 0.0) {
                continue;
            }

            for (var entry : expected.entrySet()) {
                combined.merge(entry.getKey(), weight * entry.getValue(), Double::sum);
            }
        }

        pendingForecasts.addLast(new ForecastRecord(at, at.plus(horizon), Map.copyOf(expectedByModel)));
        return new DemandForecast(at, horizon, Map.copyOf(combined));
    }

    /**
     * Updates model weights using exponential weighting based on forecast accuracy.
     * 
     * This implements the Hedge algorithm (a form of multiplicative weights update):
     * 1. For each matured forecast, compute prediction error per model
     * 2. Apply exponential penalty: newWeight = oldWeight * exp(-learningRate * error)
     * 3. Normalize weights to sum to 1.0
     * 
     * Models with lower prediction error retain higher weights, making them
     * contribute more to future ensemble forecasts.
     */
    private void updateWeights(Instant now) {
        while (!pendingForecasts.isEmpty()) {
            ForecastRecord record = pendingForecasts.peekFirst();
            if (record.untilExclusive().isAfter(now)) {
                return;
            }

            pendingForecasts.removeFirst();
            Map<ZoneId, Integer> actualCounts = actualCountsBetween(record.at(), record.untilExclusive());

            Map<String, Double> updated = new LinkedHashMap<>();
            double sum = 0.0;
            for (Model model : models) {
                String name = model.name();
                Map<ZoneId, Double> expected = record.expectedByModel().getOrDefault(name, Map.of());
                double error = absoluteError(expected, actualCounts);

                double oldWeight = weightsByModel.getOrDefault(name, 0.0);
                // Exponential penalty: higher error → lower weight
                double newWeight = oldWeight * Math.exp(-learningRate * error);
                if (Double.isNaN(newWeight) || Double.isInfinite(newWeight) || newWeight < 0.0) {
                    newWeight = 0.0;
                }

                updated.put(name, newWeight);
                sum += newWeight;
            }

            // If all weights collapsed to zero, reset to equal weights
            if (!(sum > 0.0) || Double.isNaN(sum) || Double.isInfinite(sum)) {
                resetEqualWeights();
                continue;
            }

            // Normalize weights to sum to 1.0
            for (var entry : updated.entrySet()) {
                weightsByModel.put(entry.getKey(), entry.getValue() / sum);
            }
        }
    }

    private void resetEqualWeights() {
        double weight = 1.0 / models.size();
        for (Model model : models) {
            weightsByModel.put(model.name(), weight);
        }
    }

    private Map<ZoneId, Integer> actualCountsBetween(Instant startInclusive, Instant untilExclusive) {
        Map<ZoneId, Integer> counts = new HashMap<>();
        for (var entry : incidentTimesByZone.entrySet()) {
            int count = countBetween(entry.getValue(), startInclusive, untilExclusive);
            if (count > 0) {
                counts.put(entry.getKey(), count);
            }
        }
        return Map.copyOf(counts);
    }

    private static int countBetween(List<Instant> sortedTimes, Instant startInclusive, Instant untilExclusive) {
        int start = lowerBound(sortedTimes, startInclusive);
        int end = lowerBound(sortedTimes, untilExclusive);
        return Math.max(0, end - start);
    }

    private static int lowerBound(List<Instant> sortedTimes, Instant target) {
        int lo = 0;
        int hi = sortedTimes.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sortedTimes.get(mid).compareTo(target) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static Map<ZoneId, Double> sanitizeExpected(Map<ZoneId, Double> expectedIncidents) {
        if (expectedIncidents.isEmpty()) {
            return Map.of();
        }
        Map<ZoneId, Double> sanitized = new HashMap<>();
        for (var entry : expectedIncidents.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            double value = entry.getValue() == null ? 0.0 : entry.getValue();
            if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
                continue;
            }
            sanitized.put(entry.getKey(), value);
        }
        return Map.copyOf(sanitized);
    }

    private static double absoluteError(Map<ZoneId, Double> expected, Map<ZoneId, Integer> actualCounts) {
        double error = 0.0;

        for (var entry : actualCounts.entrySet()) {
            ZoneId zoneId = entry.getKey();
            int actual = entry.getValue();
            double predicted = expected.getOrDefault(zoneId, 0.0);
            if (Double.isNaN(predicted) || Double.isInfinite(predicted) || predicted < 0.0) {
                predicted = 0.0;
            }
            error += Math.abs(actual - predicted);
        }

        for (var entry : expected.entrySet()) {
            ZoneId zoneId = entry.getKey();
            if (actualCounts.containsKey(zoneId)) {
                continue;
            }
            double predicted = entry.getValue();
            if (Double.isNaN(predicted) || Double.isInfinite(predicted) || predicted <= 0.0) {
                continue;
            }
            error += Math.abs(predicted);
        }

        return error;
    }
}

