package com.neca.perds.sim;

import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.DispatchCentre;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;

import java.time.Duration;
import java.util.Objects;

public sealed interface SystemCommand permits
        SystemCommand.ReportIncidentCommand,
        SystemCommand.ResolveIncidentCommand,
        SystemCommand.AddNodeCommand,
        SystemCommand.RemoveNodeCommand,
        SystemCommand.PutEdgeCommand,
        SystemCommand.RemoveEdgeCommand,
        SystemCommand.UpdateEdgeCommand,
        SystemCommand.RegisterUnitCommand,
        SystemCommand.SetUnitStatusCommand,
        SystemCommand.MoveUnitCommand,
        SystemCommand.PrepositionUnitsCommand,
        SystemCommand.RegisterDispatchCentreCommand {

    record ReportIncidentCommand(Incident incident) implements SystemCommand {
        public ReportIncidentCommand {
            Objects.requireNonNull(incident, "incident");
        }
    }

    record ResolveIncidentCommand(IncidentId incidentId) implements SystemCommand {
        public ResolveIncidentCommand {
            Objects.requireNonNull(incidentId, "incidentId");
        }
    }

    record AddNodeCommand(Node node) implements SystemCommand {
        public AddNodeCommand {
            Objects.requireNonNull(node, "node");
        }
    }

    record RemoveNodeCommand(NodeId nodeId) implements SystemCommand {
        public RemoveNodeCommand {
            Objects.requireNonNull(nodeId, "nodeId");
        }
    }

    record PutEdgeCommand(Edge edge) implements SystemCommand {
        public PutEdgeCommand {
            Objects.requireNonNull(edge, "edge");
        }
    }

    record RemoveEdgeCommand(NodeId from, NodeId to) implements SystemCommand {
        public RemoveEdgeCommand {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    record UpdateEdgeCommand(NodeId from, NodeId to, EdgeWeights weights, EdgeStatus status) implements SystemCommand {
        public UpdateEdgeCommand {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(weights, "weights");
            Objects.requireNonNull(status, "status");
        }
    }

    record RegisterUnitCommand(ResponseUnit unit) implements SystemCommand {
        public RegisterUnitCommand {
            Objects.requireNonNull(unit, "unit");
        }
    }

    record SetUnitStatusCommand(UnitId unitId, UnitStatus status) implements SystemCommand {
        public SetUnitStatusCommand {
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(status, "status");
        }
    }

    record MoveUnitCommand(UnitId unitId, NodeId newNodeId) implements SystemCommand {
        public MoveUnitCommand {
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(newNodeId, "newNodeId");
        }
    }

    record PrepositionUnitsCommand(Duration horizon) implements SystemCommand {
        public PrepositionUnitsCommand {
            Objects.requireNonNull(horizon, "horizon");
            if (horizon.isNegative() || horizon.isZero()) {
                throw new IllegalArgumentException("horizon must be > 0");
            }
        }
    }

    record RegisterDispatchCentreCommand(DispatchCentre dispatchCentre) implements SystemCommand {
        public RegisterDispatchCentreCommand {
            Objects.requireNonNull(dispatchCentre, "dispatchCentre");
        }
    }
}
