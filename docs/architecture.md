# PERDS Architecture (Blueprint)

This document defines the target architecture and public APIs for the PERDS coursework so the system can be implemented incrementally from **Third Class → 2:2 → 2:1 → First Class** without redesign.

## Scope (from `requirements.txt`)
- Represent a national emergency network as a dynamic, weighted graph (add/remove nodes; update edge weights).
- Allocate response units to incidents using multi‑criteria dispatch + route optimisation (e.g., Dijkstra/A*).
- Adapt to real-time updates (new incidents, unit availability, route changes) without full recomputation.
- Predict high-demand areas from historical/simulated data and pre-position units.
- Produce report material: complexity analysis, diagrams, empirical evaluation, ethical reflection.

## Constraints
- Java SE 21+ only; standard library + JUnit.
- No external graph/algorithm frameworks; no AI/ML libraries.
- Visualisation should be lightweight (e.g., CSV exports, Graphviz `.dot`, Mermaid diagrams).

## Design Principles
- **SOLID**: each class has one job; algorithms behind interfaces; orchestration separated from data structures.
- **Testability**: pure/near‑pure algorithms, deterministic simulation via seeded scenarios and simulated clock.
- **Adaptability**: graph versioning + cache invalidation; event-driven updates; reroute only affected units.

## Package Map (target)
- `com.neca.perds.model` - domain entities and IDs.
- `com.neca.perds.graph` - dynamic graph representation and mutations.
- `com.neca.perds.routing` - shortest-path routing algorithms + cost models.
- `com.neca.perds.dispatch` - incident prioritisation, unit selection, (re)assignment.
- `com.neca.perds.prediction` - demand forecasting + pre-positioning strategies.
- `com.neca.perds.sim` - event-driven simulation engine (optional real-time loop later).
- `com.neca.perds.metrics` - metrics capture + export for evaluation/visualisation.
- `com.neca.perds.io` - CSV loaders/exporters for scenarios and networks.
- `com.neca.perds.cli` - command-line entrypoints and command parsing.
- `com.neca.perds.ds` - custom data structures used by algorithms (e.g., indexed heap).

## Core Domain Model (minimal but extensible)
IDs:
- `NodeId`, `IncidentId`, `UnitId`, `DispatchCentreId`, `ZoneId` (records wrapping `String`)

Entities:
- `Node` (id, type, optional coordinates/metadata)
- `Edge` (from, to, weights, status)
- `ResponseUnit` (id, type, status, current node, assigned incident)
- `Incident` (id, location node, severity, required type(s), status, timestamps)
- `Assignment` (incident, unit, route, assignedAt)

## Graph API
Separate read/write views to keep algorithms decoupled from mutations:
- `GraphReadView`:
  - `Optional<Node> getNode(NodeId id)`
  - `Collection<NodeId> nodeIds()`
  - `Collection<Edge> outgoingEdges(NodeId from)`
  - `Optional<Edge> getEdge(NodeId from, NodeId to)`
  - `long version()`
- `GraphWriteOps`:
  - `long addNode(Node node)`
  - `long removeNode(NodeId id)`
  - `long putEdge(Edge edge)` (add or replace)
  - `long removeEdge(NodeId from, NodeId to)`
  - `long updateEdge(NodeId from, NodeId to, EdgeWeights weights, EdgeStatus status)`
- `AdjacencyMapGraph` implements both using adjacency maps for `O(1)` edge updates.

## Routing API
- `Router`:
  - `Optional<Route> findRoute(GraphReadView graph, NodeId start, NodeId goal, EdgeCostFunction cost)`
- `EdgeCostFunction`:
  - `double cost(Edge edge)` (non-negative)
- `Route`:
  - ordered node list + derived totals (distance, travel time, cost) + `graphVersionUsed`
- Implementations:
  - `DijkstraRouter` (baseline)
  - `AStarRouter` (optimised; requires `Heuristic`)
  - `Heuristic` + `EuclideanHeuristic` (if coordinates are present)

## Dispatch API
Dispatch is modelled as a **decision engine** that produces commands from a snapshot:
- `SystemSnapshot` (read-only view of current graph + units + incidents + assignments + time)
- `DispatchEngine`:
  - `List<DispatchCommand> compute(SystemSnapshot snapshot)`
- `DispatchCommand` (sealed):
  - `AssignUnitCommand(IncidentId, UnitId, Route, DispatchRationale)`
  - `RerouteUnitCommand(UnitId, Route, String reason)`
  - `CancelAssignmentCommand(IncidentId, String reason)`

Policy + scoring are separated to keep the engine small:
- `IncidentPrioritizer` (orders incidents by severity/age)
- `DispatchPolicy` (select best unit for an incident)
- `DispatchScorer` (multi-criteria scoring: severity, ETA, distance, unit type, fairness)
- `DispatchConfig` (weights and thresholds)

## Prediction + Pre-Positioning API
- `ZoneAssigner`: `ZoneId zoneFor(NodeId nodeId)`
- `DemandPredictor`:
  - `void observe(Incident incident)`
  - `DemandForecast forecast(Instant at, Duration horizon)`
- `PrepositioningStrategy`:
  - `RepositionPlan plan(SystemSnapshot snapshot, DemandForecast forecast)`
- `RepositionPlan` → `List<RepositionMove(UnitId, NodeId target, String reason)>`

## Simulation API (event-driven)
- `SystemCommand` (mutates system state in a controlled way):
  - examples: `ReportIncidentCommand`, `ResolveIncidentCommand`, `UpdateEdgeCommand`, `SetUnitStatusCommand`
- `TimedEvent(Instant time, SystemCommand command)`
- `SimulationEngine`:
  - owns an event priority queue; executes commands in time order; calls dispatch + prediction each step.

## Metrics + Export
- `MetricsCollector` records decisions and outcomes:
  - dispatch computation time, ETA vs actual, queue length, utilisation, fairness indicators
- `MetricsExporter`:
  - export aggregated metrics to CSV under `data/out/`

## Incremental Delivery Map (what gets built when)
- Third Class: `AdjacencyMapGraph`, core model, `DijkstraRouter`, simple dispatch (nearest available matching type), basic CLI.
- 2:2: dynamic graph updates + multiple incidents + basic reallocation; initial predictor stub + one pre-position action.
- 2:1: robust dispatch engine with real-time update handling + metrics; diagrams + complexity analysis.
- First/Exceptional: adaptive prediction + systematic pre-positioning + load simulation + statistical evaluation/visualisation.

