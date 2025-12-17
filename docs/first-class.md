# First Class (80-100) Deliverable (Implemented)

This document summarises the **First Class (80-100)** enhancements added on top of the Lower First baseline.

## What Was Added

- **Adaptive prediction (learning from historical error)**: `AdaptiveEnsembleDemandPredictor` blends multiple predictors and updates model weights online using observed forecast error.
- **More efficient dynamic updates**: `PerdsController` now uses `AssignmentRouteIndex` to reroute/cancel only assignments whose stored routes include the updated edge (avoids scanning all active routes).
- **Simulated real-time load + statistical evaluation**: `Main evaluate` runs a synthetic, repeatable load test (hotspot shift + congestion + unit outages) and exports per-run metrics plus aggregated CSV/Markdown summaries.

## How To Run (Reproducible)

Build:
- `mvn -q -DskipTests package`

Run synthetic evaluation (writes outputs under the chosen `outDir`):
- `java -jar target/perds-0.1.0-SNAPSHOT.jar evaluate data/scenarios/grid-4x4-nodes.csv data/scenarios/grid-4x4-edges.csv data/out 5 1`

Outputs:
- `evaluation_summary.csv` (one row per run + variant)
- `evaluation_aggregate.csv` and `evaluation_aggregate.md` (aggregate stats across runs)
- `runs/<variant>/run-*/` (raw metrics CSVs per run for deeper analysis/plots)

## Example Result (Key Comparison)

Using the command above, the aggregate table highlights the trade-off between pre-positioning vs no pre-positioning:
- `adaptive_preposition` tends to reduce mean/p95 ETA compared to `no_preposition` on hotspot-shifted demand.

Compute time values are machine-dependent; route ETA/wait metrics are deterministic for a fixed seed and network.

## Complexity Notes (High Level)

Let `A` active assignments, average route length `L`, and `K` affected assignments for a changed edge:
- **Edge update -> affected assignments**: from `O(A * L)` scan to `O(K)` lookups via `AssignmentRouteIndex` (plus `O(L)` maintenance on assignment/reroute/cancel).

Let `M` predictor models, `Z` zones with history, and `F` pending forecasts:
- **Adaptive predictor forecast**: `O(M * Z)` to combine model forecasts (sparse in practice).
- **Adaptive predictor weight update** (per matured forecast): `O(M * Z log N)` worst-case via per-zone binary-search counts over incident history.

