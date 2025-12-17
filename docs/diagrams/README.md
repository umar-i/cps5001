# Diagrams (Rendered On GitHub)

GitHub renders Mermaid diagrams **only** when they are inside a Markdown fence (` ```mermaid ... ``` `).
This page contains multiple diagrams — there are no “slides”; just scroll, or jump using the table of contents below.

This folder keeps two things:

- **Current diagrams (rendered below)**: these represent the latest system.
- **Legacy diagrams (not rendered)**: older snapshots kept as reference only.

## Current Diagrams (Latest System)

- 1) System Class Diagram (Current)
- 2) Per-Event Controller Step (Current)
- 3) Edge Update: Targeted Reroute / Cancel (Current)
- 4) Pre-positioning (Current)
- 5) Synthetic Evaluation Runner (Current)

### 1) System Class Diagram (Current)

Source: `current-system-class-diagram.mmd`

```mermaid
classDiagram
    direction LR

    class Main
    class CsvGraphLoader
    class CsvScenarioLoader
    class SimulationEngine
    class TimedEvent
    class SystemCommand
    class SystemCommandExecutor {
        <<interface>>
        +execute(SystemCommand, Instant)
    }

    Main --> CsvGraphLoader : loads
    Main --> CsvScenarioLoader : loads
    Main --> SimulationEngine : schedules
    Main --> SystemCommandExecutor : drives

    SimulationEngine --> TimedEvent : priority queue
    TimedEvent --> SystemCommand : contains
    SystemCommandExecutor --> SystemCommand : executes

    class PerdsController
    SystemCommandExecutor <|.. PerdsController

    class GraphReadView {
        <<interface>>
        +getNode(NodeId) Optional~Node~
        +nodeIds() Collection~NodeId~
        +outgoingEdges(NodeId) Collection~Edge~
        +getEdge(NodeId, NodeId) Optional~Edge~
        +version() long
    }

    class GraphWriteOps {
        <<interface>>
        +addNode(Node) long
        +removeNode(NodeId) long
        +putEdge(Edge) long
        +removeEdge(NodeId, NodeId) long
        +updateEdge(NodeId, NodeId, EdgeWeights, EdgeStatus) long
    }

    class Graph {
        <<interface>>
    }
    GraphReadView <|-- Graph
    GraphWriteOps <|-- Graph

    class AdjacencyMapGraph
    Graph <|.. AdjacencyMapGraph
    PerdsController --> Graph : owns

    class AssignmentRouteIndex
    PerdsController --> AssignmentRouteIndex : indexes routes

    class DispatchEngine {
        <<interface>>
        +compute(SystemSnapshot) List~DispatchCommand~
    }
    class DefaultDispatchEngine
    DispatchEngine <|.. DefaultDispatchEngine
    PerdsController --> DispatchEngine : computes

    class IncidentPrioritizer {
        <<interface>>
        +comparator() Comparator~Incident~
    }
    class SeverityThenOldestPrioritizer
    IncidentPrioritizer <|.. SeverityThenOldestPrioritizer
    DefaultDispatchEngine --> IncidentPrioritizer : orders incidents

    class DispatchPolicy {
        <<interface>>
        +choose(SystemSnapshot, Incident) Optional~DispatchDecision~
    }
    class NearestAvailableUnitPolicy
    class MultiSourceNearestAvailableUnitPolicy
    DispatchPolicy <|.. NearestAvailableUnitPolicy
    DispatchPolicy <|.. MultiSourceNearestAvailableUnitPolicy
    DefaultDispatchEngine --> DispatchPolicy : selects unit

    class Router {
        <<interface>>
        +findRoute(GraphReadView, NodeId, NodeId, EdgeCostFunction) Optional~Route~
    }
    class DijkstraRouter
    class AStarRouter
    Router <|.. DijkstraRouter
    Router <|.. AStarRouter
    NearestAvailableUnitPolicy --> Router : routes
    MultiSourceNearestAvailableUnitPolicy --> Router : routes

    class VirtualSourceGraphView
    GraphReadView <|.. VirtualSourceGraphView
    MultiSourceNearestAvailableUnitPolicy --> VirtualSourceGraphView : wraps graph

    class IndexedMinPriorityQueue {
        <<interface>>
        +insert(int, double)
        +extractMin() int
        +decreaseKey(int, double)
    }
    class BinaryHeapIndexedMinPriorityQueue
    IndexedMinPriorityQueue <|.. BinaryHeapIndexedMinPriorityQueue
    DijkstraRouter --> IndexedMinPriorityQueue : uses
    AStarRouter --> IndexedMinPriorityQueue : uses

    class DemandPredictor {
        <<interface>>
        +observe(Incident)
        +forecast(Instant, Duration) DemandForecast
    }
    class AdaptiveEnsembleDemandPredictor
    class SlidingWindowDemandPredictor
    class ExponentialSmoothingDemandPredictor
    DemandPredictor <|.. AdaptiveEnsembleDemandPredictor
    DemandPredictor <|.. SlidingWindowDemandPredictor
    DemandPredictor <|.. ExponentialSmoothingDemandPredictor
    PerdsController --> DemandPredictor : observes + forecasts

    class PrepositioningStrategy {
        <<interface>>
        +plan(SystemSnapshot, DemandForecast) RepositionPlan
    }
    class GreedyHotspotPrepositioningStrategy
    class MultiHotspotPrepositioningStrategy
    PrepositioningStrategy <|.. GreedyHotspotPrepositioningStrategy
    PrepositioningStrategy <|.. MultiHotspotPrepositioningStrategy
    PerdsController --> PrepositioningStrategy : repositions

    class MetricsCollector {
        <<interface>>
        +recordDispatchComputation(...)
        +recordDispatchDecision(...)
        +recordDispatchCommandApplied(...)
    }
    class InMemoryMetricsCollector
    MetricsCollector <|.. InMemoryMetricsCollector
    PerdsController --> MetricsCollector : records

    class MetricsExporter {
        <<interface>>
        +exportTo(Path)
    }
    class CsvMetricsExporter
    MetricsExporter <|.. CsvMetricsExporter
    Main --> MetricsExporter : exports

    class SyntheticLoadScenarioGenerator
    Main --> SyntheticLoadScenarioGenerator : generates

    class ScenarioSummary
    Main --> ScenarioSummary : aggregates
```

