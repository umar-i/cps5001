package com.neca.perds.sim;

import java.time.Instant;

public interface SystemCommandExecutor {
    void execute(SystemCommand command, Instant at);
}

