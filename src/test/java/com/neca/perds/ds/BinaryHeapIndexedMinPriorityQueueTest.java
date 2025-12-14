package com.neca.perds.ds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BinaryHeapIndexedMinPriorityQueueTest {
    @Test
    void insertAndExtractMin_ordersByPriority() {
        var pq = new BinaryHeapIndexedMinPriorityQueue(10);

        pq.insert(3, 3.0);
        pq.insert(1, 1.0);
        pq.insert(2, 2.0);

        assertEquals(3, pq.size());
        assertEquals(1, pq.extractMin());
        assertEquals(2, pq.extractMin());
        assertEquals(3, pq.extractMin());
        assertTrue(pq.isEmpty());
    }

    @Test
    void decreaseKey_updatesPriorityAndOrder() {
        var pq = new BinaryHeapIndexedMinPriorityQueue(10);

        pq.insert(1, 5.0);
        pq.insert(2, 2.0);
        pq.insert(3, 3.0);

        pq.decreaseKey(1, 1.0);

        assertEquals(1.0, pq.priorityOf(1), 1e-9);
        assertEquals(1, pq.extractMin());
        assertEquals(2, pq.extractMin());
        assertEquals(3, pq.extractMin());
    }

    @Test
    void containsReflectsInsertAndExtract() {
        var pq = new BinaryHeapIndexedMinPriorityQueue(10);

        pq.insert(7, 1.0);
        assertTrue(pq.contains(7));

        int min = pq.extractMin();
        assertEquals(7, min);
        assertFalse(pq.contains(7));
    }
}
