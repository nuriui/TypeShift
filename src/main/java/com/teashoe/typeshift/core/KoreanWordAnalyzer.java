package com.teashoe.typeshift.core;

import java.util.Set;

public final class KoreanWordAnalyzer {
    private static final Set<String> SUFFIXES = Set.of("거", "건", "걸", "게", "도", "만", "은", "는", "이", "가",
        "을", "를", "에", "에서", "으로", "로", "와", "과", "까지", "부터", "처럼", "하고", "에게", "들");
    private final DictionaryLoader.Data data;
    public KoreanWordAnalyzer(DictionaryLoader.Data data) { this.data = data; }
    public double evidence(String word) {
        if (data.localKoreanWords().contains(word)) return 1.6;
        if (data.korean().containsKey(word) || word.length() < 3 || word.length() > 16) return 0;
        for (int split = 2; split < word.length(); split++) {
            String left = word.substring(0, split), right = word.substring(split);
            if (!known(left)) continue;
            if (SUFFIXES.contains(right)) return 0.9;
            if (right.length() >= 2 && known(right)) return 0.9;
        }
        return 0;
    }
    private boolean known(String word) {
        int rank = data.korean().getOrDefault(word, 0);
        return rank > 0 && rank <= 30000 || data.localKoreanWords().contains(word);
    }
}
