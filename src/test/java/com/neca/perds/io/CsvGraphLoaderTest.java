package com.neca.perds.io;

import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.model.NodeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CsvGraphLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsNodesAndEdges() throws Exception {
        Path nodes = tempDir.resolve("nodes.csv");
        Path edges = tempDir.resolve("edges.csv");

        Files.writeString(nodes, """
                id,type,label,x,y
                A,CITY,City A,,
                B,CITY,City B,,
                C,CITY,City C,,
                """);
        Files.writeString(edges, """
                from,to,distanceKm,travelTimeSeconds,resourceAvailability,status
                A,B,5,300,1,OPEN
                B,C,5,300,1,OPEN
                A,C,20,1200,1,OPEN
                """);

        var graph = new CsvGraphLoader().load(nodes, edges);
        assertEquals(3, graph.nodeIds().size());

        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");

        var edge = graph.getEdge(a, b).orElseThrow();
        assertEquals(EdgeStatus.OPEN, edge.status());
        assertEquals(5.0, edge.weights().distanceKm(), 1e-9);
        assertEquals(Duration.ofSeconds(300), edge.weights().travelTime());
    }

    @Test
    void rejectsEdgesThatReferenceUnknownNodes() throws Exception {
        Path nodes = tempDir.resolve("nodes.csv");
        Path edges = tempDir.resolve("edges.csv");

        Files.writeString(nodes, """
                id,type,label,x,y
                A,CITY,City A,,
                """);
        Files.writeString(edges, """
                from,to,distanceKm,travelTimeSeconds,resourceAvailability,status
                A,B,5,300,1,OPEN
                """);

        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new CsvGraphLoader().load(nodes, edges)
        ).getMessage().contains("unknown node"));
    }
}

