package com.neca.perds.io;

import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Graph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.GeoPoint;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class CsvGraphLoader {
    public Graph load(Path nodesCsv, Path edgesCsv) throws IOException {
        Objects.requireNonNull(nodesCsv, "nodesCsv");
        Objects.requireNonNull(edgesCsv, "edgesCsv");

        var graph = new AdjacencyMapGraph();
        loadNodes(nodesCsv, graph);
        loadEdges(edgesCsv, graph);
        return graph;
    }

    private static void loadNodes(Path nodesCsv, AdjacencyMapGraph graph) throws IOException {
        try (var reader = Files.newBufferedReader(nodesCsv)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (shouldSkip(line)) {
                    continue;
                }

                var fields = CsvUtils.splitLine(line);
                if (looksLikeHeader(fields, "id", "type", "label")) {
                    continue;
                }
                if (fields.size() < 3) {
                    throw new IllegalArgumentException("nodes.csv line " + lineNo + ": expected >= 3 columns");
                }

                NodeId id = new NodeId(fields.get(0).trim());
                NodeType type = NodeType.valueOf(fields.get(1).trim().toUpperCase(Locale.ROOT));
                String label = fields.get(2).trim();

                Optional<GeoPoint> point = Optional.empty();
                if (fields.size() >= 5) {
                    String x = fields.get(3).trim();
                    String y = fields.get(4).trim();
                    if (!x.isBlank() || !y.isBlank()) {
                        if (x.isBlank() || y.isBlank()) {
                            throw new IllegalArgumentException("nodes.csv line " + lineNo + ": x and y must both be present");
                        }
                        point = Optional.of(new GeoPoint(Double.parseDouble(x), Double.parseDouble(y)));
                    }
                }

                graph.addNode(new Node(id, type, point, label));
            }
        }
    }

    private static void loadEdges(Path edgesCsv, AdjacencyMapGraph graph) throws IOException {
        try (var reader = Files.newBufferedReader(edgesCsv)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (shouldSkip(line)) {
                    continue;
                }

                var fields = CsvUtils.splitLine(line);
                if (looksLikeHeader(fields, "from", "to", "distancekm")) {
                    continue;
                }
                if (fields.size() < 6) {
                    throw new IllegalArgumentException("edges.csv line " + lineNo + ": expected 6 columns");
                }

                NodeId from = new NodeId(fields.get(0).trim());
                NodeId to = new NodeId(fields.get(1).trim());
                if (graph.getNode(from).isEmpty() || graph.getNode(to).isEmpty()) {
                    throw new IllegalArgumentException("edges.csv line " + lineNo + ": unknown node in edge " + from + " -> " + to);
                }

                double distanceKm = Double.parseDouble(fields.get(2).trim());
                long travelTimeSeconds = Long.parseLong(fields.get(3).trim());
                double resourceAvailability = Double.parseDouble(fields.get(4).trim());
                EdgeStatus status = EdgeStatus.valueOf(fields.get(5).trim().toUpperCase(Locale.ROOT));

                EdgeWeights weights = new EdgeWeights(distanceKm, Duration.ofSeconds(travelTimeSeconds), resourceAvailability);
                graph.putEdge(new Edge(from, to, weights, status));
            }
        }
    }

    private static boolean shouldSkip(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static boolean looksLikeHeader(java.util.List<String> fields, String... tokensLowercase) {
        if (fields.isEmpty()) {
            return false;
        }
        String first = fields.getFirst().trim().toLowerCase(Locale.ROOT);
        for (String token : tokensLowercase) {
            if (first.equals(token)) {
                return true;
            }
        }
        return false;
    }
}
