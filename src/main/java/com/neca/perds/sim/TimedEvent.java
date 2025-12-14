package com.neca.perds.sim;

import java.time.Instant;
import java.util.Objects;

public record TimedEvent(Instant time, SystemCommand command) implements Comparable<TimedEvent> {
    public TimedEvent {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(command, "command");
    }

    @Override
    public int compareTo(TimedEvent other) {
        return time.compareTo(other.time);
    }
}

