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
- Routing cost functions treat `CLOSED` edges as unreachable by returning `+∞` (see `com.neca.perds.routing.CostFunctions`).

### 2.2 Routing (Dijkstra + A*)

Routing lives in `com.neca.perds.routing`. I implemented two shortest-path routers:

- `DijkstraRouter` (baseline, used in most places)
- `AStarRouter` (same skeleton but adds a heuristic)

I didn’t pick something exotic here. Dijkstra is predictable, and it fits the constraints: edge costs are non-negative (travel time / distance). Also, because the network updates at runtime, I didn’t want an algorithm that needs heavy pre-processing.

**Cost model**

Rather than baking “travel time” into the routing code, I used `EdgeCostFunction` so the router stays generic. Two cost functions are provided in `CostFunctions`:

- `travelTimeSeconds()` → `edge.weights().travelTime().toSeconds()` for `OPEN` edges, otherwise `+∞`
- `distanceKm()` → `edge.weights().distanceKm()` for `OPEN` edges, otherwise `+∞`

That `+∞` trick is how closures work during routing: closed edges still exist in the graph (so you can reopen them), but they become unreachable for shortest-path.

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

`AStarRouter` is the same idea, except the priority in the open set is `fScore = gScore + heuristic(start → goal)`. The heuristic interface is `Heuristic.estimate(graph, from, goal)`.

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

TODO

### 2.5 Prediction and pre-positioning

TODO

### 2.6 Metrics + evaluation (export + aggregate)

TODO

### 2.7 Testing + reliability

TODO

### 2.8 Version control and development process

TODO

## 3 Recommendations

TODO

## 4 References

TODO

## 5 Appendices

### Appendix A: How to run (commands)

TODO

### Appendix B: Complexity summary (high-level)

**Table 2: Complexity summary (high-level)**

TODO

### Appendix C: Synthetic evaluation outputs

**Table 3: Synthetic evaluation aggregate metrics**

TODO
