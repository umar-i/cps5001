package com.neca.perds.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Exports evaluation metrics as an HTML report with embedded SVG charts.
 * No external dependencies required - generates standalone HTML files.
 */
public final class HtmlMetricsExporter {

    private HtmlMetricsExporter() {}

    /**
     * Represents aggregated metrics for a single evaluation variant.
     */
    public record VariantMetrics(
            String name,
            int runs,
            double etaAvgMean,
            double etaAvgP95,
            double waitAvgMean,
            double waitAvgP95,
            double computeAvgMean,
            double cancelsMean,
            double reroutesMean
    ) {
        public VariantMetrics {
            Objects.requireNonNull(name, "name");
            if (runs <= 0) {
                throw new IllegalArgumentException("runs must be > 0");
            }
        }
    }

    /**
     * Represents configuration used for the evaluation.
     */
    public record EvaluationConfig(
            String duration,
            int unitCount,
            int incidentCount,
            int congestionEventCount,
            int unitOutageCount
    ) {}

    /**
     * Generates an HTML report with charts comparing variant metrics.
     *
     * @param outputPath path to write the HTML file
     * @param config evaluation configuration for context
     * @param variants list of variant metrics to compare
     * @throws IOException if the file cannot be written
     */
    public static void exportReport(Path outputPath, EvaluationConfig config, List<VariantMetrics> variants) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(variants, "variants");

        if (variants.isEmpty()) {
            throw new IllegalArgumentException("variants must not be empty");
        }

