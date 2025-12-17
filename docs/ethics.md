# Ethical Considerations

This project is a simulation, but the design choices mirror real-world trade-offs where mistakes can harm people.

## Fairness (Coverage vs Efficiency)

Dispatching "nearest first" and pre-positioning toward hotspots improves average response time, but can also reduce coverage in lower-demand areas (e.g., rural zones). In a real system this can systematically disadvantage some regions.

Practical mitigations (report scope):
- define minimum coverage constraints (e.g., at least one unit per region/dispatch centre)
- use fairness-aware scoring or penalties when relocating units away from underserved areas
- audit outcomes per zone (mean/95th percentile wait time, cancellation rate, queued incidents)

## Transparency / Explainability

Dispatch decisions export a per-decision rationale (components like travel time, distance, severity) to CSV via `dispatch_decisions.csv`. This supports auditability: you can trace *why* a unit was chosen and compare behaviour across scenarios.

## Robustness and Failure Modes

The system is designed to handle dynamic changes (unit unavailability, edge closures/congestion) by rerouting or cancelling and reassigning when necessary. For the report, robustness should be evaluated under overload:
- how quickly queued incidents recover after disruptions
- how often cancellations/reroutes occur under changing travel times
- dispatch computation time under larger event loads

## Privacy / Data Minimisation

Even simulated incident data can normalise unsafe data handling practices. A real deployment would require strict data minimisation (store only what is needed), access controls, retention limits, and careful logging/redaction of personally identifiable information.

## Bias in Historical (or Simulated) Data

Prediction based on historical incidents can encode reporting bias (e.g., over-reporting in monitored areas). A report should discuss data quality, potential biases, and the risk of feedback loops where pre-positioning increases service in already-overserved areas.
