package com.neca.perds.ds;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BinaryHeapIndexedMinPriorityQueuePropertyTest {
    private static final double PRIORITY_EPSILON = 1e-12;

    @Test
    void randomOperations_matchReferenceModel() {
        long seed = 638_578_610_526_494_859L;
        var random = new Random(seed);

        int maxIndex = 500;
        int operations = 20_000;

        var pq = new BinaryHeapIndexedMinPriorityQueue(8);
        Map<Integer, Double> model = new HashMap<>();

        for (int step = 0; step < operations; step++) {
            int stepIndex = step;
            int action = chooseAction(random, model.isEmpty());
            switch (action) {
                case 0 -> insertRandom(random, pq, model, maxIndex);
                case 1 -> decreaseRandom(random, pq, model);
                case 2 -> extractAndAssertMin(pq, model, seed, stepIndex);
                default -> throw new IllegalStateException("Unexpected action: " + action);
            }

            assertEquals(model.size(), pq.size(), () -> debug(seed, stepIndex) + " size mismatch");
            assertEquals(model.isEmpty(), pq.isEmpty(), () -> debug(seed, stepIndex) + " isEmpty mismatch");
            assertContainsAndPriorityAgreement(random, pq, model, maxIndex, seed, stepIndex);
        }

        while (!model.isEmpty()) {
            extractAndAssertMin(pq, model, seed, operations);
        }
        assertTrue(pq.isEmpty());
    }

    private static int chooseAction(Random random, boolean empty) {
        if (empty) {
            return 0;
        }

        int roll = random.nextInt(100);
        if (roll < 45) {
            return 0;
        }
        if (roll < 75) {
            return 1;
        }
        return 2;
    }

    private static void insertRandom(
            Random random,
            BinaryHeapIndexedMinPriorityQueue pq,
            Map<Integer, Double> model,
            int maxIndex
    ) {
        if (model.size() >= maxIndex + 1) {
            return;
        }

        int index = randomAbsentIndex(random, model, maxIndex);
        double priority = randomPriority(random);
        pq.insert(index, priority);
        model.put(index, priority);
        assertTrue(pq.contains(index));
        assertEquals(priority, pq.priorityOf(index), PRIORITY_EPSILON);
    }

    private static void decreaseRandom(
            Random random,
            BinaryHeapIndexedMinPriorityQueue pq,
            Map<Integer, Double> model
    ) {
        if (model.isEmpty()) {
            return;
        }

        int index = randomPresentIndex(random, model);
        double current = model.get(index);
        double next = current - random.nextDouble() * 250.0;
        pq.decreaseKey(index, next);
        model.put(index, next);
        assertEquals(next, pq.priorityOf(index), PRIORITY_EPSILON);
    }

    private static void extractAndAssertMin(
            BinaryHeapIndexedMinPriorityQueue pq,
            Map<Integer, Double> model,
            long seed,
            int step
    ) {
        if (model.isEmpty()) {
            return;
        }

        double minPriority = minPriority(model);
        int extracted = pq.extractMin();
        Double extractedPriority = model.remove(extracted);
        assertTrue(extractedPriority != null, () -> debug(seed, step) + " extracted index not in model: " + extracted);
        assertEquals(minPriority, extractedPriority, PRIORITY_EPSILON, () -> debug(seed, step) + " extracted wrong min");
        assertFalse(pq.contains(extracted), () -> debug(seed, step) + " extracted index should not be present: " + extracted);
    }

    private static void assertContainsAndPriorityAgreement(
            Random random,
            BinaryHeapIndexedMinPriorityQueue pq,
            Map<Integer, Double> model,
            int maxIndex,
            long seed,
            int step
    ) {
        int samples = 6;
        for (int i = 0; i < samples; i++) {
            int index = random.nextInt(maxIndex + 1);
            boolean inModel = model.containsKey(index);
            assertEquals(inModel, pq.contains(index), () -> debug(seed, step) + " contains mismatch for index " + index);
            if (inModel) {
                assertEquals(model.get(index), pq.priorityOf(index), PRIORITY_EPSILON, () -> debug(seed, step) + " priority mismatch for index " + index);
            }
        }
    }

    private static int randomAbsentIndex(Random random, Map<Integer, Double> model, int maxIndex) {
        for (int tries = 0; tries < 50; tries++) {
            int index = random.nextInt(maxIndex + 1);
            if (!model.containsKey(index)) {
                return index;
            }
        }

        for (int index = 0; index <= maxIndex; index++) {
            if (!model.containsKey(index)) {
                return index;
            }
        }
        throw new IllegalStateException("No absent indices available");
    }

    private static int randomPresentIndex(Random random, Map<Integer, Double> model) {
        int pick = random.nextInt(model.size());
        int i = 0;
        for (int index : model.keySet()) {
            if (i == pick) {
                return index;
            }
            i++;
        }
        throw new IllegalStateException("Model unexpectedly empty");
    }

    private static double randomPriority(Random random) {
        return random.nextDouble() * 2_000.0 - 1_000.0;
    }

    private static double minPriority(Map<Integer, Double> model) {
        double min = Double.POSITIVE_INFINITY;
        for (double priority : model.values()) {
            if (priority < min) {
                min = priority;
            }
        }
        return min;
    }

    private static String debug(long seed, int step) {
        return "seed=" + seed + " step=" + step;
    }
}