        String html = generateHtml(config, variants);
        Files.writeString(outputPath, html);
    }

    private static String generateHtml(EvaluationConfig config, List<VariantMetrics> variants) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>PERDS Evaluation Report</title>
                    <style>
                        * { box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                            margin: 0;
                            padding: 20px;
                            background: #f5f5f5;
                            color: #333;
                        }
                        .container { max-width: 1200px; margin: 0 auto; }
                        h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
                        h2 { color: #34495e; margin-top: 30px; }
                        .config-box {
                            background: #fff;
                            border-radius: 8px;
                            padding: 15px 20px;
                            margin-bottom: 20px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        }
                        .config-box h3 { margin: 0 0 10px 0; color: #7f8c8d; font-size: 14px; text-transform: uppercase; }
                        .config-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px; }
                        .config-item { text-align: center; }
                        .config-value { font-size: 24px; font-weight: bold; color: #2c3e50; }
                        .config-label { font-size: 12px; color: #7f8c8d; }
                        .chart-container {
                            background: #fff;
                            border-radius: 8px;
                            padding: 20px;
                            margin-bottom: 20px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        }
                        .chart-title { font-size: 16px; font-weight: 600; color: #2c3e50; margin-bottom: 15px; }
                        table {
                            width: 100%;
                            border-collapse: collapse;
                            background: #fff;
                            border-radius: 8px;
                            overflow: hidden;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        }
                        th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #eee; }
                        th { background: #3498db; color: #fff; font-weight: 600; }
                        tr:hover { background: #f8f9fa; }
                        tr:last-child td { border-bottom: none; }
                        .num { text-align: right; font-family: 'Consolas', monospace; }
                        .best { background: #d4edda; font-weight: bold; }
                        .legend { display: flex; gap: 20px; margin-top: 10px; flex-wrap: wrap; }
                        .legend-item { display: flex; align-items: center; gap: 5px; font-size: 12px; }
                        .legend-color { width: 12px; height: 12px; border-radius: 2px; }
                        svg { display: block; }
                        .footer { text-align: center; margin-top: 30px; color: #7f8c8d; font-size: 12px; }
                    </style>
                </head>
                <body>
                <div class="container">
                    <h1>PERDS Evaluation Report</h1>
                """);

        // Configuration section
        sb.append("""
                    <div class="config-box">
                        <h3>Evaluation Configuration</h3>
                        <div class="config-grid">
                """);
        sb.append(configItem(config.duration(), "Duration"));
        sb.append(configItem(String.valueOf(config.unitCount()), "Units"));
        sb.append(configItem(String.valueOf(config.incidentCount()), "Incidents"));
        sb.append(configItem(String.valueOf(config.congestionEventCount()), "Congestion Events"));
        sb.append(configItem(String.valueOf(config.unitOutageCount()), "Unit Outages"));
        sb.append(configItem(String.valueOf(variants.getFirst().runs()), "Runs per Variant"));
        sb.append("""
                        </div>
                    </div>
                """);

        // Charts section
        sb.append("<h2>Performance Comparison</h2>");

        // ETA chart
        sb.append(generateBarChart("Average Response Time (ETA)",
                variants, v -> v.etaAvgMean(), "seconds", true));

        // Wait time chart
        sb.append(generateBarChart("Average Wait Time",
                variants, v -> v.waitAvgMean(), "seconds", true));

        // Compute time chart
        sb.append(generateBarChart("Average Compute Time",
                variants, v -> v.computeAvgMean(), "μs", true));

        // Detailed metrics table
        sb.append("<h2>Detailed Metrics</h2>");
        sb.append(generateMetricsTable(variants));

        // Footer
        sb.append("""
                    <div class="footer">
                        Generated by PERDS (Predictive Emergency Response Dispatch System)
                    </div>
                </div>
                </body>
                </html>
                """);

        return sb.toString();
    }

    private static String configItem(String value, String label) {
        return String.format("""
                            <div class="config-item">
                                <div class="config-value">%s</div>
                                <div class="config-label">%s</div>
                            </div>
                """, escapeHtml(value), escapeHtml(label));
    }

    private static String generateBarChart(String title, List<VariantMetrics> variants,
                                           java.util.function.ToDoubleFunction<VariantMetrics> valueExtractor,
                                           String unit, boolean lowerIsBetter) {
        List<Double> values = variants.stream().mapToDouble(valueExtractor).boxed().toList();
        double maxValue = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (maxValue <= 0) maxValue = 1.0;

        double bestValue = lowerIsBetter
                ? values.stream().mapToDouble(Double::doubleValue).min().orElse(0)
                : values.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        int chartWidth = 600;
        int chartHeight = 40 * variants.size() + 40;
        int barHeight = 25;
        int labelWidth = 180;
        int barMaxWidth = chartWidth - labelWidth - 80;

        StringBuilder svg = new StringBuilder();
        svg.append(String.format("<svg width=\"%d\" height=\"%d\" xmlns=\"http://www.w3.org/2000/svg\">", chartWidth, chartHeight));

        String[] colors = {"#3498db", "#e74c3c", "#2ecc71", "#f39c12", "#9b59b6"};

        for (int i = 0; i < variants.size(); i++) {
            VariantMetrics v = variants.get(i);
            double value = valueExtractor.applyAsDouble(v);
            int barWidth = (int) ((value / maxValue) * barMaxWidth);
            int y = 20 + i * 40;
            String color = colors[i % colors.length];
            boolean isBest = Math.abs(value - bestValue) < 0.001;

            // Label
            svg.append(String.format("<text x=\"5\" y=\"%d\" font-size=\"12\" fill=\"#333\">%s</text>",
                    y + 17, escapeHtml(v.name())));

            // Bar
            svg.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\" rx=\"3\"/>",
                    labelWidth, y, Math.max(barWidth, 2), barHeight, color));

            // Value label
            String valueLabel = String.format("%.1f %s%s", value, unit, isBest ? " ★" : "");
            svg.append(String.format("<text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"#333\">%s</text>",
                    labelWidth + barWidth + 5, y + 17, escapeHtml(valueLabel)));
        }

        svg.append("</svg>");

        // Build legend
        StringBuilder legend = new StringBuilder("<div class=\"legend\">");
        for (int i = 0; i < variants.size(); i++) {
            legend.append(String.format(
                    "<div class=\"legend-item\"><div class=\"legend-color\" style=\"background:%s\"></div>%s</div>",
                    colors[i % colors.length], escapeHtml(variants.get(i).name())));
        }
        legend.append("</div>");

        return String.format("""
                <div class="chart-container">
                    <div class="chart-title">%s</div>
                    %s
                    %s
                </div>
                """, escapeHtml(title), svg.toString(), legend.toString());
    }

    private static String generateMetricsTable(List<VariantMetrics> variants) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("""
                <thead>
                    <tr>
                        <th>Variant</th>
                        <th class="num">Runs</th>
                        <th class="num">ETA Avg (s)</th>
                        <th class="num">ETA P95 (s)</th>
                        <th class="num">Wait Avg (s)</th>
                        <th class="num">Wait P95 (s)</th>
                        <th class="num">Compute Avg (μs)</th>
                        <th class="num">Cancels</th>
                        <th class="num">Reroutes</th>
                    </tr>
                </thead>
                <tbody>
                """);

        // Find best values for highlighting (lower is better for these metrics)
        double bestEta = variants.stream().mapToDouble(VariantMetrics::etaAvgMean).min().orElse(0);
        double bestWait = variants.stream().mapToDouble(VariantMetrics::waitAvgMean).min().orElse(0);
        double bestCompute = variants.stream().mapToDouble(VariantMetrics::computeAvgMean).min().orElse(0);

        for (VariantMetrics v : variants) {
            sb.append("<tr>");
            sb.append(String.format("<td>%s</td>", escapeHtml(v.name())));
            sb.append(String.format("<td class=\"num\">%d</td>", v.runs()));
            sb.append(formatCell(v.etaAvgMean(), bestEta));
            sb.append(String.format("<td class=\"num\">%.1f</td>", v.etaAvgP95()));
            sb.append(formatCell(v.waitAvgMean(), bestWait));
            sb.append(String.format("<td class=\"num\">%.1f</td>", v.waitAvgP95()));
            sb.append(formatCell(v.computeAvgMean(), bestCompute));
            sb.append(String.format("<td class=\"num\">%.1f</td>", v.cancelsMean()));
            sb.append(String.format("<td class=\"num\">%.1f</td>", v.reroutesMean()));
            sb.append("</tr>");
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String formatCell(double value, double bestValue) {
        boolean isBest = Math.abs(value - bestValue) < 0.001;
        String cssClass = isBest ? "num best" : "num";
        return String.format("<td class=\"%s\">%.1f</td>", cssClass, value);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
