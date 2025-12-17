# PERDS - Predictive Emergency Response Dispatch System

Java SE 21+ coursework project that simulates an emergency-response dispatch network, focusing on implementing core **data structures and algorithms** (dynamic graph, routing, dispatch policies) without third-party algorithm libraries.

## Status

- Current milestone: **First-Class (80-100) deliverable implemented** (`docs/first-class.md`).
- Next milestone: report polish + submission packaging.

## What's Implemented

- Dynamic weighted graph: adjacency-map representation (`com.neca.perds.graph`)
- Routing: `DijkstraRouter` + `AStarRouter` + custom indexed binary-heap PQ (`com.neca.perds.routing`, `com.neca.perds.ds`)
- Dispatch: prioritise by severity/age; allocate nearest eligible unit via routing (`com.neca.perds.dispatch`)
- End-to-end cycle: commands -> dispatch decisions -> assignments applied (`com.neca.perds.app`, `com.neca.perds.sim`)
- CSV loaders + scenario runner + metrics export (`com.neca.perds.io`, `com.neca.perds.metrics`, `com.neca.perds.cli`)
- Reallocation on unit unavailability and on edge closures; simple demand prediction + hotspot pre-position command (`com.neca.perds.app`, `com.neca.perds.prediction`)
- CLI demo + JUnit 5 tests (`com.neca.perds.cli`, `src/test/java/...`)

## Quick Start

Prereqs: Java 21+, Maven 3.9+.

- Run tests: `mvn test`
- Package: `mvn -q -DskipTests package`
- Run demo: `java -jar target/perds-0.1.0-SNAPSHOT.jar demo`
- Run sample scenario + export metrics: `java -jar target/perds-0.1.0-SNAPSHOT.jar scenario data/scenarios/mini-nodes.csv data/scenarios/mini-edges.csv data/scenarios/mini-events.csv data/out`

If your environment blocks forked test JVMs: `mvn "-Dperds.surefire.forkCount=0" test`

## Repository Layout

- `src/main/java/com/neca/perds/` - application source
  - `graph/` dynamic graph model + mutations
  - `routing/` Dijkstra/A* routers, heuristics, cost functions
  - `dispatch/` dispatch engine, prioritisation, policies, rationale
  - `ds/` custom data structures used by algorithms (e.g., indexed heap)
  - `sim/` command model and simulation primitives
  - `metrics/` metrics capture/export interfaces (baseline in-memory collector)
  - `io/` CSV loaders for networks and scenarios
  - `cli/` CLI entry point (`Main`)
- `src/test/java/...` - JUnit 5 tests
- `docs/` - report material, design notes, and diagrams
- `data/` - optional scenario inputs/outputs (recommended: `data/scenarios/`, `data/out/`)

## Documentation

- Submission report (Markdown): `docs/report.md`
- Baseline deliverable: `docs/third-class.md`
- 2:2 deliverable: `docs/lower-second.md`
- 2:1 deliverable: `docs/upper-second.md`
- Lower First deliverable: `docs/lower-first.md`
- First-Class deliverable: `docs/first-class.md`
- Target architecture / APIs: `docs/architecture.md`
- Evaluation plan: `docs/evaluation.md`
- Ethics reflection notes: `docs/ethics.md`
- Diagrams (rendered on GitHub): `docs/diagrams/README.md`

## Diagrams (Mermaid on GitHub)

GitHub renders Mermaid diagrams when they are inside fenced code blocks (` ```mermaid ... ``` `). The rendered, up-to-date diagrams live in `docs/diagrams/README.md`.

## Roadmap (Grade Bands)

- [x] Third Class (40-49): dynamic graph + Dijkstra routing + simple dispatch + CLI demo + tests
- [x] 2:2 (50-59): CSV scenario/network loading; basic reallocation; initial metrics exports; partial prediction + one pre-position action
- [x] 2:1 (60-69): efficient nearest-unit allocation; route invalidation on edge closure; expanded evaluation notes + diagrams
- [x] Lower First (70-79): predictive demand + multi-hotspot pre-positioning; rerouting on congestion; larger-scale scenario + summary metrics
- [x] First (80-100): adaptive prediction + synthetic load evaluation + aggregate visualisation outputs

## Constraints (Assessment)

- Java SE 21+ only; standard library + JUnit (no external graph/algorithm frameworks; no AI/ML libraries).
- Implement and justify core data structures/algorithms explicitly (adjacency-list graph, priority queue/heap, Dijkstra/A*).

## Notes

This is a simulation/academic project and not a real emergency-services system.
