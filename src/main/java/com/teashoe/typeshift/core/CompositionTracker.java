package com.teashoe.typeshift.core;

public final class CompositionTracker {
    private boolean composing;
    private boolean awaitingCommit;
    public void preedit(boolean active) { awaitingCommit |= composing || active; composing = active; }
    public boolean acceptAscii(int codepoint) {
        boolean blocked = composing || awaitingCommit || codepoint > 127;
        if (!composing) awaitingCommit = false;
        return !blocked;
    }
    public boolean composing() { return composing; }
}
