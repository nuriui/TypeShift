package com.teashoe.typeshift.core;

import java.util.*;

public final class DictionaryMatcher {
    private final DictionaryLoader.Data data;
    private final NavigableSet<String> englishPrefixes;
    public DictionaryMatcher(DictionaryLoader.Data data) {
        this.data = data;
        englishPrefixes = new TreeSet<>(data.english().keySet());
        englishPrefixes.addAll(data.protectedWords());
    }
    public boolean localKoreanWord(String word) { return data.localKoreanWords().contains(word); }
    public int koreanRank(String word) { return data.korean().getOrDefault(word, 0); }
    public int englishRank(String word) { return data.english().getOrDefault(word.toLowerCase(Locale.ROOT), 0); }
    public boolean englishPrefix(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        String next = englishPrefixes.ceiling(lower);
        return next != null && next.startsWith(lower);
    }
    public boolean protectedWord(String word) { return data.protectedWords().contains(word.toLowerCase(Locale.ROOT)); }
    public double koreanBonus(String word) { return bonus(koreanRank(word)); }
    public double englishBonus(String word) {
        double bonus = bonus(englishRank(word));
        return data.subtitleOnly().contains(word.toLowerCase(Locale.ROOT)) ? bonus * 0.7 : bonus;
    }
    private double bonus(int rank) { return rank == 0 ? 0 : Math.max(0.25, 1.8 - Math.log10(rank) * 0.3); }
}
