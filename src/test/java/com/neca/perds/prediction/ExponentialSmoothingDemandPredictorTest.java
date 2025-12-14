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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExponentialSmoothingDemandPredictorTest {
    @Test
    void observeAndForecast_returnsSmoothedZoneScores() {
        DemandPredictor predictor = new ExponentialSmoothingDemandPredictor(nodeId -> new ZoneId(nodeId.value()), 0.5);

        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        predictor.observe(incident("I1", "A", t0));
        predictor.observe(incident("I2", "A", t0.plusSeconds(10)));
        predictor.observe(incident("I3", "B", t0.plusSeconds(20)));

        DemandForecast forecast = predictor.forecast(t0, Duration.ofMinutes(30));
        assertEquals(t0, forecast.generatedAt());
        assertEquals(Duration.ofMinutes(30), forecast.horizon());

        assertEquals(0.375, forecast.expectedIncidents().get(new ZoneId("A")), 1e-9);
        assertEquals(0.5, forecast.expectedIncidents().get(new ZoneId("B")), 1e-9);
    }

    private static Incident incident(String id, String nodeId, Instant at) {
        return new Incident(
                new IncidentId(id),
                new NodeId(nodeId),
                IncidentSeverity.MEDIUM,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                at,
                Optional.empty()
        );
    }
}

