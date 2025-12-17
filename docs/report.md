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

TODO

### 2.2 Routing (Dijkstra + A*)

TODO

### 2.3 Dispatch (prioritisation + unit selection)

TODO

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

