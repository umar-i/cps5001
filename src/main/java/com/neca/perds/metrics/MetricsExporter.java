package com.neca.perds.metrics;

import java.io.IOException;
import java.nio.file.Path;

public interface MetricsExporter {
    void exportTo(Path outputDirectory) throws IOException;
}

