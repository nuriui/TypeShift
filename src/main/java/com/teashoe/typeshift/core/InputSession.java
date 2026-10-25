package com.teashoe.typeshift.core;

public final class InputSession {
    private final int start;
    private String raw = "";
    private String displayed = "";
    private boolean converted;
    private int literalPrefixLength;
    public InputSession(int start) { this.start = start; }
    public int literalPrefixLength() { return literalPrefixLength; }
    public void literalPrefixLength(int length) { literalPrefixLength = length; }
    public int start() { return start; }
    public String raw() { return raw; }
    public String displayed() { return displayed; }
    public boolean converted() { return converted; }
    public void append(char c) { raw += c; }
    public void backspace() { if (!raw.isEmpty()) raw = raw.substring(0, raw.length() - 1); }
    public void display(String value, boolean converted) { displayed = value; this.converted = converted; }
    public boolean matches(ReplacementManager.Snapshot state) {
        return state.cursor() == state.anchor() && state.cursor() == start + displayed.length()
            && start + displayed.length() <= state.text().length()
            && state.text().regionMatches(start, displayed, 0, displayed.length());
    }
}
