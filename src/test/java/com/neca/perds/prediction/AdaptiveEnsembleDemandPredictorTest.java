package com.neca.perds.prediction;

import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentSeverity;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.UnitType;
import com.neca.perds.model.ZoneId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdaptiveEnsembleDemandPredictorTest {
    @Test
    void weightsShiftTowardsLowerErrorModel() {
        ZoneId zoneA = new ZoneId("A");

        var good = new AdaptiveEnsembleDemandPredictor.Model("good", new ConstantDemandPredictor(zoneA, 2.0));
        var bad = new AdaptiveEnsembleDemandPredictor.Model("bad", new ConstantDemandPredictor(zoneA, 0.0));

        var predictor = new AdaptiveEnsembleDemandPredictor(nodeId -> new ZoneId(nodeId.value()), List.of(good, bad), 0.5);

        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");

        DemandForecast initial = predictor.forecast(t0, Duration.ofHours(1));
        assertEquals(1.0, initial.expectedIncidents().get(zoneA), 1e-9);

        predictor.observe(incident("I1", "A", t0.plus(Duration.ofMinutes(10))));
        predictor.observe(incident("I2", "A", t0.plus(Duration.ofMinutes(20))));

        predictor.forecast(t0.plus(Duration.ofHours(1)).plusSeconds(1), Duration.ofHours(1));

        Map<String, Double> weights = predictor.weights();
        assertTrue(weights.get("good") > weights.get("bad"));
        assertEquals(1.0, weights.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);

        DemandForecast after = predictor.forecast(t0.plus(Duration.ofHours(2)), Duration.ofHours(1));
        assertTrue(after.expectedIncidents().get(zoneA) > 1.0);
    }

    private static Incident incident(String id, String nodeId, Instant reportedAt) {
        return new Incident(
                new IncidentId(id),
                new NodeId(nodeId),
                IncidentSeverity.MEDIUM,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                reportedAt,
                Optional.empty()
        );
    }

    private static final class ConstantDemandPredictor implements DemandPredictor {
        private final ZoneId zoneId;
        private final double expectedIncidents;

        private ConstantDemandPredictor(ZoneId zoneId, double expectedIncidents) {
            this.zoneId = zoneId;
            this.expectedIncidents = expectedIncidents;
        }

        @Override
        public void observe(Incident incident) {
        }

        @Override
        public DemandForecast forecast(Instant at, Duration horizon) {
            return new DemandForecast(at, horizon, Map.of(zoneId, expectedIncidents));
        }
    }
}
