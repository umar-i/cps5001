# First Class (80-100) TODO

Goal: reach the **First Class (80-100)** software + report criteria from `requirements.txt` with small, testable increments.

## Software Artefact (First Class)

- [x] Adaptive prediction: dynamic demand predictor that learns from historical error (online weighting / parameter adaptation)
- [x] Efficient dynamic updates: avoid scanning all active routes on edge updates (index affected assignments)
- [x] Simulated real-time load: add a synthetic load scenario runner (repeatable seeds; incident spikes + congestion)
- [x] Statistical evaluation: aggregate results across runs (mean/percentiles) and export summary CSVs
- [x] Lightweight visualisation: produce report-ready tables (and optional ASCII/Markdown summaries) from exported metrics

## Report Material (First Class)

- [x] Add `docs/first-class.md` with design notes + complexity + evaluation results + ethical reflection
- [x] Update `docs/evaluation.md` with the new evaluation runner and reproducible commands
- [x] Extend `docs/ethics.md` with fairness metrics and trade-off discussion based on measured results
- [x] Add/refresh diagrams in `docs/diagrams/` where they add clarity

## Version Control Evidence (GitHub)

- [x] Implement via feature branch + small commits pushed regularly
- [x] Open PR, pass CI, squash-merge to `main`, delete branch
- [x] Tag milestone: `v1.0.0-first`
