package com.neca.perds.metrics;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class CsvMetricsExporter implements MetricsExporter {
    private final MetricsCollector metricsCollector;

    public CsvMetricsExporter(MetricsCollector metricsCollector) {
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
    }

    @Override
    public void exportTo(Path outputDirectory) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

