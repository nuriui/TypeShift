package com.teashoe.typeshift.core;

import java.util.*;

public final class AttachedParticleMatcher {
    public record Match(String candidate, int prefixLength, boolean complete, boolean honorific) {}
    private final DictionaryLoader.Data data;
    private final DubeolsikConverter converter = new DubeolsikConverter();
    private final Set<String> complete = new HashSet<>();
    private final Set<String> prefixes = new HashSet<>();
    private final Set<String> honorifics = new HashSet<>();
    private final NgramScorer scorer;
    public AttachedParticleMatcher(DictionaryLoader.Data data) {
        this.data = data;
        scorer = new NgramScorer(data);
        Set<String> particles = new HashSet<>(Set.of("은", "는", "이", "가", "을", "를", "에", "에서", "로", "으로", "와", "과",
            "도", "만", "한테", "에게", "까지", "부터", "처럼", "보다", "랑", "이랑", "하고", "의", "이나", "이라고", "라고",
            "이라는", "라는", "이다", "이고", "이며", "입니다", "이에요", "예요"));
        for (String base : List.of("에", "에서", "로", "으로", "한테", "에게", "까지", "부터", "처럼", "보다"))
            for (String tail : List.of("는", "도", "만")) particles.add(base + tail);
        for (String tail : List.of("", "은", "이", "을", "께", "께서", "께서는", "께도", "도", "만", "한테", "에게", "과", "처럼")) {
            particles.add("님" + tail);
            honorifics.add(converter.toKeys("님" + tail));
        }
        for (String particle : particles) {
            String keys = converter.toKeys(particle);
            complete.add(keys);
            for (int i = 1; i <= keys.length(); i++) prefixes.add(keys.substring(0, i));
        }
    }
    public Optional<Match> match(String raw) {
        Optional<Match> established = match(raw, false);
        return established.isPresent() ? established : match(raw, true);
    }
    private Optional<Match> match(String raw, boolean allowLowercase) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (data.korean().containsKey(converter.convert(raw))) return Optional.empty();
        if (data.english().containsKey(lower) || data.protectedWords().contains(lower)) return Optional.empty();
        Match partial = null;
        for (int split = raw.length() - 1; split >= 2; split--) {
            String base = raw.substring(0, split).toLowerCase(Locale.ROOT);
            String suffix = raw.substring(split);
            boolean honorific = honorifics.contains(suffix);
            boolean nickname = honorific && base.matches("[a-z]{3,32}")
                && !converter.convert(raw.substring(0, split)).chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
            int rank = data.english().getOrDefault(base, 0);
            boolean completeSuffix = complete.contains(suffix);
            boolean listedStem = allowLowercase && completeSuffix && suffix.length() >= 2 && base.length() >= 4 && rank > 0;
            if (!data.protectedWords().contains(base) && !(rank > 0 && rank <= 20000) && !listedStem
                && !compoundEnglish(base) && !unlistedName(raw.substring(0, split), raw, allowLowercase) && !nickname) continue;
            if (!prefixes.contains(suffix)) continue;
            Match found = new Match(raw.substring(0, split) + converter.convert(suffix), split, completeSuffix, honorific);
            if (completeSuffix) return Optional.of(found);
            if (partial == null) partial = found;
        }
        return Optional.ofNullable(partial);
    }
    private static final Set<String> UNITS = Set.of("h", "m", "s", "g", "k", "x", "ms", "km", "kg", "mg", "cm", "mm", "ml",
        "mb", "gb", "tb", "kb", "hz", "khz", "mhz", "ghz", "fps", "px", "pt", "db", "hr", "min", "sec");
    /** Unit or short English word directly after digits, followed by a particle: 45min으로, 5GB로. */
    public Optional<Match> numericUnit(String raw) { return stemParticle(raw, UNITS); }
    private static final Set<String> EXTENSIONS = Set.of("json", "json5", "txt", "md", "yml", "yaml", "xml", "toml", "cfg", "conf",
        "ini", "properties", "log", "jar", "zip", "rar", "png", "jpg", "jpeg", "gif", "svg", "mp3", "mp4", "pdf", "doc", "docx",
        "xls", "xlsx", "ppt", "pptx", "csv", "java", "kt", "py", "js", "ts", "html", "css", "sh", "bat", "gradle", "exe", "dll",
        "dmg", "pkg", "mcmeta", "mcfunction", "nbt", "dat", "mca", "lock", "bak", "tmp", "class", "com", "net", "org", "io", "kr",
        "dev", "app");
    /** File extension or domain suffix directly followed by a particle: config.json을, github.com에서. */
    public Optional<Match> fileExtension(String raw) { return stemParticle(raw, EXTENSIONS); }
    private Optional<Match> stemParticle(String raw, Set<String> known) {
        Match partial = null;
        for (int split = raw.length() - 1; split >= 1; split--) {
            String stem = raw.substring(0, split), suffix = raw.substring(split);
            String lower = stem.toLowerCase(Locale.ROOT);
            int rank = data.english().getOrDefault(lower, 0);
            if (!(known.contains(lower) || stem.length() >= 3 && rank > 0 && rank <= 20000) || !prefixes.contains(suffix)) continue;
            Match found = new Match(stem + converter.convert(suffix), split, complete.contains(suffix), false);
            if (found.complete()) return Optional.of(found);
            if (partial == null) partial = found;
        }
        return Optional.ofNullable(partial);
    }
    boolean englishStem(String stem, String whole, boolean strict) {
        String base = stem.toLowerCase(Locale.ROOT);
        int rank = data.english().getOrDefault(base, 0);
        if (data.protectedWords().contains(base) || rank > 0 && (rank <= 20000 || strict && stem.length() >= 5)) return true;
        return !strict && (compoundEnglish(base) || unlistedName(stem, whole, true));
    }
    private boolean unlistedName(String base, String whole, boolean allowLowercase) {
        if (base.length() < 3 || base.length() > 32 || !base.matches("[A-Za-z]+")) return false;
        String korean = converter.convert(base);
        if (korean.chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3)) return false;
        boolean acronym = base.matches("[A-Z]{3,8}");
        boolean camel = base.matches("[A-Za-z]*[a-z][A-Z][a-z]+(?:[A-Z][a-z]+)*");
        if (!acronym && !camel && (!allowLowercase || base.length() < 4)) return false;
        return acronym || scorer.english(base, true) >= -4.5
            && scorer.english(base, true) - scorer.english(whole, true) >= 0.35;
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
