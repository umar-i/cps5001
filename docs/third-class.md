# Third Class Deliverable (Implemented)

This document summarises what is implemented for the **Third Class (40–49)** grade band, and how to run it.

## What Works

**Emergency network representation**
- Dynamic, directed graph using adjacency maps: `AdjacencyMapGraph` implements `GraphReadView` + `GraphWriteOps`.
- Nodes and edges can be added/removed; edge weights and status can be updated.

**Route optimisation**
- Shortest path routing via `DijkstraRouter`, using a custom indexed binary heap priority queue.
- Pluggable cost model via `EdgeCostFunction`; provided cost functions:
  - travel time in seconds (treats `CLOSED` edges as unreachable)
  - distance in km (treats `CLOSED` edges as unreachable)

**Simple allocation (nearest-available)**
- `NearestAvailableUnitPolicy` selects the available unit matching the incident’s required type with the lowest ETA.
- `DefaultDispatchEngine` prioritises incidents (severity, then oldest) and prevents double-assigning the same unit in a single compute cycle.

**End-to-end simulation cycle**
- `PerdsController` accepts `SystemCommand`s, computes dispatch commands, and applies `AssignUnitCommand` to system state:
  - creates an `Assignment`
  - sets the unit to `EN_ROUTE` and attaches the incident ID
  - sets the incident status to `DISPATCHED`
- Resolving an incident releases the assigned unit (set to `AVAILABLE` and clears incident ID).

## How To Run

Run tests:
- `mvn test`

Run demo:
- `mvn -q -DskipTests package`
- `java -jar target/perds-0.1.0-SNAPSHOT.jar demo`

The demo constructs a tiny 3-node network, registers two ambulance units, reports an incident, and prints the selected assignment/route.

## Diagrams

- Class diagram: `docs/diagrams/third-class-class-diagram.mmd`
- Dispatch sequence: `docs/diagrams/third-class-dispatch-sequence.mmd`

## Basic Justification (Data Structures + Algorithms)

**Graph representation**
- `AdjacencyMapGraph` uses `HashMap<NodeId, Node>` for nodes and `HashMap<NodeId, HashMap<NodeId, Edge>>` for outgoing edges.
- This supports fast updates and queries for typical simulation workloads (dynamic changes + repeated route queries).

**Priority queue**
- `BinaryHeapIndexedMinPriorityQueue` supports `decreaseKey`, enabling an efficient Dijkstra implementation without scanning.

**Shortest path**
- Dijkstra is appropriate for non-negative edge costs (travel time and distance are non-negative).

## Complexity (Big‑O, high level)

Let:
- `V` = number of nodes, `E` = number of edges,
- `I` = number of incidents considered in one dispatch cycle,
- `U` = number of units.

**Graph operations (average-case)**
- Add/remove node: `O(V + E)` worst-case when removing (must remove incident edges), otherwise `O(1)` average inserts.
- Get edge / outgoing edges: `O(1)` average for lookup; iterating outgoing edges is `O(outDegree(v))`.
- Update/put edge: `O(1)` average.

**Routing**
- Dijkstra (binary heap): `O((V + E) log V)` time, `O(V)` memory.

**Dispatch (Third Class policy)**
- Worst-case per cycle: `O(I * U * (V + E) log V)` because it routes per candidate unit.
- This is acceptable for a baseline band; later bands should introduce caching/targeted re-routing to reduce recomputation.

## Known Limitations (Deliberate, for later bands)
- No caching/targeted re-routing on graph changes.
- No dynamic reallocation/rerouting logic beyond releasing units on resolution.
- Prediction/pre-positioning are no-op placeholders.
- CSV scenario/network loaders are not implemented yet.

