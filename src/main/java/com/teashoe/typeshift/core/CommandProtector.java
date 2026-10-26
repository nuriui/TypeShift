package com.teashoe.typeshift.core;

import java.util.*;
import java.util.regex.*;

public final class CommandProtector {
    private final Set<String> local;
    private Set<String> players = Set.of();
    public CommandProtector(Set<String> local) { this.local = local; }
    public void setPlayers(Collection<String> names) {
        Set<String> lower = new HashSet<>();
        for (String name : names) lower.add(name.toLowerCase(Locale.ROOT));
        players = Set.copyOf(lower);
    }
    public boolean protectedRange(String text, int start, int end) {
        return protectedRange(text, start, end, false);
    }
    public boolean protectedRange(String text, int start, int end, boolean numericSuffix) {
        return protectedRange(text, start, end, numericSuffix, false);
    }
    public boolean protectedRange(String text, int start, int end, boolean numericSuffix, boolean versionSuffix) {
        return protectedRange(text, start, end, numericSuffix, versionSuffix, false);
    }
    public boolean protectedCompletedRange(String text, int start, int end) {
        return protectedRange(text, start, end, false, false, true);
    }
    private boolean protectedRange(String text, int start, int end, boolean numericSuffix, boolean versionSuffix, boolean complete) {
        if (start < messageStart(text)) return true;
        int left = start, right = end;
        while (left > 0 && !Character.isWhitespace(text.charAt(left - 1)) && text.charAt(left - 1) != '"') left--;
        while (right < text.length() && !Character.isWhitespace(text.charAt(right)) && text.charAt(right) != '"') right++;
        String token = text.substring(left, right).toLowerCase(Locale.ROOT);
        boolean version = versionSuffix && numericSuffix && token.matches("[0-9]+(?:\\.[0-9]+)+[a-z]+[!?;,]?");
        if (token.chars().anyMatch(c -> (!numericSuffix && Character.isDigit(c))
            || ":/\\@_~".indexOf(c) >= 0 || c == '.' && !version)) return true;
        for (String word : local) if (!token.isEmpty() && (complete ? word.equals(token) : word.startsWith(token))) return true;
        for (String name : players) if (!token.isEmpty() && name.startsWith(token)) return true;
        return false;
    }
    private int messageStart(String text) {
        if (!text.startsWith("/")) return 0;
        Matcher matcher = Pattern.compile("\\S+").matcher(text);
        List<Integer> ends = new ArrayList<>();
        List<String> words = new ArrayList<>();
        while (matcher.find()) { words.add(matcher.group()); ends.add(matcher.end()); }
        if (words.isEmpty()) return Integer.MAX_VALUE;
        int args = switch (words.getFirst().toLowerCase(Locale.ROOT)) {
            case "/msg", "/tell", "/w" -> 2;
            case "/say", "/me", "/teammsg", "/tm" -> 1;
            default -> Integer.MAX_VALUE;
        };
        if (ends.size() < args || args == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return ends.get(args - 1) + 1;
    }
}
