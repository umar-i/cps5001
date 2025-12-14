# 2:2 TODO (Lower Second Class 50–59)

Goal: reach the **2:2** software + report criteria from `requirements.txt` with small, testable increments.

## Software Artefact (2:2)

- [x] Implement CSV parsing utility (`com.neca.perds.io.CsvUtils`) + tests
- [x] Implement CSV network loading (`com.neca.perds.io.CsvGraphLoader`) + tests
- [x] Implement CSV scenario/event loading (`com.neca.perds.io.CsvScenarioLoader`) + tests
- [x] Add CLI command to run a CSV scenario end-to-end
- [x] Basic reallocation: cancel + reassign when a unit becomes unavailable
- [x] Partial predictive element: implement simple demand predictor + one pre-position action
- [x] Export basic run metrics to CSV (`com.neca.perds.metrics.CsvMetricsExporter`)

## Report Material (2:2)

- [x] Add `docs/lower-second.md` explaining what’s implemented + basic complexity notes
- [x] Add/extend a small evaluation scenario and how to reproduce it

## CSV Formats (proposed)

**Nodes** (`nodes.csv`)

Header:
`id,type,label,x,y`

- `id`: string (e.g., `A`)
- `type`: `CITY|DISPATCH_CENTRE|INCIDENT_SITE`
- `label`: human label (non-blank)
- `x`,`y`: optional doubles (blank means “no coordinates”)

**Edges** (`edges.csv`)

Header:
`from,to,distanceKm,travelTimeSeconds,resourceAvailability,status`

- `distanceKm`: double `>= 0`
- `travelTimeSeconds`: long `>= 0`
- `resourceAvailability`: double in `[0,1]`
- `status`: `OPEN|CLOSED`

**Events** (`events.csv`)

Header:
`time,command,arg1,arg2,arg3,arg4,arg5,arg6`

`time` is an ISO-8601 instant (e.g., `2025-01-01T00:00:00Z`).

Planned commands:
- `REGISTER_UNIT`: `arg1=id`, `arg2=type`, `arg3=status`, `arg4=nodeId`
- `REPORT_INCIDENT`: `arg1=id`, `arg2=nodeId`, `arg3=severity`, `arg4=requiredUnitTypes` (pipe-separated, e.g., `AMBULANCE|FIRE_TRUCK`)
- `RESOLVE_INCIDENT`: `arg1=id`
- `SET_UNIT_STATUS`: `arg1=unitId`, `arg2=status`
- `MOVE_UNIT`: `arg1=unitId`, `arg2=nodeId`
