package com.neca.perds.dispatch;

import com.neca.perds.system.SystemSnapshot;

import java.util.List;

public interface DispatchEngine {
    List<DispatchCommand> compute(SystemSnapshot snapshot);
}

