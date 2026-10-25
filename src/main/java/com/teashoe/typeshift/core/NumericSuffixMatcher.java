package com.teashoe.typeshift.core;

import java.util.HashSet;
import java.util.Set;

public final class NumericSuffixMatcher {
    public enum Match { NONE, PREFIX, COMPLETE }
    private final Set<String> complete = new HashSet<>();
    private final Set<String> prefixes = new HashSet<>();
    private final Set<String> versionComplete = new HashSet<>();
    private final Set<String> versionPrefixes = new HashSet<>();
    public NumericSuffixMatcher() {
        var converter = new DubeolsikConverter();
        for (String unit : Set.of("년", "월", "일", "시", "분", "초", "버전")) {
            for (String ending : Set.of("", "에", "에는", "부터", "까지", "인데", "이고", "입니다", "이야",
                "쯤", "동안", "도", "만", "은", "을", "이", "의", "마다", "으로", "이라고")) {
                String keys = converter.toKeys(unit + ending);
                add(complete, prefixes, keys);
                if (unit.equals("버전")) add(versionComplete, versionPrefixes, keys);
            }
        }
    }
    private void add(Set<String> words, Set<String> starts, String keys) {
        words.add(keys);
        for (int i = 1; i <= keys.length(); i++) starts.add(keys.substring(0, i));
    }
    public Match match(String keys) { return match(keys, false); }
    public Match match(String keys, boolean version) {
        if ((version ? versionComplete : complete).contains(keys)) return Match.COMPLETE;
        return (version ? versionPrefixes : prefixes).contains(keys) ? Match.PREFIX : Match.NONE;
    }
}
