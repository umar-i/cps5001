package com.neca.perds.sim;

import java.time.Duration;
import java.time.Instant;

public interface SimulationClock {
    Instant now();

    void advanceTo(Instant instant);

    void advanceBy(Duration duration);
}

