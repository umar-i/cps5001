# PERDS - Predictive Emergency Response Dispatch System

Java SE 21+ coursework project that simulates an emergency-response dispatch network, focusing on implementing core **data structures and algorithms** (dynamic graph, routing, dispatch policies) without third-party algorithm libraries.

## Status

- Current milestone: **Third Class baseline implemented** (`docs/third-class.md`).
- Next milestone: **2:2 features** (dynamic update handling + early reallocation + CSV scenarios).

## What's Implemented (Baseline)

- Dynamic weighted graph: adjacency-map representation (`com.neca.perds.graph`)
- Routing: `DijkstraRouter` + custom indexed binary-heap PQ (`com.neca.perds.routing`, `com.neca.perds.ds`)
- Dispatch (simple): prioritise by severity/age, allocate nearest available matching unit type (`com.neca.perds.dispatch`)
- End-to-end cycle: commands -> dispatch decisions -> assignments applied (`com.neca.perds.app`, `com.neca.perds.sim`)
- CLI demo + JUnit 5 tests (`com.neca.perds.cli`, `src/test/java/...`)

## Quick Start

Prereqs: Java 21+, Maven 3.9+.

- Run tests: `mvn test`
- Package: `mvn -q -DskipTests package`
- Run demo: `java -jar target/perds-0.1.0-SNAPSHOT.jar demo`

If your environment blocks forked test JVMs: `mvn "-Dperds.surefire.forkCount=0" test`

## Repository Layout

- `src/main/java/com/neca/perds/` - application source
  - `graph/` dynamic graph model + mutations
  - `routing/` Dijkstra/A* routers, heuristics, cost functions
  - `dispatch/` dispatch engine, prioritisation, policies, rationale
  - `ds/` custom data structures used by algorithms (e.g., indexed heap)
  - `sim/` command model and simulation primitives
  - `metrics/` metrics capture/export interfaces (baseline in-memory collector)
  - `io/` CSV loaders/exporters (some stubs; see Roadmap)
  - `cli/` CLI entry point (`Main`)
- `src/test/java/...` - JUnit 5 tests
- `docs/` - report material, design notes, and diagrams
- `data/` - optional scenario inputs/outputs (recommended: `data/scenarios/`, `data/out/`)

## Documentation

- Baseline deliverable: `docs/third-class.md`
- Target architecture / APIs: `docs/architecture.md`
- Evaluation plan: `docs/evaluation.md`
- Ethics reflection notes: `docs/ethics.md`
- Diagrams: `docs/diagrams/README.md`

## Roadmap (Grade Bands)

- [x] Third Class (40-49): dynamic graph + Dijkstra routing + simple dispatch + CLI demo + tests
- [ ] 2:2 (50-59): CSV scenario/network loading; handle dynamic updates without full recomputation; basic reallocation/reroute commands; initial metrics exports
- [ ] 2:1 (60-69): stronger multi-criteria dispatch scoring; caching/invalidation strategy; broader simulation scenarios + empirical evaluation write-up
- [ ] First (70+): predictive demand + systematic pre-positioning; larger-scale experiments; visualisation (CSV/diagrams) and report polish

## Constraints (Assessment)

- Java SE 21+ only; standard library + JUnit (no external graph/algorithm frameworks; no AI/ML libraries).
- Implement and justify core data structures/algorithms explicitly (adjacency-list graph, priority queue/heap, Dijkstra/A*).

## Notes

This is a simulation/academic project and not a real emergency-services system.
