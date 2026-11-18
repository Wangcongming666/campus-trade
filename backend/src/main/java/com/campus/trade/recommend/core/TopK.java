package com.campus.trade.recommend.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** 小顶堆求 TopK；分数相同按 ID 升序，保证结果确定。 */
final class TopK {

    static final Comparator<ScoredItem> DESC = (a, b) -> {
        int c = Double.compare(b.score(), a.score());
        return c != 0 ? c : Long.compare(a.itemId(), b.itemId());
    };

    private final int k;
    private final PriorityQueue<ScoredItem> heap;

    TopK(int k) {
        this.k = k;
        this.heap = new PriorityQueue<>(Math.max(1, k), DESC.reversed());
    }

    void offer(long id, double score) {
        if (k <= 0) {
            return;
        }
        if (heap.size() < k) {
            heap.add(new ScoredItem(id, score));
        } else {
            ScoredItem worst = heap.peek();
            if (score > worst.score() || (score == worst.score() && id < worst.itemId())) {
                heap.poll();
                heap.add(new ScoredItem(id, score));
            }
        }
    }

    List<ScoredItem> sorted() {
        List<ScoredItem> list = new ArrayList<>(heap);
        list.sort(DESC);
        return list;
    }
}
