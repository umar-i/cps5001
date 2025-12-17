# PERDS Technical Report

Student: [YOUR NAME] ([YOUR ID])  
Module: CPS5001  
Project: Predictive Emergency Response Dispatch System (PERDS)  
Language/stack: Java SE 21 + JUnit 5 (Maven)  
Word count (strict): 2400

## 1. Overview (aim, scope, and what this really is)

PERDS is a simulation of an emergency dispatch system over a national-style network. The brief asks for five things: a dynamic graph model, route optimisation, unit allocation and reallocation, predictive analysis for pre-positioning, and a report that proves I understand the data structures and algorithms (not just that I can paste a library).

What I built is not a production dispatch platform. It is a testable, repeatable simulator with a CLI. I focused on making the algorithmic pieces explicit: adjacency-map graph, Dijkstra/A*, an indexed binary heap, and a dispatch policy that can react to closures, congestion, and unit outages without recomputing the entire world every time.

Hard limits (no sugarcoating):

- Units do not continuously move along edges. A unit has a `currentNodeId`, and routes are recomputed from that node when the network changes.
- Incidents are resolved by scenario events, not by a detailed on-scene workflow.
- Prediction is lightweight forecasting over zones (no ML libraries, no hidden model training).

Despite those simplifications, the system meets the assessment requirements in a way that is measurable and inspectable: you can run scenarios, export CSV metrics, and compare variants.

Milestone coverage (for the marking rubric): Third Class delivered a dynamic adjacency-map graph, Dijkstra routing, and a nearest-available dispatch demo with unit tests. 2:2 added CSV loading, an event-driven scenario runner, basic reallocation when units become unavailable, a simple demand predictor, and metrics export. 2:1 introduced multi-source unit selection and route invalidation on closures. Lower First added rerouting on travel-time changes plus multi-hotspot pre-positioning. First (80-100) finished with an adaptive ensemble predictor, route indexing, and synthetic load evaluation with aggregate tables that are committed for easy inspection.

## 2. Data structures and algorithms (what I chose, how I implemented it, and why)

### 2.1 Core domain model (records + IDs)

I used small immutable domain records under `com.neca.perds.model` (e.g., `Incident`, `ResponseUnit`, `Assignment`) and wrapper ID records (`NodeId`, `IncidentId`, `UnitId`, `ZoneId`). This avoids mixing raw strings everywhere, and it makes bugs noisier (good). It also keeps snapshots simple: the dispatch engine receives a read-only `SystemSnapshot` and returns commands, instead of mutating state directly.

### 2.2 Graph representation (dynamic adjacency maps)

The network lives in `com.neca.perds.graph.AdjacencyMapGraph`. Internally it stores:

- `Map<NodeId, Node> nodes`
- `Map<NodeId, Map<NodeId, Edge>> outgoing`

This is an adjacency-list style structure using hash maps. I chose it because the brief demands dynamic updates (add/remove nodes, update edge weights, closures). With adjacency maps, edge updates are average-case `O(1)` lookups and replacements, and outgoing edge iteration is proportional to the local degree.

Edges carry `EdgeWeights(distanceKm, travelTime, resourceAvailability)` plus `EdgeStatus(OPEN/CLOSED)`. Closures are modelled as an edge that still exists, but routing cost functions return `+Infinity` for `CLOSED`, so the shortest path treats it as unreachable while keeping reopen operations cheap.

Brutal honesty: `removeNode` is expensive. It removes the node and its outgoing map, then scans every other outgoing map to remove incoming edges. That is worst-case `O(V + E)`. I accepted it because node removals are rare in my scenarios compared to routing and dispatch.

### 2.3 Routing (Dijkstra and A* with a custom indexed heap)

Routing is in `com.neca.perds.routing`. I implemented:

- `DijkstraRouter` for baseline shortest paths on non-negative edge costs.
- `AStarRouter` for optional optimisation when a good heuristic exists.

Routing uses an `EdgeCostFunction`, so the algorithm is decoupled from the meaning of weights. `CostFunctions.travelTimeSeconds()` and `CostFunctions.distanceKm()` return an edge cost, and return `+Infinity` for closed edges.

