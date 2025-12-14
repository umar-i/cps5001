# Lower Second Class (2:2) Deliverable (Implemented)

This document summarises what is implemented for the **Lower Second Class (2:2) (50–59)** grade band, and how to run it.

## What Works

**Dynamic city/network model**
- Dynamic adjacency-map graph (`AdjacencyMapGraph`) with runtime mutations (add/remove nodes/edges; update edge weights/status).

**Concurrent incident management (multiple incidents over time)**
- Event-driven simulation via `SimulationEngine` + `TimedEvent`.
- System mutations as `SystemCommand` (report/resolve incidents; unit status changes; edge updates; prepositioning trigger).

**Basic reallocation**
- If a dispatched unit becomes incompatible/unavailable (e.g., set to `UNAVAILABLE`), the existing assignment is cancelled and the incident is returned to `QUEUED` so it can be reassigned in the next dispatch cycle.

**Partial predictive element + one pre-position action**
- `ExponentialSmoothingDemandPredictor` produces simple zone “demand scores” from observed incidents (default zone = node ID).
- `GreedyHotspotPrepositioningStrategy` proposes moves for idle units to the highest-demand zone.
- `SystemCommand.PrepositionUnitsCommand` applies that reposition plan (when scheduled in a scenario).

**Evaluation hooks**
- `InMemoryMetricsCollector` records dispatch computation timings and applied dispatch commands.
- `CsvMetricsExporter` writes metrics CSVs for later analysis/visualisation.

## How To Run

Run tests:
- `mvn "-Dperds.surefire.forkCount=0" test`

Run the built-in demo:
- `mvn -q -DskipTests package`
- `java -jar target/perds-0.1.0-SNAPSHOT.jar demo`

Run the sample CSV scenario (includes reallocation + preposition event):
- `mvn -q -DskipTests package`
- `java -jar target/perds-0.1.0-SNAPSHOT.jar scenario data/scenarios/mini-nodes.csv data/scenarios/mini-edges.csv data/scenarios/mini-events.csv data/out`

## Complexity (high level)

Let `V` be nodes, `E` edges, `I` incidents, `U` units.

- Graph updates: average `O(1)` insert/update; node removal `O(V + E)` worst-case (removes incident edges).
- Routing (Dijkstra / A* with binary heap): `O((V + E) log V)` time, `O(V)` memory.
- Baseline dispatch loop (nearest available): worst-case `O(I * U * (V + E) log V)` due to routing per candidate unit.
- Demand predictor update: `O(Z)` per observation where `Z` is the number of tracked zones (simple decay + increment).

## Known Limitations (deliberate, for 2:1+)
- No route-cache/invalidation strategy yet (dispatch still recomputes routes frequently).
- No continuous unit movement model (units “teleport” only when explicitly moved/repositioned).
- No fairness metrics/constraints beyond deterministic tie-breaking.

