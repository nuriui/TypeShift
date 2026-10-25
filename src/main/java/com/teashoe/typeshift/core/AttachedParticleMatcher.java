package com.teashoe.typeshift.core;

import java.util.*;

public final class AttachedParticleMatcher {
    public record Match(String candidate, int prefixLength, boolean complete) {}
    private final DictionaryLoader.Data data;
    private final DubeolsikConverter converter = new DubeolsikConverter();
    private final Set<String> complete = new HashSet<>();
    private final Set<String> prefixes = new HashSet<>();
    public AttachedParticleMatcher(DictionaryLoader.Data data) {
        this.data = data;
        for (String particle : Set.of("은", "는", "이", "가", "을", "를", "에", "에서", "로", "으로", "와", "과",
            "도", "만", "한테", "에게", "까지", "부터", "처럼", "보다", "랑", "이랑", "하고")) {
            String keys = converter.toKeys(particle);
            complete.add(keys);
            for (int i = 1; i <= keys.length(); i++) prefixes.add(keys.substring(0, i));
        }
    }
    public Optional<Match> match(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (data.korean().containsKey(converter.convert(raw))) return Optional.empty();
        if (data.english().containsKey(lower) || data.protectedWords().contains(lower)) return Optional.empty();
        for (int split = raw.length() - 1; split >= 2; split--) {
            String base = raw.substring(0, split).toLowerCase(Locale.ROOT);
            int rank = data.english().getOrDefault(base, 0);
            if (!data.protectedWords().contains(base) && !(rank > 0 && rank <= 20000)
                && !compoundEnglish(base)) continue;
            String suffix = raw.substring(split);
            if (prefixes.contains(suffix))
                return Optional.of(new Match(raw.substring(0, split) + converter.convert(suffix), split, complete.contains(suffix)));
        }
        return Optional.empty();
    }
    private boolean compoundEnglish(String base) {
        if (base.length() < 6 || base.length() > 32) return false;
        if (!base.chars().allMatch(c -> c >= 'a' && c <= 'z')) return false;
        String korean = converter.convert(base);
        if (korean.chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3)) return false;
        for (int split = 3; split <= base.length() - 3; split++) {
            int left = data.english().getOrDefault(base.substring(0, split), 0);
            int right = data.english().getOrDefault(base.substring(split), 0);
            if (left > 0 && left <= 5000 && right > 0 && right <= 5000) return true;
        }
        return false;
    }
}
