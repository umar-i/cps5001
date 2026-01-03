package com.neca.perds.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HtmlMetricsExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void exportReport_generatesValidHtml() throws IOException {
        Path outputPath = tempDir.resolve("report.html");
        var config = new HtmlMetricsExporter.EvaluationConfig("2h", 20, 100, 50, 5);
        var variants = List.of(
                new HtmlMetricsExporter.VariantMetrics("no_preposition", 10, 120.5, 180.0, 15.2, 25.0, 500.0, 2.5, 10.0),
                new HtmlMetricsExporter.VariantMetrics("adaptive_preposition", 10, 95.3, 140.0, 10.5, 18.0, 520.0, 1.2, 12.0)
        );

        HtmlMetricsExporter.exportReport(outputPath, config, variants);

        assertTrue(Files.exists(outputPath));
        String content = Files.readString(outputPath);
        assertTrue(content.contains("<!DOCTYPE html>"));
        assertTrue(content.contains("PERDS Evaluation Report"));
    }

    @Test
    void exportReport_containsConfiguration() throws IOException {
        Path outputPath = tempDir.resolve("report.html");
        var config = new HtmlMetricsExporter.EvaluationConfig("2h", 20, 100, 50, 5);
        var variants = List.of(
                new HtmlMetricsExporter.VariantMetrics("test", 10, 100.0, 150.0, 10.0, 20.0, 500.0, 1.0, 5.0)
        );

        HtmlMetricsExporter.exportReport(outputPath, config, variants);

        String content = Files.readString(outputPath);
        assertTrue(content.contains("2h"));
        assertTrue(content.contains("20"));
        assertTrue(content.contains("100"));
        assertTrue(content.contains("50"));
    }

    @Test
    void exportReport_containsVariantNames() throws IOException {
        Path outputPath = tempDir.resolve("report.html");
        var config = new HtmlMetricsExporter.EvaluationConfig("1h", 10, 50, 25, 2);
        var variants = List.of(
                new HtmlMetricsExporter.VariantMetrics("variant_alpha", 5, 100.0, 150.0, 10.0, 20.0, 500.0, 1.0, 5.0),
                new HtmlMetricsExporter.VariantMetrics("variant_beta", 5, 90.0, 130.0, 8.0, 15.0, 480.0, 0.5, 6.0)
        );

        HtmlMetricsExporter.exportReport(outputPath, config, variants);

        String content = Files.readString(outputPath);
        assertTrue(content.contains("variant_alpha"));
        assertTrue(content.contains("variant_beta"));
    }

    @Test
    void exportReport_containsSvgCharts() throws IOException {
        Path outputPath = tempDir.resolve("report.html");
        var config = new HtmlMetricsExporter.EvaluationConfig("1h", 10, 50, 25, 2);
        var variants = List.of(
                new HtmlMetricsExporter.VariantMetrics("test", 5, 100.0, 150.0, 10.0, 20.0, 500.0, 1.0, 5.0)
        );

        HtmlMetricsExporter.exportReport(outputPath, config, variants);

        String content = Files.readString(outputPath);
        assertTrue(content.contains("<svg"));
        assertTrue(content.contains("</svg>"));
        assertTrue(content.contains("<rect"));
    }

    @Test
    void exportReport_containsMetricsTable() throws IOException {
        Path outputPath = tempDir.resolve("report.html");
        var config = new HtmlMetricsExporter.EvaluationConfig("1h", 10, 50, 25, 2);
        var variants = List.of(
                new HtmlMetricsExporter.VariantMetrics("test", 5, 100.0, 150.0, 10.0, 20.0, 500.0, 1.0, 5.0)
        );

        HtmlMetricsExporter.exportReport(outputPath, config, variants);

        String content = Files.readString(outputPath);
        assertTrue(content.contains("<table>"));
        assertTrue(content.contains("ETA Avg"));
        assertTrue(content.contains("Wait Avg"));
        assertTrue(content.contains("Compute Avg"));
    }

    @Test
    void exportReport_highlightsBestValues() throws IOException {
        Path outputPath = tempDir.resolve("report.html");
        var config = new HtmlMetricsExporter.EvaluationConfig("1h", 10, 50, 25, 2);
        var variants = List.of(
                new HtmlMetricsExporter.VariantMetrics("worse", 5, 150.0, 200.0, 20.0, 30.0, 600.0, 3.0, 8.0),
                new HtmlMetricsExporter.VariantMetrics("better", 5, 90.0, 120.0, 8.0, 12.0, 400.0, 1.0, 4.0)
        );

        HtmlMetricsExporter.exportReport(outputPath, config, variants);

        String content = Files.readString(outputPath);
        assertTrue(content.contains("class=\"num best\""));
    }

    @Test
    void exportReport_escapesHtmlCharacters() throws IOException {
        Path outputPath = tempDir.resolve("report.html");
        var config = new HtmlMetricsExporter.EvaluationConfig("<test>", 10, 50, 25, 2);
        var variants = List.of(
                new HtmlMetricsExporter.VariantMetrics("variant<script>", 5, 100.0, 150.0, 10.0, 20.0, 500.0, 1.0, 5.0)
        );

        HtmlMetricsExporter.exportReport(outputPath, config, variants);

        String content = Files.readString(outputPath);
        assertFalse(content.contains("<script>"));
        assertTrue(content.contains("&lt;test&gt;"));
        assertTrue(content.contains("variant&lt;script&gt;"));
    }

    @Test
    void exportReport_emptyVariants_throws() {
        Path outputPath = tempDir.resolve("report.html");
        var config = new HtmlMetricsExporter.EvaluationConfig("1h", 10, 50, 25, 2);

        assertThrows(IllegalArgumentException.class, () ->
                HtmlMetricsExporter.exportReport(outputPath, config, List.of()));
    }

    @Test
    void variantMetrics_invalidRuns_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new HtmlMetricsExporter.VariantMetrics("test", 0, 100.0, 150.0, 10.0, 20.0, 500.0, 1.0, 5.0));
    }

    @Test
    void variantMetrics_nullName_throws() {
        assertThrows(NullPointerException.class, () ->
                new HtmlMetricsExporter.VariantMetrics(null, 5, 100.0, 150.0, 10.0, 20.0, 500.0, 1.0, 5.0));
    }

    @Test
    void exportReport_multipleVariants_allIncluded() throws IOException {
        Path outputPath = tempDir.resolve("report.html");
        var config = new HtmlMetricsExporter.EvaluationConfig("1h", 10, 50, 25, 2);
        var variants = List.of(
                new HtmlMetricsExporter.VariantMetrics("v1", 5, 100.0, 150.0, 10.0, 20.0, 500.0, 1.0, 5.0),
                new HtmlMetricsExporter.VariantMetrics("v2", 5, 95.0, 140.0, 9.0, 18.0, 480.0, 0.8, 4.5),
                new HtmlMetricsExporter.VariantMetrics("v3", 5, 105.0, 160.0, 11.0, 22.0, 520.0, 1.2, 5.5)
        );

        HtmlMetricsExporter.exportReport(outputPath, config, variants);

        String content = Files.readString(outputPath);
        assertTrue(content.contains("v1"));
        assertTrue(content.contains("v2"));
        assertTrue(content.contains("v3"));
    }
}
