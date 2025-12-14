package com.neca.perds.dispatch;

import com.neca.perds.model.Incident;

import java.util.Comparator;

public interface IncidentPrioritizer {
    Comparator<Incident> comparator();
}

