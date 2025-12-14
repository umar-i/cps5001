package com.neca.perds.sim;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

public final class SimulationEngine {
    private final PriorityQueue<TimedEvent> eventQueue = new PriorityQueue<>();

    public void schedule(TimedEvent event) {
        eventQueue.add(Objects.requireNonNull(event, "event"));
    }

    public void scheduleAll(List<TimedEvent> events) {
        Objects.requireNonNull(events, "events");
        eventQueue.addAll(events);
    }

    public int queuedEventCount() {
        return eventQueue.size();
    }

    public List<TimedEvent> runUntil(SystemCommandExecutor executor, Instant untilExclusive) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(untilExclusive, "untilExclusive");

        List<TimedEvent> executed = new ArrayList<>();
        while (!eventQueue.isEmpty() && eventQueue.peek().time().isBefore(untilExclusive)) {
            TimedEvent event = eventQueue.poll();
            executor.execute(event.command(), event.time());
            executed.add(event);
        }
        return List.copyOf(executed);
    }
}

