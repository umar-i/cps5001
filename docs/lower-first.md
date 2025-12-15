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

## Design Notes (High Level)

**Adaptive rerouting on edge updates**

- Trigger: `UPDATE_EDGE` / `REMOVE_EDGE` commands that affect an edge currently used by an active assignment route.
- Action: recompute a shortest path from the assigned unit's *current node* to the incident location using travel-time costs.
- Outcome: apply `REROUTE_UNIT` if a route exists; otherwise apply `CANCEL_ASSIGNMENT` and let the normal dispatch cycle reassign the incident.

**Sliding-window demand predictor**

- Keeps recent incident timestamps per zone for a fixed window (default: 1 hour).
- Forecast scales observed counts in the window into the requested horizon:
  - `expected = countInWindow * (horizon / window)`

**Multi-hotspot pre-positioning**

- Ranks zones by forecast demand and allocates up to `maxMoves` across the top `maxZones` proportionally.
- Picks units for each zone using a multi-source routing trick (virtual source -> many unit locations -> target zone), so each move needs one routing run rather than one per unit.

## Complexity Notes (High Level)

Let:
- `V` nodes, `E` edges.
- `A` active assignments, average route length `L`.
- `K` assignments whose routes use a changed edge.
- `Z` tracked zones with recent incidents.
- `M` planned moves (`M <= maxMoves`), `S` distinct unit start nodes (`S <= U`).

**Adaptive rerouting**
- Detect affected assignments: `O(A * L)` (scan stored routes for the changed edge).
- For each affected assignment: one shortest-path run `O((V + E) log V)`.
- Total per edge update: `O(A * L + K * (V + E) log V)`.

**Sliding-window predictor**
- `observe`: amortised `O(1)` per incident (enqueue + window pruning).
- `forecast`: `O(Z)` to prune/scale per-zone counts (plus amortised pruning work).

**Multi-hotspot pre-positioning**
- Demand ranking/allocation: `O(Z log Z)` (bounded by `maxZones` in practice).
- Per move: one multi-source shortest-path run over `V + 1` nodes and `E + S` edges: `O((V + E + S) log V)`.
- Total: `O(M * (V + E + S) log V)`.

## Diagrams

- `docs/diagrams/lower-first-reroute-sequence.mmd`
- `docs/diagrams/lower-first-prepositioning-sequence.mmd`