I did not use `java.util.PriorityQueue` for Dijkstra/A* because it has no efficient `decreaseKey`. Instead I implemented `BinaryHeapIndexedMinPriorityQueue` in `com.neca.perds.ds`:

- The heap stores indices, not node objects.
- `positions[index]` tells me where an index sits in the heap (or -1 if absent).
- `decreaseKey` becomes `O(log V)` by a `swim()` from the current heap position.

In both routers I map `NodeId -> int` once per query, then use arrays (`dist`, `prev`, `gScore`) to keep routing fast and predictable. Path reconstruction walks `prev[]` backwards from the goal.

Honest limitation: I rebuild the `NodeId` index every route query. For big graphs, a reusable indexing layer (or stable node indexing) would help, but I kept it simple and correct.

### 2.4 Dispatch (prioritisation, policies, and multi-source routing trick)

Dispatch is split so the logic stays testable:

- `DefaultDispatchEngine` orchestrates: sort incidents, call a policy, produce `DispatchCommand`s.
- `IncidentPrioritizer` decides ordering. I used `SeverityThenOldestPrioritizer` (severity desc, then oldest).
- `DispatchPolicy` selects a unit for one incident.

I implemented two policies:

1) `NearestAvailableUnitPolicy` (baseline): for each eligible unit, run routing from the unit to the incident and pick the lowest cost route. This is simple but expensive.

2) `MultiSourceNearestAvailableUnitPolicy` (2:1 improvement): run routing once by injecting a virtual source node connected to every eligible unit-start node with zero-cost edges. This is implemented as `VirtualSourceGraphView`, which wraps a `GraphReadView` and adds one extra node plus virtual outgoing edges. After routing from the virtual source to the incident, the first real node in the path tells me which start node is closest. If multiple units sit on that node, I pick the smallest `UnitId` for deterministic ties.

That multi-source trick is the main performance jump in the dispatch layer because it avoids "route once per unit". It is also honest about its assumption: it finds the nearest start node, not a magically optimal multi-objective solution.

Each assignment stores a `DispatchRationale` with a score and components (`travelTimeSeconds`, `distanceKm`, `severityLevel`). This is not just for show: those components are exported to CSV so decisions can be audited.

### 2.5 Dynamic updates and reallocation (event-driven controller)

Real-time behaviour is simulated with an event queue:

- `SimulationEngine` runs `TimedEvent`s from a `PriorityQueue` ordered by `Instant`.
- Each event contains a `SystemCommand` (sealed interface): report/resolve incident, add/remove node, update edge, set unit status, move unit, preposition.
- `PerdsController` applies the command, then runs dispatch, applies dispatch commands, and records metrics.

Reallocation is blunt but correct: if a unit becomes `UNAVAILABLE` while assigned, the controller cancels the assignment, returns the incident to `QUEUED`, and clears the unit. That allows reassignment in the next dispatch cycle.

Dynamic route changes are handled in two steps. First, detect whether an edge update affects any active route. Second, reroute or cancel:

- If the edge is part of an active route, try to recompute a route from the unit's current node to the incident using travel time cost.
- If a route exists, apply `REROUTE_UNIT`. If not, apply `CANCEL_ASSIGNMENT` and re-queue.

The "First Class" optimisation here is `AssignmentRouteIndex`: instead of scanning all routes on every edge update, I maintain a map from `(from,to)` edges to incident IDs whose current route uses that edge. Then an edge update gives me a small affected set `K` and I reroute only those.

### 2.6 Prediction and pre-positioning (lightweight, measurable, not magic)

Prediction is implemented through `DemandPredictor` and `PrepositioningStrategy`. A predictor observes incidents and forecasts expected counts per zone over a horizon. A strategy consumes a forecast and proposes unit moves.

Predictors:

