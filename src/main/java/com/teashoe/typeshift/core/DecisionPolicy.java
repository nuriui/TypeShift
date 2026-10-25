package com.teashoe.typeshift.core;

public final class DecisionPolicy {
    public boolean accept(String raw, String korean, double margin, DictionaryMatcher dictionary,
                          NgramScorer scorer, AutoLangConfig config, boolean boundary, boolean koreanContext,
                          boolean wordEvidence, boolean statisticalEvidence) {
        boolean shortWord = boundary && koreanContext && raw.length() >= 3 && korean.length() == 1
            && dictionary.koreanRank(korean) > 0 && dictionary.koreanRank(korean) <= 1000;
        if (!config.enabled() || raw.length() > 64) return false;
        if (!shortWord && (raw.length() < 4 || korean.length() < 2)) return false;
        if (!korean.chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3)) return false;
        if (dictionary.protectedWord(raw)) return false;
        if (!boundary && dictionary.englishRank(raw) > 0) return false;
        if (!boundary && dictionary.englishPrefix(raw)) return false;
        if (!boundary && dictionary.koreanRank(korean) == 0 && !wordEvidence
            && (korean.length() < 5 || margin < 4.5)) return false;
        double threshold = 2.8 - 2.0 * config.sensitivity() - (boundary ? 0.4 : 0);
        if (boundary && dictionary.localKoreanWord(korean)) threshold -= 0.6;
        if (dictionary.koreanRank(korean) == 0 && !wordEvidence) threshold += 1.0;
        if (boundary && dictionary.koreanRank(korean) > 0 && dictionary.koreanRank(korean) <= 200) threshold -= 0.2;
        if (boundary && koreanContext) {
            threshold -= shortWord ? 0.9 : 0.6;
            if (dictionary.koreanRank(korean) == 0 && !wordEvidence && korean.length() >= 3) threshold -= 0.6;
        }
        return (scorer.commonRatio(korean) >= 0.8 || boundary && statisticalEvidence) && margin >= threshold;
    }
}
