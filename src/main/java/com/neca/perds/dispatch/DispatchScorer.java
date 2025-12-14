package com.neca.perds.dispatch;

import com.neca.perds.model.Incident;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.routing.Route;
import com.neca.perds.system.SystemSnapshot;

public interface DispatchScorer {
    DispatchRationale score(SystemSnapshot snapshot, Incident incident, ResponseUnit unit, Route route);
}