- `ExponentialSmoothingDemandPredictor`: maintains a decaying score per zone using alpha in (0,1]. It reacts smoothly, but it can lag when hotspots shift suddenly.
- `SlidingWindowDemandPredictor`: stores recent incident timestamps per zone for a fixed window (default 1 hour) and scales counts into the forecast horizon. It reacts faster, but it is noisier.
- `AdaptiveEnsembleDemandPredictor`: combines multiple predictors and updates weights online based on observed forecast error. It is not ML; it is a transparent weighting scheme with a learning rate and normalisation.

Pre-positioning strategies:

- `GreedyHotspotPrepositioningStrategy`: choose the highest-demand zone and move up to `maxMoves` available units there.
- `MultiHotspotPrepositioningStrategy`: pick up to `maxZones` zones, allocate a limited number of moves proportionally, and choose nearest units per move. It reuses the same multi-source routing trick (virtual source) to pick a nearest unit efficiently.

Honest limitation: repositioning "moves" a unit by updating its node instantly. That is not physically realistic travel, but it makes the impact measurable: are units closer to future incidents on average?

### 2.7 Metrics and export (evidence, not vibes)

`InMemoryMetricsCollector` records dispatch compute times, decisions, and applied commands. `CsvMetricsExporter` writes:

- `dispatch_computations.csv`
- `dispatch_decisions.csv`
- `dispatch_commands_applied.csv`

This is the backbone for evaluation. Without it, I would be guessing about performance and fairness.

## 3. Complexity analysis (Big-O for major operations)

Let `V` nodes, `E` edges, `U` units, `I` incidents per compute cycle, `A` active assignments, `L` average route length, `K` affected assignments on an edge update, `Z` zones, `M` predictor models, `N` timestamps stored per zone.

- Graph updates: `addNode`, `putEdge`, `removeEdge`, `updateEdge` are average `O(1)`; `removeNode` is worst-case `O(V + E)`.
- Routing (Dijkstra/A* with binary heap): `O((V + E) log V)` time, `O(V)` memory.
- Baseline dispatch policy: `O(U * (V + E) log V)` per incident (route per unit).
- Multi-source policy: `O((V + E + S) log V)` per incident, where `S` is the number of distinct unit start nodes.
- Edge update impact detection: with indexing, lookup is `O(1)` and iterating affected incidents is `O(K)`. Without indexing it would be closer to `O(A * L)` scanning all routes.
- Reroute on edge update: `O(K * (V + E) log V)` (one route query per affected assignment).
- Sliding window predictor: observe is amortised `O(1)`; forecast is `O(Z)` (prune and scale).
- Exponential smoothing predictor: observe is `O(Z)` (decays all zones); forecast is `O(Z)`.
- Adaptive ensemble: forecast combine is `O(M * Z)`; weight update is `O(M * Z log N)` using binary search over sorted timestamps.
- Metrics export: writing CSV is `O(R)` where `R` is number of recorded rows (decisions + commands + computations).

## 4. Design diagrams (what exists, and how to turn it into images)

Diagrams are stored as Mermaid source in `docs/diagrams/` (text-first so they are version-controlled). I did not embed PNG screenshots directly in this Markdown report. If your submission format requires images, use these placeholders and export steps.

Figure placeholders:

1. Class diagram: `docs/diagrams/third-class-class-diagram.mmd`  
2. Dispatch sequence: `docs/diagrams/third-class-dispatch-sequence.mmd`  
3. Route invalidation on closure: `docs/diagrams/upper-second-route-invalidation-sequence.mmd`  
4. Reroute on congestion: `docs/diagrams/lower-first-reroute-sequence.mmd`  
5. Pre-positioning sequence: `docs/diagrams/lower-first-prepositioning-sequence.mmd`  
6. Synthetic evaluation sequence: `docs/diagrams/first-class-evaluation-sequence.mmd`

Export instructions (quick and realistic):

- Open a `.mmd` file, copy its contents into https://mermaid.live, then export as PNG/SVG.
- Paste the exported image into a Word/PDF report at the matching figure placeholder, and keep the caption.

## 5. Empirical evaluation (what I measured, what improved, and what did not)

