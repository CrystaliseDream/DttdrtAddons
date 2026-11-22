package com.dttdrt.addons.features.mining.profit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * In-flight bookkeeping for inventory and sack capture deduplication:
 * - holds: increases seen while a non-mining menu is open waiting for container catch-up
 * - removals: recent item decreases used to cancel drop-pickup or Personal Compactor crafting
 * - skipped: withdrawals ignored inside a Sack screen, matched by the subsequent [Sacks] - line
 */
public class CaptureBuffer {
    public record KeyAmount(String key, int amount) {}

    public interface CompactionResolver {
        Compaction resolve(String key);
    }

    public record Compaction(String parentKey, int ratio) {}

    private static final class Bucket {
        int amount;
        long atMs;

        Bucket(int amount, long atMs) {
            this.amount = amount;
            this.atMs = atMs;
        }
    }

    private final long pendingIncreaseMs;
    private final long removalCreditMs;

    private final Map<String, Bucket> holds = new HashMap<>();
    private final Map<String, Bucket> removals = new HashMap<>();
    private final Map<String, Integer> skipped = new HashMap<>();

    public CaptureBuffer(long pendingIncreaseMs, long removalCreditMs) {
        this.pendingIncreaseMs = pendingIncreaseMs;
        this.removalCreditMs = removalCreditMs;
    }

    public synchronized void clear() {
        holds.clear();
        removals.clear();
        skipped.clear();
    }

    public synchronized void clearHoldsAndSkipped() {
        holds.clear();
        skipped.clear();
    }

    public synchronized void hold(String key, int amount, long now) {
        if (key == null || amount <= 0) return;
        Bucket existing = holds.get(key);
        if (existing == null) {
            holds.put(key, new Bucket(amount, now));
        } else {
            existing.amount += amount;
            existing.atMs = now;
        }
    }

    public synchronized void explain(String key, int amount) {
        if (key == null || amount <= 0) return;
        Bucket existing = holds.get(key);
        if (existing == null) return;
        existing.amount -= amount;
        if (existing.amount <= 0) {
            holds.remove(key);
        }
    }

    public synchronized int cancelHold(String key, int amount) {
        if (key == null || amount <= 0) return 0;
        Bucket existing = holds.get(key);
        if (existing == null) return 0;
        int cancelled = Math.min(existing.amount, amount);
        existing.amount -= cancelled;
        if (existing.amount <= 0) {
            holds.remove(key);
        }
        return cancelled;
    }

    public synchronized List<KeyAmount> dueHolds(long now) {
        if (holds.isEmpty()) return List.of();
        List<KeyAmount> due = new ArrayList<>();
        Iterator<Map.Entry<String, Bucket>> it = holds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bucket> entry = it.next();
            if (now - entry.getValue().atMs >= pendingIncreaseMs) {
                due.add(new KeyAmount(entry.getKey(), entry.getValue().amount));
                it.remove();
            }
        }
        return due;
    }

    public synchronized void addRemoval(String key, int amount, long now) {
        if (key == null || amount <= 0) return;
        Bucket existing = removals.get(key);
        if (existing == null || now - existing.atMs > removalCreditMs) {
            removals.put(key, new Bucket(amount, now));
        } else {
            existing.amount += amount;
            existing.atMs = now;
        }
    }

    private int takeRemoval(String key, int upTo, long now) {
        Bucket existing = removals.get(key);
        if (existing == null) return 0;
        if (now - existing.atMs > removalCreditMs) {
            removals.remove(key);
            return 0;
        }
        int consumed = Math.min(existing.amount, upTo);
        existing.amount -= consumed;
        if (existing.amount <= 0) {
            removals.remove(key);
        }
        return consumed;
    }

    private boolean takeRemovalIfEnough(String key, long needed, long now) {
        Bucket existing = removals.get(key);
        if (existing == null) return false;
        if (now - existing.atMs > removalCreditMs) {
            removals.remove(key);
            return false;
        }
        if (existing.amount < needed) return false;
        existing.amount -= (int) needed;
        if (existing.amount <= 0) {
            removals.remove(key);
        }
        return true;
    }

    public synchronized int takeCredit(String key, int amount, long now, CompactionResolver resolver) {
        if (key == null || amount <= 0) return 0;
        int remaining = amount - takeRemoval(key, amount, now);
        if (remaining <= 0) return 0;
        if (resolver == null) return remaining;

        Compaction comp = resolver.resolve(key);
        if (comp == null || comp.parentKey() == null || comp.ratio() <= 0) {
            return remaining;
        }
        long needed = (long) comp.ratio() * remaining;
        return takeRemovalIfEnough(comp.parentKey(), needed, now) ? 0 : remaining;
    }

    public synchronized void noteSkipped(String key, int amount) {
        if (key == null || amount <= 0) return;
        skipped.merge(key, amount, Integer::sum);
    }

    public synchronized int takeSkipped(String key, int amount) {
        if (key == null || amount <= 0) return 0;
        Integer existing = skipped.get(key);
        if (existing == null) return 0;
        int consumed = Math.min(existing, amount);
        if (existing == consumed) {
            skipped.remove(key);
        } else {
            skipped.put(key, existing - consumed);
        }
        return consumed;
    }
}
