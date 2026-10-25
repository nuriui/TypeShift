package com.teashoe.typeshift.core;

import java.util.*;

public final class ReplacementManager {
    public record Snapshot(String text, int cursor, int anchor) {
        public Snapshot {
            if (cursor < 0 || anchor < 0 || cursor > text.length() || anchor > text.length()) throw new IllegalArgumentException("Invalid selection");
        }
    }
    public record Edit(int start, int end, String replacement, Snapshot result) {}
    public record ConversionRecord(int start, String raw, String displayed, Snapshot before, Snapshot after) {}
    private final Deque<ConversionRecord> history = new ArrayDeque<>();
    private boolean modifying;
    public boolean modifying() { return modifying; }
    public void guarded(Runnable action) {
        if (modifying) return;
        modifying = true;
        try { action.run(); } finally { modifying = false; }
    }
    public Edit replace(Snapshot state, int start, int end, String replacement) {
        if (start < 0 || end < start || end > state.text().length()) throw new IllegalArgumentException("Invalid range");
        String text = state.text().substring(0, start) + replacement + state.text().substring(end);
        return new Edit(start, end, replacement, new Snapshot(text,
            map(state.cursor(), start, end, replacement.length()), map(state.anchor(), start, end, replacement.length())));
    }
    private int map(int position, int start, int end, int length) {
        if (position < start) return position;
        if (position >= end) return position + length - (end - start);
        return start + Math.min(position - start, length);
    }
    public void record(ConversionRecord record) {
        history.addLast(record);
        while (history.size() > 32) history.removeFirst();
    }
    public List<ConversionRecord> history() { return List.copyOf(history); }
}
