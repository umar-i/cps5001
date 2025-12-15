# Lower First Class (70-79) Deliverable (In Progress)

This document tracks progress toward the **Lower First Class** grade band.

## Target Outcomes

- Stronger predictive demand + proactive resource placement (not just a single hotspot move).
- Better dynamic adaptability: congestion/edge updates trigger rerouting where possible.
- Evidence of efficiency (reuse existing multi-source routing; keep update handling localised).
- Clear, report-ready evaluation notes + ethical considerations tied to exported metrics.

## Work Items

See `docs/todo-first.md`.

## Implemented So Far

- Adaptive rerouting: when an edge used by an active assignment is updated (closure/removal/weight change), the system recalculates a route from the unit's current node to the incident. If unreachable, it cancels the assignment and allows reassignment in the same step.
- Sliding-window demand prediction: forecasts zone demand using a recent-time window, scaling recent incident counts into the requested prediction horizon.
- Multi-hotspot pre-positioning: allocates a limited number of unit moves across the top-demand zones and picks nearest available units via multi-source routing.
- Scenario summaries: the CLI prints a small metrics summary (compute timings, decision/ETA stats, and command counts) alongside exporting raw CSVs.