I evaluated using the built-in synthetic runner (`Main evaluate`). It generates repeatable load: incident spikes, a mid-run hotspot shift, random congestion events (travel time changes), and a small number of unit outages. Outputs are committed under `docs/results/`.

Command used:

`java -jar target/perds-0.1.0-SNAPSHOT.jar evaluate data/scenarios/grid-4x4-nodes.csv data/scenarios/grid-4x4-edges.csv data/out/eval-report 10 1`

Aggregate results (10 runs, seed 1):

| variant | etaAvgSecondsMean | etaAvgSecondsP95Runs | computeAvgMicrosMean | cancelCommandsMean | rerouteCommandsMean |
|---|---:|---:|---:|---:|---:|
| no_preposition | 23.44 | 27.00 | 93.38 | 0.0 | 17.6 |
| sliding_preposition | 21.81 | 24.43 | 62.66 | 0.0 | 16.3 |
| adaptive_preposition | 21.66 | 24.43 | 55.84 | 0.0 | 16.0 |

What this actually says (not marketing):

- Pre-positioning helps average ETA in this scenario. The improvement is not huge, but it is consistent across runs.
- The adaptive ensemble is slightly better than sliding-window here, but not dramatically. It is not a miracle model; it just avoids sticking to the weaker predictor when it keeps being wrong.
- Compute time is lower in the pre-position variants in these runs because the dispatch behaviour changes (different mix of routes, different reroute counts). This number is machine-dependent, so I trust ETA trends more than microsecond claims.
- Cancellations are zero in this specific setup because the synthetic generator mostly creates congestion (travel time updates) rather than full closures, and rerouting succeeded. If closures are injected, cancellations do appear (see scenario-based tests and CSV scenarios).

What I did not do: I did not produce fancy plots inside the repo. If you need graphs, use `docs/results/evaluation_summary.csv` in Excel and plot `etaAvgSeconds` by variant, plus a boxplot over runs. It is 5 minutes of work and more honest than me drawing a pretend chart.

## 6. Ethical considerations (fairness, transparency, reliability)

Fairness: "nearest first" and hotspot pre-positioning can systematically disadvantage low-demand regions. My system does not enforce fairness constraints (coverage minimums, zone penalties). That is a gap. What I did instead is make decisions auditable: `dispatch_decisions.csv` includes per-decision components, so you can slice results by zone and check whether some areas are consistently worse after pre-positioning.

Transparency: decisions are not hidden inside a black box. The rationale components are exported, and the prediction models are simple enough to explain. The ensemble update is just weighted error decay, not opaque training.

Reliability: in real emergency dispatch, failure modes matter more than average-case performance. I handle route changes and unit outages by rerouting or cancelling and re-queuing. That improves robustness, but it is still a simplified world: no continuous movement, no partial edge traversal, and no concurrency issues. A real system would need stronger guarantees, monitoring, and fallback policies (including manual override).

Bias and feedback loops: predicting demand from historical data can encode reporting bias and create feedback loops (serve hotspots more, collect more data there, then justify serving them even more). My evaluation is synthetic, so the bias is controlled, but the risk is real and should be stated explicitly.

## 7. Version control and professional practice (evidence, not claims)

The repo uses feature branches, PRs, CI, and milestone tags. Concretely: there is a GitHub Actions workflow running `mvn test` on pull requests; the deliverable milestones are tagged (`v0.3.0-21`, `v0.4.0-lower-first`, `v1.0.0-first`); and the report work was done through incremental commits and a squash-merged PR, not a single dump.

## 8. Conclusion (what is strong, what is weak, and what I would fix next)

Strengths: the core algorithms are explicit, testable, and match the brief. The dispatch layer improved from per-unit routing to multi-source routing. Dynamic updates are handled locally (reroute only affected assignments). Evaluation outputs exist and are reproducible.

Weaknesses: realism is limited (no continuous movement), fairness is measured but not enforced, and prediction is simple by design. If I had more time, I would add a movement model, introduce coverage constraints, and expand evaluation to larger graphs and closure-heavy scenarios. That is the honest path to a more credible system.
