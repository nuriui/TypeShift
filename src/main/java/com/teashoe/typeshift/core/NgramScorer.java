package com.teashoe.typeshift.core;

import java.util.*;

public final class NgramScorer {
    private final DictionaryLoader.Data data;
    private final Map<String, Integer> syllableSupport = new HashMap<>();
    private int totalSupport;
    public NgramScorer(DictionaryLoader.Data data) {
        this.data = data;
        data.korean().forEach((word, rank) -> {
            if (rank <= 20000) word.chars().distinct().filter(c -> c >= 0xAC00 && c <= 0xD7A3)
                .forEach(c -> syllableSupport.merge(String.valueOf((char) c), 1, Integer::sum));
        });
        totalSupport = syllableSupport.values().stream().mapToInt(Integer::intValue).sum();
    }
    public double unknownWordMargin(String raw, String korean) {
        if (raw.length() < 5 || korean.length() < 2
            || !korean.chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3)
            || english(raw, true) * raw.length() / (raw.length() + 1) > -4.5) return Double.NEGATIVE_INFINITY;
        String bounded = "^" + korean + "$";
        double score = 0;
        for (int i = 0; i < bounded.length() - 1; i++) {
            String left = bounded.substring(i, i + 1), right = bounded.substring(i + 1, i + 2);
            Integer pair = data.koreanPairs().get(left + right);
            double transition = pair == null
                ? data.koreanUnseen().getOrDefault(left, data.unknown().get("korean")) / 100.0 : pair / 100.0;
            if (pair == null && i > 0 && i < korean.length()) {
                int support = syllableSupport.getOrDefault(right, 0);
                if (support >= 20)
                    transition = Math.max(transition, Math.log(support / (double) totalSupport));
            }
            score += transition;
        }
        double unseenCoverage = korean.chars().filter(c -> data.koreanUnseen().containsKey(String.valueOf((char) c))).count()
            / (double) korean.length();
        return score / raw.length() - english(raw, true)
            - 0.3 * (1 - commonRatio(korean)) - 2.0 * (1 - unseenCoverage);
    }
    public double korean(String word, int keyCount, boolean complete) {
        if (keyCount <= 0) return 0;
        String bounded = "^" + word + (complete ? "$" : "");
        double score = 0;
        for (int i = 0; i < bounded.length() - 1; i++) {
            String context = bounded.substring(i, i + 1);
            score += data.koreanPairs().getOrDefault(bounded.substring(i, i + 2),
                data.koreanUnseen().getOrDefault(context, data.unknown().get("korean")));
        }
        return score / (keyCount * 100.0);
    }
    public double english(String word, boolean complete) {
        if (word.isEmpty()) return 0;
        String bounded = "^^" + word.toLowerCase(Locale.ROOT) + (complete ? "$" : "");
        double score = 0;
        for (int i = 0; i < bounded.length() - 2; i++) {
            String context = bounded.substring(i, i + 2);
            score += data.englishTriples().getOrDefault(bounded.substring(i, i + 3),
                data.englishUnseen().getOrDefault(context, data.unknown().get("english")));
        }
        return score / (word.length() * 100.0);
    }
    public double commonRatio(String word) {
        return word.chars().filter(c -> data.commonSyllables().contains(String.valueOf((char) c))).count() / (double) Math.max(1, word.length());
    }
}
