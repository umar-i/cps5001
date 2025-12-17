# First Class (80-100) TODO

Goal: reach the **First Class (80-100)** software + report criteria from `requirements.txt` with small, testable increments.

## Software Artefact (First Class)

- [ ] Adaptive prediction: dynamic demand predictor that learns from historical error (online weighting / parameter adaptation)
- [ ] Efficient dynamic updates: avoid scanning all active routes on edge updates (index affected assignments)
- [ ] Simulated real-time load: add a synthetic load scenario runner (repeatable seeds; incident spikes + congestion)
- [ ] Statistical evaluation: aggregate results across runs (mean/percentiles) and export summary CSVs
- [ ] Lightweight visualisation: produce report-ready tables (and optional ASCII/Markdown summaries) from exported metrics

## Report Material (First Class)

- [ ] Add `docs/first-class.md` with design notes + complexity + evaluation results + ethical reflection
- [ ] Update `docs/evaluation.md` with the new evaluation runner and reproducible commands
- [ ] Extend `docs/ethics.md` with fairness metrics and trade-off discussion based on measured results
- [ ] Add/refresh diagrams in `docs/diagrams/` where they add clarity

## Version Control Evidence (GitHub)

- [ ] Implement via feature branch + small commits pushed regularly
- [ ] Open PR, pass CI, squash-merge to `main`, delete branch
- [ ] Tag milestone: `v1.0.0-first`

