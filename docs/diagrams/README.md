# Diagrams

Store diagrams in text-first formats (e.g., Mermaid, PlantUML source, Graphviz `.dot`) so they can be version-controlled.

GitHub only renders Mermaid when the diagram is inside a Markdown code fence (` ```mermaid ... ``` `).
The `.mmd` files in this folder are raw Mermaid source; the sections below embed the same content so it renders directly on GitHub.

If you edit a `.mmd` file, copy/paste its contents into the matching Mermaid block below.

## Third Class: Class Diagram

Source: `third-class-class-diagram.mmd`

```mermaid
classDiagram
    direction LR

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

    class Router {
        <<interface>>
        +findRoute(GraphReadView, NodeId, NodeId, EdgeCostFunction) Optional~Route~
    }

    class DijkstraRouter
    Router <|.. DijkstraRouter

    class IndexedMinPriorityQueue {
        <<interface>>
        +insert(int, double)
        +extractMin() int
        +decreaseKey(int, double)
    }
    class BinaryHeapIndexedMinPriorityQueue
    IndexedMinPriorityQueue <|.. BinaryHeapIndexedMinPriorityQueue
    DijkstraRouter --> IndexedMinPriorityQueue : uses

    class DispatchEngine {
        <<interface>>
        +compute(SystemSnapshot) List~DispatchCommand~
    }
    class DefaultDispatchEngine
    DispatchEngine <|.. DefaultDispatchEngine

    class DispatchPolicy {
        <<interface>>
        +choose(SystemSnapshot, Incident) Optional~DispatchDecision~
    }
    class NearestAvailableUnitPolicy
    DispatchPolicy <|.. NearestAvailableUnitPolicy
    NearestAvailableUnitPolicy --> Router : routes

    class PerdsController
    PerdsController --> Graph : owns
    PerdsController --> DispatchEngine : computes

    class SystemCommand
    class SystemSnapshot
    PerdsController --> SystemCommand : executes
    PerdsController --> SystemSnapshot : produces

    class DispatchCommand
    class DispatchDecision
    class Assignment
    DispatchDecision --> Assignment
    DefaultDispatchEngine --> DispatchPolicy
    DefaultDispatchEngine --> DispatchCommand : emits
```

## Third Class: Dispatch Sequence

Source: `third-class-dispatch-sequence.mmd`

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Controller as PerdsController
    participant Graph as AdjacencyMapGraph
    participant Engine as DefaultDispatchEngine
    participant Policy as NearestAvailableUnitPolicy
    participant Router as DijkstraRouter

    User->>Controller: execute(ReportIncidentCommand)
    Controller->>Engine: compute(snapshot)
    Engine->>Policy: choose(snapshot, incident)
    Policy->>Router: findRoute(graph, unitNode, incidentNode)
    Router->>Graph: outgoingEdges(...) / getEdge(...)
    Router-->>Policy: Route
    Policy-->>Engine: DispatchDecision(Assignment, Rationale)
    Engine-->>Controller: AssignUnitCommand(...)
    Controller->>Controller: applyAssignment(...)
    Controller-->>User: assignment stored + unit EN_ROUTE
```

## Upper Second (2:1): Route Invalidation on Edge Closure

Source: `upper-second-route-invalidation-sequence.mmd`

```mermaid
sequenceDiagram
    participant Sim as SimulationEngine
    participant Ctrl as PerdsController
    participant G as Graph
    participant D as DispatchEngine
    participant M as MetricsCollector

    Sim->>Ctrl: execute(UpdateEdgeCommand, t)
    Ctrl->>G: updateEdge(from,to,...)
    Ctrl->>Ctrl: cancel assignments using (from->to)
    Ctrl->>M: recordDispatchCommandApplied(CANCEL_ASSIGNMENT)
    Ctrl->>D: compute(snapshot)
    D-->>Ctrl: [AssignUnitCommand...]
    Ctrl->>Ctrl: applyAssignment(...)
    Ctrl->>M: recordDispatchDecision(...)
    Ctrl->>M: recordDispatchCommandApplied(ASSIGN_UNIT)
```

## Lower First: Reroute on Congestion / Edge Update

Source: `lower-first-reroute-sequence.mmd`

```mermaid
sequenceDiagram
    participant Scenario
    participant Controller as PerdsController
    participant Graph
    participant Router
    participant Dispatch as DispatchEngine

    Scenario->>Controller: UPDATE_EDGE(from,to,weights,status)
    Controller->>Graph: updateEdge(from,to,...)
    Controller->>Controller: find assignments using edge

    alt route exists
        Controller->>Router: findRoute(unitNode, incidentNode)
        Router-->>Controller: Route
        Controller->>Controller: apply REROUTE_UNIT(unitId, route)
    else unreachable
        Controller->>Controller: apply CANCEL_ASSIGNMENT(incidentId)
    end

    Controller->>Dispatch: compute(snapshot)
    Dispatch-->>Controller: dispatch commands
    Controller->>Controller: apply commands
```

## Lower First: Pre-positioning Sequence

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

## First Class (80-100): Synthetic Evaluation Sequence

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
