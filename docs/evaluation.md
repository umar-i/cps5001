# Evaluation Plan (2:2 → 2:1)

This file captures a lightweight, reproducible evaluation method that can be expanded for the final report.

## How To Run

Prereqs: Java 21+, Maven 3.9+.

### Scenario 1: Reallocation + Pre-position (2:2 baseline)

Runs the existing mini scenario (unit becomes unavailable; later a pre-position event).

- `mvn -q -DskipTests package`
- `java -jar target/perds-0.1.0-SNAPSHOT.jar scenario data/scenarios/mini-nodes.csv data/scenarios/mini-edges.csv data/scenarios/mini-events.csv data/out`

### Scenario 2: Route Invalidation on Edge Closure (2:1 update handling)

Demonstrates real-time route change handling: an edge used by the current assignment is closed, which triggers cancellation + reassignment.

- `mvn -q -DskipTests package`
- `java -jar target/perds-0.1.0-SNAPSHOT.jar scenario data/scenarios/mini-nodes.csv data/scenarios/mini-edges.csv data/scenarios/edge-closure-events.csv data/out`

### Scenario 3: Congestion + Demand Spike + Pre-positioning (Lower First)

Uses a 4x4 grid network to exercise:
- rerouting on travel-time changes (congestion)
- sliding-window demand prediction (incident spike)
- multi-hotspot pre-positioning (move a limited number of available units)

- `mvn -q -DskipTests package`
- `java -jar target/perds-0.1.0-SNAPSHOT.jar scenario data/scenarios/grid-4x4-nodes.csv data/scenarios/grid-4x4-edges.csv data/scenarios/grid-4x4-events.csv data/out`

## Metrics Exported

The CLI scenario runner writes CSVs to the chosen output directory:

- `dispatch_computations.csv`: dispatch compute time per event step
- `dispatch_decisions.csv`: (applied) assignment decisions with scores/components
- `dispatch_commands_applied.csv`: applied dispatch commands, including cancellations

The scenario runner also prints a small summary (compute timings, command counts, and basic decision stats) to stdout.

## What To Analyse (Report-Friendly)

- Dispatch computation time vs scenario size/complexity.
- Number of cancellations/reassignments triggered by unit unavailability and by edge closures.
- Comparison of decisions/commands across scenarios (e.g., does the system recover quickly after a route change?).

