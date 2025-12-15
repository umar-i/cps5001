# Upper Second Class (2:1) Deliverable (Implemented)

This document tracks progress toward the **Upper Second Class (2:1) (60–69)** grade band.

## What’s Improved Since 2:2

**Efficient allocation algorithm**
- Added `MultiSourceNearestAvailableUnitPolicy`, which computes a shortest path from a virtual source connected to all eligible units, selecting the nearest unit in **one** routing run per incident (instead of routing once per unit).

**Real-time update handling (route changes)**
- When an edge is removed or closed and it appears in an active assignment route, the assignment is cancelled and the incident is re-queued so it can be reassigned in the same simulation step.

**Evaluation hooks**
- Dispatch decisions are now recorded in metrics and exported to CSV for later analysis.

## How To Run

- Tests: `mvn "-Dperds.surefire.forkCount=0" test`
- Demo: `mvn -q -DskipTests package && java -jar target/perds-0.1.0-SNAPSHOT.jar demo`
- Scenarios: see `docs/evaluation.md`

## Complexity Notes (High Level)

Let:
- `V` nodes, `E` edges, `U` eligible units, `I` dispatchable incidents in a compute cycle.

**Previous nearest-available policy (per-incident)**
- Worst-case: `O(U * (V + E) log V)` (route computed for each unit candidate).

**Multi-source nearest-available policy (per-incident)**
- Worst-case: `O((V + E + U) log V)` (one Dijkstra-style run with `U` zero-cost virtual edges).

**Route invalidation on edge closure**
- On an edge closure/removal: `O(A * L)` to scan `A` active assignments with average route length `L` (only triggered on relevant graph updates).

## Remaining 2:1 Work

See `docs/todo-21.md`.