### 2) Per-Event Controller Step (Current)

Source: `current-controller-step-sequence.mmd`

```mermaid
sequenceDiagram
    autonumber
    participant Sim as SimulationEngine
    participant Ctrl as PerdsController
    participant G as Graph
    participant Index as AssignmentRouteIndex
    participant Pred as DemandPredictor
    participant Pre as PrepositioningStrategy
    participant Eng as DispatchEngine
    participant Metrics as MetricsCollector

    Sim->>Ctrl: execute(SystemCommand, at)

    alt ReportIncidentCommand
        Ctrl->>Ctrl: incidents.put(incident)
        Ctrl->>Pred: observe(incident)
    end

    alt UpdateEdgeCommand / RemoveEdgeCommand
        Ctrl->>G: updateEdge/removeEdge(from,to,...)
        Ctrl->>Index: incidentIdsUsingEdge(from,to)
        loop each affected incidentId
            Ctrl->>Ctrl: rerouteOrCancelAssignment(incidentId)
            Ctrl->>Metrics: recordDispatchCommandApplied(REROUTE or CANCEL)
        end
    end

    alt PrepositionUnitsCommand
        Ctrl->>Pred: forecast(at, horizon)
        Ctrl->>Pre: plan(snapshot, forecast)
        Ctrl->>Ctrl: move available units
    end

    Ctrl->>Eng: compute(snapshot)
    Ctrl->>Metrics: recordDispatchComputation(...)
    loop each DispatchCommand
        Ctrl->>Ctrl: applyDispatchCommand(...)
        Ctrl->>Metrics: recordDispatchCommandApplied(...)
    end
```

