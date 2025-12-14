package com.neca.perds.ds;

import java.util.Arrays;

public final class BinaryHeapIndexedMinPriorityQueue implements IndexedMinPriorityQueue {
    private static final int NOT_PRESENT = -1;

    private int size;
    private int[] heap;
    private int[] positions;
    private double[] priorities;

    public BinaryHeapIndexedMinPriorityQueue() {
        this(16);
    }

    public BinaryHeapIndexedMinPriorityQueue(int maxIndex) {
        if (maxIndex < 0) {
            throw new IllegalArgumentException("maxIndex must be >= 0");
        }
        heap = new int[Math.max(2, maxIndex + 2)];
        positions = new int[Math.max(1, maxIndex + 1)];
        priorities = new double[Math.max(1, maxIndex + 1)];
        Arrays.fill(positions, NOT_PRESENT);
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean contains(int index) {
        requireValidIndex(index);
        return index < positions.length && positions[index] != NOT_PRESENT;
    }

    @Override
    public void insert(int index, double priority) {
        requireValidIndex(index);
        requireValidPriority(priority);

        if (contains(index)) {
            throw new IllegalStateException("Index already present: " + index);
        }

        ensureIndexCapacity(index);
        ensureHeapCapacity(size + 1);

        size++;
        heap[size] = index;
        positions[index] = size;
        priorities[index] = priority;
        swim(size);
    }

    @Override
    public int extractMin() {
        if (size == 0) {
            throw new IllegalStateException("Queue is empty");
        }

        int minIndex = heap[1];
        swap(1, size);
        size--;
        positions[minIndex] = NOT_PRESENT;

        if (size > 0) {
            sink(1);
        }

        return minIndex;
    }

    @Override
    public void decreaseKey(int index, double newPriority) {
        requireValidIndex(index);
        requireValidPriority(newPriority);

        if (!contains(index)) {
            throw new IllegalStateException("Index not present: " + index);
        }

        double current = priorities[index];
        if (newPriority > current) {
            throw new IllegalArgumentException("newPriority must be <= current priority");
        }

        priorities[index] = newPriority;
        swim(positions[index]);
    }

    @Override
    public double priorityOf(int index) {
        requireValidIndex(index);
        if (!contains(index)) {
            throw new IllegalStateException("Index not present: " + index);
        }
        return priorities[index];
    }

    private void swim(int heapPosition) {
        int k = heapPosition;
        while (k > 1) {
            int parent = k / 2;
            if (priorityAt(k) >= priorityAt(parent)) {
                break;
            }
            swap(k, parent);
            k = parent;
        }
    }

    private void sink(int heapPosition) {
        int k = heapPosition;
        while (2 * k <= size) {
            int child = 2 * k;
            if (child < size && priorityAt(child + 1) < priorityAt(child)) {
                child++;
            }
            if (priorityAt(k) <= priorityAt(child)) {
                break;
            }
            swap(k, child);
            k = child;
        }
    }

    private double priorityAt(int heapPosition) {
        return priorities[heap[heapPosition]];
    }

    private void swap(int a, int b) {
        int indexA = heap[a];
        int indexB = heap[b];
        heap[a] = indexB;
        heap[b] = indexA;
        positions[indexA] = b;
        positions[indexB] = a;
    }

    private void ensureIndexCapacity(int index) {
        if (index < positions.length) {
            return;
        }

        int newLength = Math.max(index + 1, positions.length * 2);
        int[] newPositions = Arrays.copyOf(positions, newLength);
        Arrays.fill(newPositions, positions.length, newLength, NOT_PRESENT);
        positions = newPositions;
        priorities = Arrays.copyOf(priorities, newLength);
    }

    private void ensureHeapCapacity(int requiredSize) {
        int requiredLength = requiredSize + 1;
        if (requiredLength < heap.length) {
            return;
        }
        heap = Arrays.copyOf(heap, Math.max(requiredLength, heap.length * 2));
    }

    private static void requireValidIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
    }

    private static void requireValidPriority(double priority) {
        if (Double.isNaN(priority)) {
            throw new IllegalArgumentException("priority must not be NaN");
        }
    }
}
