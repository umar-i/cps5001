package com.neca.perds.prediction;

import com.neca.perds.system.SystemSnapshot;

public interface PrepositioningStrategy {
    RepositionPlan plan(SystemSnapshot snapshot, DemandForecast forecast);
}