### 3) Edge Update: Targeted Reroute / Cancel (Current)

Source: `current-edge-update-reroute-sequence.mmd`

```mermaid
sequenceDiagram
    autonumber
    participant Scenario
    participant Ctrl as PerdsController
    participant G as Graph
    participant Index as AssignmentRouteIndex
    participant Router as DijkstraRouter
    participant Metrics as MetricsCollector

    Scenario->>Ctrl: execute(UpdateEdgeCommand/RemoveEdgeCommand, at)
    Ctrl->>G: updateEdge/removeEdge(from,to,...)
    Ctrl->>Index: incidentIdsUsingEdge(from,to)

    loop each affected incidentId
        Ctrl->>Router: findRoute(unitNode, incidentNode)
        alt route exists
            Router-->>Ctrl: Route
            Ctrl->>Ctrl: apply REROUTE_UNIT(unitId, route)
            Ctrl->>Metrics: recordDispatchCommandApplied(REROUTE_UNIT)
        else unreachable
            Router-->>Ctrl: empty
            Ctrl->>Ctrl: apply CANCEL_ASSIGNMENT(incidentId)
            Ctrl->>Metrics: recordDispatchCommandApplied(CANCEL_ASSIGNMENT)
        end
    end
```

### 4) Pre-positioning (Current)

Source: `lower-first-prepositioning-sequence.mmd`

```mermaid
sequenceDiagram
    participant Scenario
    participant Controller as PerdsController
    participant Predictor as DemandPredictor
    participant Strategy as PrepositioningStrategy
    participant Router

    Scenario->>Controller: PREPOSITION_UNITS(horizon)
    Controller->>Predictor: forecast(at, horizon)
    Controller->>Strategy: plan(snapshot, forecast)

    loop up to maxMoves
        Strategy->>Router: findRoute(VirtualSourceGraph, zoneNode)
        Router-->>Strategy: Route (startNode -> zoneNode)
    end

    Strategy-->>Controller: RepositionPlan(moves)
    Controller->>Controller: move available units
```

### 5) Synthetic Evaluation Runner (Current)

Source: `first-class-evaluation-sequence.mmd`

```mermaid
sequenceDiagram
    participant CLI as CLI (Main evaluate)
    participant Gen as SyntheticLoadScenarioGenerator
    participant Engine as SimulationEngine
    participant Ctrl as PerdsController
    participant Pred as DemandPredictor
    participant Pre as PrepositioningStrategy
    participant Metrics as InMemoryMetricsCollector
    participant Export as CsvMetricsExporter

    CLI->>Gen: generate(graph, start, config, seed)
    CLI->>Engine: scheduleAll(events)
    loop Each timed event
        Engine->>Ctrl: execute(command, time)
        Ctrl->>Pred: observe(incident) (report)
        Ctrl->>Pred: forecast(at, horizon) (preposition)
        Ctrl->>Pre: plan(snapshot, forecast)
        Ctrl->>Metrics: record computation/decisions/commands
    end
    CLI->>Export: exportTo(outDir/runs/<variant>/run-*)
    CLI->>CLI: write evaluation_summary.csv + evaluation_aggregate.md
```

## Legacy Diagrams (Reference Only, Not Rendered)

These are kept as a historical trail (what you built on), but they are **not embedded** here:

- `third-class-class-diagram.mmd` (baseline)
- `third-class-dispatch-sequence.mmd` (baseline)
- `upper-second-route-invalidation-sequence.mmd` (older 2:1 snapshot)
- `lower-first-reroute-sequence.mmd` (older reroute snapshot)

If you ever want to render a legacy diagram, copy its contents into a ` ```mermaid` block in a Markdown file or paste into https://mermaid.live.
