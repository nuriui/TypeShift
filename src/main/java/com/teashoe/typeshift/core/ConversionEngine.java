package com.teashoe.typeshift.core;

public final class ConversionEngine {
    public record Decision(String candidate, double margin, boolean convert) {}
    private final DubeolsikConverter converter = new DubeolsikConverter();
    private final DictionaryMatcher dictionary;
    private final NgramScorer scorer;
    private final DecisionPolicy policy = new DecisionPolicy();
    private final AutoLangConfig config;
    private final KoreanWordAnalyzer words;
    private final AttachedParticleMatcher particles;
    private final UserDictionary userDictionary;
    public ConversionEngine(DictionaryLoader.Data data, AutoLangConfig config) {
        this(data, config, new UserDictionary());
    }
    public ConversionEngine(DictionaryLoader.Data data, AutoLangConfig config, UserDictionary userDictionary) {
        this.userDictionary = userDictionary;
        words = new KoreanWordAnalyzer(data);
        particles = new AttachedParticleMatcher(data);
        dictionary = new DictionaryMatcher(data); scorer = new NgramScorer(data); this.config = config;
    }
    public Decision evaluate(String raw, boolean boundary) {
        return evaluate(raw, boundary, false);
    }
    public Decision evaluate(String raw, boolean boundary, boolean koreanContext) {
        return evaluate(raw, boundary, koreanContext, true);
    }
    Decision evaluate(String raw, boolean boundary, boolean koreanContext, boolean unknownBackoff) {
        String candidate = converter.convert(raw);
        double wordEvidence = boundary || dictionary.localKoreanWord(candidate) ? words.evidence(candidate) : 0;
        double margin = scorer.korean(candidate, raw.length(), boundary) + dictionary.koreanBonus(candidate)
            - scorer.english(raw, boundary) - dictionary.englishBonus(raw) + wordEvidence;
        double backoff = Double.NEGATIVE_INFINITY;
        if (unknownBackoff && boundary && dictionary.koreanRank(candidate) == 0 && dictionary.englishRank(raw) == 0
            && !dictionary.englishPrefix(raw))
            backoff = scorer.unknownWordMargin(raw, candidate);
        boolean statisticalEvidence = Double.isFinite(backoff);
        if (statisticalEvidence) margin = backoff + wordEvidence;
        boolean accepted = policy.accept(raw, candidate, margin, dictionary, scorer, config, boundary, koreanContext,
            wordEvidence > 0, statisticalEvidence);
        if (!accepted && !boundary && raw.length() >= 5 && raw.length() <= 64 && !dictionary.englishPrefix(raw)
            && !dictionary.protectedWord(raw) && scorer.commonRatio(candidate) >= 0.8) {
            String prefix = raw.substring(0, raw.length() - 1);
            String stem = converter.convert(prefix);
            if (dictionary.englishRank(prefix) > 0 && receivesFinalConsonant(stem, candidate)) {
                Decision completedStem = evaluate(prefix, true, koreanContext);
                accepted = completedStem.convert();
            }
        }
        return new Decision(candidate, margin, accepted);
    }
    private boolean receivesFinalConsonant(String stem, String candidate) {
        if (stem.length() < 2 || stem.length() != candidate.length()) return false;
        int last = stem.length() - 1;
        char base = stem.charAt(last), composed = candidate.charAt(last);
        return base >= 0xAC00 && base <= 0xD7A3 && (base - 0xAC00) % 28 == 0
            && composed > base && composed < base + 28
            && stem.regionMatches(0, candidate, 0, last);
    }
    public boolean prefersEnglish(String raw, boolean boundary) {
        if (!config.enabled() || raw.length() < (boundary ? 2 : 4)) return false;
        String korean = converter.convert(raw);
        if (dictionary.koreanRank(korean) > 0 || words.evidence(korean) > 0) return false;
        boolean completeHangul = korean.chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
        double margin = scorer.english(raw, boundary) + dictionary.englishBonus(raw) - scorer.korean(korean, raw.length(), boundary);
        int rank = dictionary.englishRank(raw);
        if (rank > 0) return !completeHangul || margin >= (rank <= 5000 ? 0.5 : rank <= 20000 ? 1.5 : 2.5);
        return boundary && raw.length() >= 6 && scorer.english(raw, boundary) >= -3.5 && margin >= 3.0;
    }
    public boolean plausibleComposition(String candidate, boolean boundary) {
        if (candidate.isEmpty()) return false;
        if (candidate.chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3)) return true;
        if (candidate.chars().allMatch(c -> c >= 0x3131 && c <= 0x314E)) return true;
        if (candidate.length() >= 2 && candidate.charAt(0) >= 0x314F && candidate.charAt(0) <= 0x3163
            && candidate.chars().allMatch(c -> c == candidate.charAt(0))) return true;
        if (boundary) return false;
        int last = candidate.length() - 1;
        return candidate.charAt(last) >= 0x3131 && candidate.charAt(last) <= 0x314E
            && candidate.substring(0, last).chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
    }
    public boolean isEnglishWord(String raw) { return dictionary.englishRank(raw) > 0; }
    public int englishRank(String raw) { return dictionary.englishRank(raw); }
    public boolean reconsiderBefore(String previous, String next) {
        String head = compose(previous), tail = compose(next);
        return java.util.Set.of("내", "네", "제", "그", "이", "저").contains(head)
            && dictionary.koreanRank(head) > 0 && dictionary.koreanRank(head) <= 200
            && tail.length() >= 2 && dictionary.koreanRank(tail) > 0
            && dictionary.englishRank(next) == 0 && evaluate(next, true).convert();
    }
    public boolean knownKorean(String raw) { return dictionary.koreanRank(converter.convert(raw)) > 0; }
    public java.util.Optional<AttachedParticleMatcher.Match> attachedParticle(String raw) { return particles.match(raw); }
    public java.util.Optional<AttachedParticleMatcher.Match> numericUnit(String raw) { return particles.numericUnit(raw); }
    public java.util.Optional<AttachedParticleMatcher.Match> fileExtension(String raw) { return particles.fileExtension(raw); }
    /** English stem immediately followed by Korean keys that are not a particle, e.g. build + 하는지가. */
    public java.util.Optional<AttachedParticleMatcher.Match> mixedSplit(String raw, boolean boundary) {
        if (raw.length() < 5 || raw.length() > 64 || !raw.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'))
            return java.util.Optional.empty();
        if (dictionary.englishRank(raw.toLowerCase(java.util.Locale.ROOT)) > 0 || dictionary.protectedWord(raw)
            || dictionary.koreanRank(converter.convert(raw)) > 0) return java.util.Optional.empty();
        for (int split = raw.length() - 2; split >= 3; split--) {
            String stem = raw.substring(0, split), tail = raw.substring(split);
            String korean = converter.convert(tail);
            if (korean.isEmpty() || korean.charAt(0) < 0xAC00 || korean.charAt(0) > 0xD7A3) continue;
            boolean single = korean.length() == 1;
            if (!particles.englishStem(stem, raw, single)) continue;
            if (boundary) {
                boolean hangul = korean.chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
                if (!hangul) continue;
                if (single) {
                    int rank = dictionary.koreanRank(korean);
                    if (rank == 0 || rank > 1000) continue;
                } else if (dictionary.koreanRank(korean) == 0 && words.evidence(korean) <= 0 && !evaluate(tail, true, true).convert()) continue;
            } else if (!plausibleComposition(korean, false)) continue;
            return java.util.Optional.of(new AttachedParticleMatcher.Match(stem + korean, split, boundary, false));
        }
        return java.util.Optional.empty();
    }
    public String compose(String raw) { return converter.convert(raw); }
    public boolean enabled() { return config.enabled(); }
    public UserDictionary userDictionary() { return userDictionary; }
    public String experimentalReverse(String committedText) {
        if (!config.experimentalReverse()) return committedText;
        String keys = converter.toKeys(committedText);
        return dictionary.englishRank(keys) > 0 && dictionary.koreanRank(committedText) == 0 ? keys : committedText;
    }
}
