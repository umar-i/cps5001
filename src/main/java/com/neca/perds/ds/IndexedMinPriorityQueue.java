package com.neca.perds.ds;

public interface IndexedMinPriorityQueue {
    boolean isEmpty();

    int size();

    boolean contains(int index);

    void insert(int index, double priority);

    int extractMin();

    void decreaseKey(int index, double newPriority);

    double priorityOf(int index);
}

