# Roadmap (Assessment Grade Bands)

Deliverables are cumulative: **Third → 2:2 → 2:1 → First**. Do not advance until the current band is complete, tested, and documented.

## Third Class (40–49)

- [x] Dynamic graph representation
- [x] Simple incident registration and dispatch
- [x] Basic routing (Dijkstra) + tests
- [x] Basic docs + diagrams

## Lower Second (2:2) (50–59)

- [x] Event-driven scenario simulation (multiple incidents over time)
- [x] Basic reallocation when a unit becomes unavailable
- [x] Partial prediction + one pre-position action
- [x] CSV loaders + CLI scenario runner
- [x] Basic metrics export

## Upper Second (2:1) (60–69)

- [ ] Efficient allocation algorithm (reduce routing recomputation)
- [ ] Real-time update handling for route changes (reroute/cancel + reassignment)
- [ ] Strengthen predictive logic/resource management integration
- [ ] Add evaluation scenario(s) and empirical write-up (`docs/evaluation.md`)
- [ ] Expand complexity analysis + diagrams for new components
- [ ] Continue iterative development with branches + PRs (CI should pass)

## First Class (70+)

- [ ] Larger-scale experiments + visualisation (CSV/diagrams)
- [ ] Stronger predictive demand + systematic pre-positioning
- [ ] Fairness/robustness considerations supported by metrics
- [ ] Release tagging at milestones (e.g., `v0.3.0-21`)

