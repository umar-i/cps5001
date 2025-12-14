package com.neca.perds.prediction;

import com.neca.perds.model.NodeId;
import com.neca.perds.model.ZoneId;

public interface ZoneAssigner {
    ZoneId zoneFor(NodeId nodeId);
}

