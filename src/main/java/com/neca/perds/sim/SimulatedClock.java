package com.neca.perds.sim;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class SimulatedClock implements SimulationClock {
    private Instant now;

    public SimulatedClock(Instant initialTime) {
        this.now = Objects.requireNonNull(initialTime, "initialTime");
    }

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public void advanceTo(Instant instant) {
        now = Objects.requireNonNull(instant, "instant");
    }

    @Override
    public void advanceBy(Duration duration) {
        now = now.plus(Objects.requireNonNull(duration, "duration"));
    }
}

