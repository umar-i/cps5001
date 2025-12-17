# PERDS Technical Report (Markdown Submission)

Module: CPS5001  
Project: Predictive Emergency Response Dispatch System (PERDS)  
Language/stack: Java SE 21 + JUnit 5 (Maven)

This report is written in Markdown because generating a PDF inside the coursework tooling is not guaranteed. Diagrams are stored as Mermaid source in `docs/diagrams/` so they stay version-controlled.

## Table of Contents

1. [Overview](#1-overview)  
2. [Modules / Functionality](#2-modules--functionality)  
3. [Recommendations](#3-recommendations)  
4. [References](#4-references)  
5. [Appendices](#5-appendices)

## Table of Figures

- Figure 1: Third Class class diagram (`docs/diagrams/third-class-class-diagram.mmd`)
- Figure 2: Dispatch compute/apply sequence (`docs/diagrams/third-class-dispatch-sequence.mmd`)
- Figure 3: Route invalidation on closure (`docs/diagrams/upper-second-route-invalidation-sequence.mmd`)
- Figure 4: Reroute on edge update (`docs/diagrams/lower-first-reroute-sequence.mmd`)
- Figure 5: Pre-positioning sequence (`docs/diagrams/lower-first-prepositioning-sequence.mmd`)
- Figure 6: Synthetic evaluation sequence (`docs/diagrams/first-class-evaluation-sequence.mmd`)

## Index of Tables

- Table 1: Requirement coverage checklist
- Table 2: Complexity summary (high-level)
- Table 3: Synthetic evaluation aggregate metrics

## 1 Overview

### Aim and objectives

The goal was to build a dispatch simulation that *actually uses* data structures and algorithms we learned: a dynamic weighted graph, shortest-path routing, and a dispatch policy that can react to change (new incidents, road closures, unit outages).

I also wanted it to be testable and repeatable from the command line, because otherwise it’s too easy to “demo once” and then everything breaks the moment you change the scenario.

### Scope and limitations (what I did and did not model)

This is not a real emergency-services product. It’s a simulation, and some things are simplified on purpose:

- Units do not continuously move along edges; they are modelled as “located at a node” and re-routed using an updated shortest path.
- Incidents are resolved via scenario events rather than a full on-scene timeline model.
- Prediction is demand forecasting over zones in the graph, not a machine-learning system (that’s out of scope and not allowed anyway).

### Requirements achieved (summary)

Table 1 is the checklist I used to sanity-check the brief and make sure I didn’t “accidentally” ignore something important.

**Table 1: Requirement coverage checklist**

| Requirement (brief) | Where it is implemented | Status |
|---|---|---|
| Dynamic network representation (nodes/edges, weights, closures) | `com.neca.perds.graph` (`AdjacencyMapGraph`, `Edge`, `EdgeWeights`, `EdgeStatus`) | ✅ |
| Route optimisation (shortest path) | `com.neca.perds.routing` (`DijkstraRouter`, `AStarRouter`) | ✅ |
| Allocation (severity + nearest eligible unit) | `com.neca.perds.dispatch` (`DefaultDispatchEngine`, policies/prioritizer) | ✅ |
| Reallocation (unit unavailable / priorities change) | `com.neca.perds.app.PerdsController` + `SystemCommand` handling | ✅ |
| Prediction + pre-positioning | `com.neca.perds.prediction` + `SystemCommand.PrepositionUnitsCommand` | ✅ |
| Efficient dynamic updates (avoid full recomputation) | `com.neca.perds.app.AssignmentRouteIndex` + targeted reroute/cancel | ✅ |
| Empirical evaluation + outputs | `com.neca.perds.metrics` + `com.neca.perds.cli.Main evaluate` | ✅ |
| Ethical considerations | `docs/ethics.md` (summarised in this report) | ✅ |
| Version control evidence | Feature branches, PR template, CI (`.github/workflows/ci.yml`), tags | ✅ |

### Report structure

Section 2 goes through the system by modules (graph, routing, dispatch, simulation, prediction, metrics). I kept the “why” close to the code, so you can cross-check claims quickly.

## 2 Modules / Functionality

This section explains the main parts of the implementation and the reasoning behind the data structure / algorithm choices. When something has a clear complexity story, I include it here instead of dumping a random Big‑O list at the end.

### 2.1 Graph / network representation (dynamic updates)

I modelled the “country” as a directed weighted graph. The core class is `com.neca.perds.graph.AdjacencyMapGraph`.

I went for an adjacency-map structure because it matches what the brief keeps pushing: the network changes at runtime (closures, congestion, adding/removing nodes), and I don’t want updates to be expensive. An adjacency matrix would make some lookups easy, but it would also waste memory on non-edges and make “remove node + all incident edges” feel clunky.

Concrete structure (this is literally what the class stores):

- `Map<NodeId, Node> nodes`
- `Map<NodeId, Map<NodeId, Edge>> outgoing`

So when I need an edge `(from -> to)`, it’s effectively a couple of `HashMap` lookups. When I need all outgoing edges from a node, I just iterate the inner map values.

Dynamic update support is built-in:

- `addNode(Node)` and `putEdge(Edge)` are average `O(1)` inserts.
- `updateEdge(from, to, weights, status)` replaces the existing edge and throws if it doesn’t exist (I’d rather fail loudly than silently invent new roads).
- `removeEdge(from, to)` is average `O(1)`.
- `removeNode(id)` is the “expensive” one: it removes the node, its outgoing map, and then scans all other outgoing maps to remove incoming edges. That’s basically `O(V + E)` worst-case. I accepted that because node removals are rare compared to routing/dispatch.

Every mutation increments a `version` counter (`GraphReadView.version()`), and every `Route` records the version it was computed against. I didn’t build a full cache, but that version stamp was still useful while testing (it makes it obvious when I’m looking at a stale route).

Weights and closures:

- `EdgeWeights` holds `distanceKm`, `travelTime` (a `Duration`), and a `resourceAvailability` factor in `[0,1]`.
- `EdgeStatus` is `OPEN` / `CLOSED`.
- Routing cost functions treat `CLOSED` edges as unreachable by returning `+Infinity` (see `com.neca.perds.routing.CostFunctions`).

### 2.2 Routing (Dijkstra + A*)

Routing lives in `com.neca.perds.routing`. I implemented two shortest-path routers:

- `DijkstraRouter` (baseline, used in most places)
- `AStarRouter` (same skeleton but adds a heuristic)

I didn’t pick something exotic here. Dijkstra is predictable, and it fits the constraints: edge costs are non-negative (travel time / distance). Also, because the network updates at runtime, I didn’t want an algorithm that needs heavy pre-processing.

**Cost model**

Rather than baking “travel time” into the routing code, I used `EdgeCostFunction` so the router stays generic. Two cost functions are provided in `CostFunctions`:

- `travelTimeSeconds()` -> `edge.weights().travelTime().toSeconds()` for `OPEN` edges, otherwise `+Infinity`
- `distanceKm()` -> `edge.weights().distanceKm()` for `OPEN` edges, otherwise `+Infinity`

That `+Infinity` trick is how closures work during routing: closed edges still exist in the graph (so you can reopen them), but they become unreachable for shortest-path.

**Why a custom priority queue**

This is the part where I stopped trusting the standard library. Java’s `PriorityQueue` doesn’t have a clean `decreaseKey`, which is a big deal for Dijkstra/A* if you want the normal complexity.

So I wrote `BinaryHeapIndexedMinPriorityQueue` in `com.neca.perds.ds`:

- It stores heap positions in an `int[] positions`, so “where is node X in the heap?” is `O(1)`.
- `decreaseKey` becomes `O(log n)` by doing a `swim()` from the current heap position.

It’s not fancy, but it’s explicit and testable (there’s a dedicated unit test class for it).

**Implementation shape (Dijkstra)**

`DijkstraRouter` builds a stable index for nodes (`Map<NodeId, Integer> indexByNodeId`) and then uses arrays:

- `dist[]` for current best-known distance/cost
- `prev[]` for path reconstruction

That lets me avoid creating a ton of wrapper objects during routing. It also makes the complexity story straightforward:

- Time: `O((V + E) log V)` with the binary heap.
- Space: `O(V)` for the arrays + heap.

**A* (optional optimisation)**

`AStarRouter` is the same idea, except the priority in the open set is `fScore = gScore + heuristic(start -> goal)`. The heuristic interface is `Heuristic.estimate(graph, from, goal)`.

In this coursework, most scenarios don’t need A* because there aren’t coordinates everywhere (nodes can have no `GeoPoint`). But I kept it because it’s a clean extension point: if you do have coordinates, `EuclideanHeuristic` is ready to go.

### 2.3 Dispatch (prioritisation + unit selection)

Dispatch is split into two parts on purpose:

- `DefaultDispatchEngine` decides *which incidents to consider first* and turns decisions into `DispatchCommand`s.
- A `DispatchPolicy` answers a narrower question: “for this one incident, which unit should I pick?”

That separation saved me from mixing orchestration logic with routing logic, and it made testing easier (I can test a policy in isolation with a small snapshot).

**Incident prioritisation**

I used `SeverityThenOldestPrioritizer`. It does exactly what it says: higher severity first, then earlier `reportedAt`. It’s simple, but it hits the brief’s “severity + time” requirement and keeps behaviour deterministic.

**Avoiding double-assigning the same unit**

One subtle problem: during one dispatch compute cycle, you can accidentally assign the same unit to multiple incidents if you always look at the original snapshot.

`DefaultDispatchEngine.compute()` keeps “working” copies:

- `workingUnits` (a map keyed by `UnitId`)
- `workingAssignments` (a list)

After it picks a unit for an incident, it marks that unit as `EN_ROUTE` in `workingUnits` before moving to the next incident. So later incidents won’t see that unit as available anymore.

**Unit selection policy**

There are two policies in `com.neca.perds.dispatch`:

- `NearestAvailableUnitPolicy` (baseline): loops every eligible unit and runs routing for each one.
- `MultiSourceNearestAvailableUnitPolicy` (improved): runs routing once by using a “virtual source”.

The multi-source one is the interesting part. The trick is `VirtualSourceGraphView`: it wraps the real graph and injects one extra node (`__VIRTUAL_SOURCE__`) with zero-cost edges to every node that currently has at least one eligible available unit.

Then the policy runs one shortest-path query from that virtual node to the incident location. The first real node in the resulting path tells you which unit-start node is closest. If multiple units sit on that same start node, I just pick the lexicographically smallest `UnitId` as a deterministic tie-break.

**Scoring / rationale**

I kept the scoring transparent rather than “clever”. Each assignment records a `DispatchRationale` with:

- a numeric score (right now: `-route.totalCost()` so “shorter route” = higher score)
- components (`travelTimeSeconds`, `distanceKm`, `severityLevel`)

Those components get exported to CSV (`dispatch_decisions.csv`) so I can actually explain decisions in the report instead of hand-waving.

### 2.4 Simulation and dynamic updates (events, reallocation, rerouting)

The “real-time” part is simulated with an event queue. I didn’t want threads here (it would make debugging and marking harder), so I went for a deterministic, event-driven loop:

- `SimulationEngine` stores `TimedEvent`s in a `PriorityQueue` ordered by `Instant`.
- A `TimedEvent` wraps a `SystemCommand` (sealed interface), so every state mutation is explicit and typed.
- `PerdsController` implements `SystemCommandExecutor` and is basically the orchestrator: apply the command, handle any targeted updates, run dispatch, apply dispatch commands, record metrics.

That last part (controller orchestration) is what makes the system feel “alive” even though it’s not truly concurrent.

**Reallocation when units become unavailable**

The rule I used is simple: if a unit is assigned to an incident and I change its status to something that can’t continue the assignment, I cancel the assignment and re-queue the incident.

You can see this in `PerdsController.setUnitStatus(...)`:

- It calls `cancelAssignment(...)` if the unit had an assigned incident and the new status is not compatible (`EN_ROUTE` or `ON_SCENE` are treated as compatible; everything else is not).
- `cancelAssignment(...)` removes the assignment, sets the incident back to `QUEUED`, and clears the unit’s `assignedIncidentId` (and usually makes it `AVAILABLE` again).

So the next dispatch cycle can pick a different unit.

**Route changes (closures + congestion)**

The graph supports two “bad things happen” cases:

- `RemoveEdge` (road closure)
- `UpdateEdge` (congestion -> travel time changes, or closure via status)

When an edge changes, I don’t want to scan every assignment route on every update. That gets ugly fast. So for the First-Class work I added `AssignmentRouteIndex`:

- On every assignment / reroute, I index the unique edges in that `Route`.
- On cancellation or resolution, I remove the incident from the index.
- When an edge changes, I can ask: “which incidents currently have a route that uses `(from -> to)`?”

That’s exactly what `PerdsController.rerouteOrCancelAssignmentsUsingEdge(...)` does. For each affected assignment, the controller tries to recompute a route from the unit’s *current node* to the incident:

- If a new route exists, it applies `DispatchCommand.RerouteUnitCommand`.
- If there is no route (unreachable), it applies `DispatchCommand.CancelAssignmentCommand`, which queues the incident again for reassignment.

It’s not perfect realism (the unit’s “current node” is coarse), but it demonstrates the key requirement: handle dynamic updates without rebuilding the whole world.

### 2.5 Prediction and pre-positioning

Prediction is deliberately lightweight (no ML libraries, and honestly I didn’t want a black box anyway). The point is to show how forecast information can *change dispatch behaviour* via proactive moves.

The prediction API is small:

- `DemandPredictor.observe(Incident)` updates history.
- `DemandPredictor.forecast(at, horizon)` returns a `DemandForecast` with expected incident counts per zone.

Zones are handled through `ZoneAssigner`. By default I assign `zone = nodeId` (so every node is its own zone), which keeps the scenarios simple and avoids having to invent a geographic clustering algorithm.

**Predictors I implemented**

- `ExponentialSmoothingDemandPredictor`: a decaying score per zone. Every observation decays existing scores by `(1 - alpha)` and adds `alpha` to the incident’s zone. It’s simple and reacts quickly, but it can also be “too smooth” if the hotspot shifts suddenly.
- `SlidingWindowDemandPredictor`: keeps a queue of incident timestamps per zone for a fixed window (default 1 hour). Forecasting is basically “count incidents in the last window, then scale to the requested horizon”. This reacts to sudden changes better, but it’s noisier.
- `AdaptiveEnsembleDemandPredictor` (First-Class step): blends multiple predictors and updates model weights online based on forecast error.

The ensemble part is the most “interesting” algorithmically. I store pending forecasts (at time `t`, horizon `h`) and when `t + h` has passed, I compare what each model predicted vs what actually happened in that time window. Then I update weights with an exponential penalty (`newWeight = oldWeight * exp(-learningRate * error)`) and normalise.

It’s not magic. It just means if one model keeps being wrong, it slowly loses influence, and the other model takes over.

**Pre-positioning strategies**

Pre-positioning is separate from prediction because I wanted to swap strategies without rewriting predictors.

- `GreedyHotspotPrepositioningStrategy` (2:2 baseline): pick the highest-demand zone and move up to `maxMoves` available units there.
- `MultiHotspotPrepositioningStrategy` (Lower First+): look at the top `maxZones` zones, allocate a limited number of moves proportionally to forecast demand, and for each move pick the nearest available unit.

The multi-hotspot strategy reuses the same multi-source routing trick as dispatch (`VirtualSourceGraphView`). That way “choose nearest unit to target” is one routing call, not one call per unit.

Finally, `PerdsController` applies the move plan when it receives `SystemCommand.PrepositionUnitsCommand`. In this simulation, moving a unit just updates its `currentNodeId`. That’s not realistic travel, but it makes the effect of “being in a better place before the next incident” measurable in a repeatable way.

### 2.6 Metrics + evaluation (export + aggregate)

I treated metrics as a first-class feature because otherwise the “evaluation” section becomes vibes.

The metrics pipeline is:

1. `PerdsController` records:
   - dispatch computation time (per event step)
   - each dispatch decision (assignment + rationale components)
   - each applied dispatch command (assign / reroute / cancel)
2. `InMemoryMetricsCollector` stores those records in lists.
3. `CsvMetricsExporter` exports them to CSV for analysis.

The CSVs are intentionally boring (plain rows, no nested JSON):

- `dispatch_computations.csv`
- `dispatch_decisions.csv`
- `dispatch_commands_applied.csv`

That is enough to answer most “did the system behave well?” questions:

- Are we cancelling a lot when roads close?
- Does pre-positioning reduce ETA, or does it just shuffle units around?
- Does dispatch compute time blow up when incidents spike?

For quick feedback, I added `ScenarioSummary` which computes a small set of aggregated metrics (avg/p95 compute time, avg/p95 ETA, avg/p95 wait time, plus command counts). The CLI prints this summary after running a scenario, and the First-Class `evaluate` command also writes an aggregate Markdown table that can be pasted straight into the report.

### 2.7 Testing + reliability

I used JUnit 5 and tried to keep tests small and “explainable”. If a test fails, I want to know *what broke* without reading 500 lines of setup.

Main areas covered:

- Data structures: `BinaryHeapIndexedMinPriorityQueueTest`
- Routing: `DijkstraRouterTest`, `AStarRouterTest`
- Dispatch policies: `NearestAvailableUnitPolicyTest`, `MultiSourceNearestAvailableUnitPolicyTest`
- Controller behaviour under changes:
  - reallocation when a unit becomes unavailable (`PerdsControllerReallocationTest`)
  - route invalidation / cancellation on edge closure (`PerdsControllerRouteInvalidationTest`)
  - reroute on congestion (travel time updates) (`PerdsControllerRerouteOnCongestionTest`)
  - route index correctness (`AssignmentRouteIndexTest`)
- I/O and evaluation plumbing: CSV loaders/exporter and summary stats (`CsvGraphLoaderTest`, `CsvScenarioLoaderTest`, `CsvMetricsExporterTest`, `ScenarioSummaryTest`)

For the “First Class” evaluation code, I also tested the synthetic load generator so it stays deterministic for a fixed seed (`SyntheticLoadScenarioGeneratorTest`).

Run tests:

- `mvn test`
- If your environment blocks forked test JVMs: `mvn "-Dperds.surefire.forkCount=0" test`

### 2.8 Version control and development process

I treated version control as part of the deliverable, not an afterthought. The repo includes:

- feature branching (topic branches instead of committing straight to `main`)
- a PR template (`.github/PULL_REQUEST_TEMPLATE.md`) and issue templates
- a GitHub Actions CI workflow (`.github/workflows/ci.yml`) that runs `mvn test` on PRs and on pushes to `main`
- milestone tags for the grade-band checkpoints (`v0.3.0-21`, `v0.4.0-lower-first`, `v1.0.0-first`)

That matters because it shows incremental development and makes it easier to review changes. It also helped me personally: when I broke routing during the multi-source work, I could bisect quickly instead of guessing.

### 2.9 Ethical considerations (fairness, transparency, robustness)

I’m aware this is “just a simulation”, but the same shortcuts and incentives show up in real dispatch systems too.

Fairness vs efficiency is the obvious tension. Nearest-first dispatch and hotspot pre-positioning usually improve the *average* ETA, but you can also end up pulling units out of low-demand areas until those areas effectively become “always second priority”. I didn’t implement hard fairness constraints (coverage minimums, penalties for stripping a zone, etc.), but I tried to make the trade-off visible rather than hiding it.

Two concrete things in the system help with that:

- Decisions are explainable: every assignment stores a `DispatchRationale` with components (travel time, distance, severity). Those components are exported in `dispatch_decisions.csv` so you can audit what happened instead of guessing.
- Evaluation outputs are comparable: the synthetic runner exports the same metrics across variants so you can compare `no_preposition` vs `*_preposition` and check whether faster ETAs come with instability (reroutes/cancels) or worse tails (p95).

Robustness is the other big one. A routing/dispatch system that only works when roads never change is basically useless. That’s why I built the reroute/cancel path and later optimised it with `AssignmentRouteIndex` rather than relying on “full recompute is probably fine”.

## 3 Recommendations

If I kept working on this after submission, I’d focus on a few things that are “boring” but would make the simulation closer to reality:

- **Continuous movement**: right now a unit is basically “at a node” until an event moves it. A better model would schedule movement along edges over time and then rerun dispatch at meaningful points (arrival, traffic update, new incident). That would also make rerouting feel less like teleporting.
- **Fairness constraints**: I can already measure outcomes by zone, but I don’t enforce anything. The next step would be adding minimum coverage rules (e.g., keep at least 1 unit per region) or adding a fairness penalty into `DispatchRationale` so hotspot chasing doesn’t become the only objective.
- **More realistic resource constraints**: `EdgeWeights.resourceAvailability` exists but I don’t use it in routing/dispatch yet. In a real system, you’d want to factor in things like road suitability, station capacity, and “don’t send the last ambulance out of a whole district”.
- **Route caching with invalidation**: the graph versioning and the targeted edge index are already there. It would be possible to cache common routes and invalidate only what’s affected on updates (carefully, because caching can also make behaviour stale if you’re not disciplined).
- **Lightweight plotting**: I kept visualisation to Markdown tables/CSVs because of the dependency constraints, but a small script (even just spreadsheet guidance) to plot ETA distributions per variant would make the evaluation section clearer.

## 4 References

I didn’t rely on external libraries for algorithms, but I did lean on standard sources for the ideas:

- CPS5001 Assessment 2 brief (project requirements; local copy in `requirements.txt`)
- Java SE 21 API documentation (records, collections, `Duration`, etc.)
- E. W. Dijkstra (1959). *A note on two problems in connexion with graphs.*
- P. E. Hart, N. J. Nilsson, B. Raphael (1968). *A Formal Basis for the Heuristic Determination of Minimum Cost Paths.*

## 5 Appendices

### Appendix A: How to run (commands)

Prereqs: Java 21+, Maven 3.9+.

Run unit tests:

- `mvn test`
- If your environment blocks forked test JVMs: `mvn "-Dperds.surefire.forkCount=0" test`

Build a runnable JAR:

- `mvn -q -DskipTests package`

Run the tiny demo:

- `java -jar target/perds-0.1.0-SNAPSHOT.jar demo`

Run a CSV scenario (and optionally export metrics):

- `java -jar target/perds-0.1.0-SNAPSHOT.jar scenario data/scenarios/mini-nodes.csv data/scenarios/mini-edges.csv data/scenarios/mini-events.csv data/out`

Run the synthetic evaluation:

- `java -jar target/perds-0.1.0-SNAPSHOT.jar evaluate data/scenarios/grid-4x4-nodes.csv data/scenarios/grid-4x4-edges.csv data/out/eval-report 10 1`

More scenario ideas (reallocation, edge closures, congestion spikes) are listed in `docs/evaluation.md`.

### Appendix B: Complexity summary (high-level)

**Table 2: Complexity summary (high-level)**

Notation used below:

- `V` nodes, `E` edges
- `U` response units, `I` incidents considered in a compute cycle
- `A` active assignments, `L` average route length (nodes)
- `K` assignments affected by an updated edge
- `Z` zones with history, `M` predictor models
- `N` incident timestamps stored for a zone (worst-case)

| Area | Operation | Time (typical) | Notes |
|---|---|---:|---|
| Graph (`AdjacencyMapGraph`) | `addNode`, `putEdge`, `updateEdge`, `removeEdge` | `O(1)` avg | HashMap operations |
| Graph (`AdjacencyMapGraph`) | `removeNode` | `O(V + E)` worst | removes incoming edges by scanning outgoing maps |
| Routing (`DijkstraRouter` / `AStarRouter`) | one shortest-path query | `O((V + E) log V)` | binary heap PQ; arrays for dist/prev |
| Dispatch (baseline) | per incident, `NearestAvailableUnitPolicy` | `O(U * (V + E) log V)` | routes once per eligible unit |
| Dispatch (improved) | per incident, `MultiSourceNearestAvailableUnitPolicy` | `O((V + E + S) log V)` | `S` = distinct unit start nodes (virtual edges) |
| Dynamic updates | edge update -> find affected assignments | `O(1)` + `O(K)` | `AssignmentRouteIndex` lookup + iterate affected IDs |
| Dynamic updates | edge update -> reroute affected assignments | `O(K * (V + E) log V)` | one reroute query per affected assignment |
| Sliding window predictor | `observe` | `O(1)` amortised | enqueue + prune old timestamps |
| Sliding window predictor | `forecast` | `O(Z)` | prune per zone + scale counts |
| Exp smoothing predictor | `observe` | `O(Z)` | decays all zone scores each incident |
| Ensemble predictor | `forecast` | `O(M * Z)` | combine model forecasts |
| Ensemble predictor | weight update (when forecasts mature) | `O(M * Z log N)` | binary-search incident timestamps per zone window |

### Appendix C: Synthetic evaluation outputs

**Table 3: Synthetic evaluation aggregate metrics**

I ran the built-in synthetic evaluation with 10 runs and seed 1:

- `java -jar target/perds-0.1.0-SNAPSHOT.jar evaluate data/scenarios/grid-4x4-nodes.csv data/scenarios/grid-4x4-edges.csv data/out/eval-report 10 1`

The committed copy of the outputs (so you can view them without rerunning) is in `docs/results/`:

- `docs/results/evaluation_aggregate.md`
- `docs/results/evaluation_aggregate.csv`
- `docs/results/evaluation_summary.csv`

The big thing I cared about here was whether pre-positioning actually improves response ETA under disruptions (hotspot shift + congestion + outages), not whether it makes the code “look advanced”.

| variant | runs | etaAvgSecondsMean | etaAvgSecondsP95Runs | waitAvgSecondsMean | waitAvgSecondsP95Runs | computeAvgMicrosMean | cancelCommandsMean | rerouteCommandsMean |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| adaptive_preposition | 10 | 21.66453140246082 | 24.431818181818183 | 307.7158466707499 | 467.5238095238095 | 55.836094674556215 | 0.0 | 16.0 |
| no_preposition | 10 | 23.4413043015635 | 27.0 | 307.7158466707499 | 467.5238095238095 | 93.38343195266273 | 0.0 | 17.6 |
| sliding_preposition | 10 | 21.807388545317966 | 24.431818181818183 | 307.7158466707499 | 467.5238095238095 | 62.658579881656806 | 0.0 | 16.3 |

Notes (so I don’t oversell it):

- The “wait” metrics are basically identical here because in this simulation incidents get dispatched immediately after they’re reported (unless something forces a cancel + requeue).
- Compute times are machine-dependent. I’m including them because the brief asks for performance evidence, but the ETA trends are what I trust more.
