# Lower First (70-79) TODO

Goal: reach the **Lower First Class** software + report criteria from `requirements.txt` with small, testable increments.

## Software Artefact (Lower First)

- [x] Adapt to congestion: reroute active assignments when edge weights change (fallback to cancel + reassignment if unreachable)
- [x] Predictive demand: implement a rate-based (windowed) demand predictor that reacts to incident spikes
- [x] Systematic pre-positioning: distribute available units across the top-demand zones (limit moves; respect unit availability)
- [x] Optimisation: keep allocation efficient (continue using multi-source routing; avoid per-unit rerouting where possible)
- [x] Add targeted tests for rerouting, prediction, and pre-positioning

## Evaluation / Visualisation

- [x] Add a larger-scale scenario (or generator) to exercise congestion + spikes + pre-positioning
- [x] Add a simple, reproducible metrics summary (CSV + printed table) suitable for the report

## Report Material (Lower First)

- [x] Add `docs/lower-first.md` with design notes + complexity analysis + diagrams
- [x] Update `docs/evaluation.md` with new scenarios + what to analyse
- [x] Update `docs/ethics.md` with fairness/robustness considerations tied to metrics

## Version Control Evidence (Local-Only)

- [x] Use feature branches + small commits per task (no direct commits to `main`)
- [x] Merge completed branches into `main` after tests
- [x] Tag milestones (e.g., `v0.3.0-21`, `v0.4.0-lower-first`)

