package com.teashoe.typeshift.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DictionaryLoader {
    public record Data(Map<String, Integer> korean, Map<String, Integer> english,
                       Set<String> commonSyllables, Set<String> subtitleOnly,
                       Map<String, Integer> koreanPairs, Map<String, Integer> englishTriples,
                       Map<String, Integer> koreanUnseen, Map<String, Integer> englishUnseen,
                       Map<String, Integer> unknown, Set<String> protectedWords, Set<String> localKoreanWords) {}
    private static final String BASE = "/data/typeshift/catchtype/";
    public Data load() throws IOException {
        Map<String, Integer> korean = ranks(lines(BASE + "korean-words.txt"), 0);
        korean.putAll(ranks(lines(BASE + "korean-more-words.txt"), korean.size()));
        return new Data(Map.copyOf(korean), Map.copyOf(ranks(lines(BASE + "english-words.txt"), 0)),
            Set.copyOf(lines(BASE + "common-syllables.txt")), Set.copyOf(lines(BASE + "english-subtitle-only-words.txt")),
            values("korean-syllable-pairs.txt"), values("english-letter-triples.txt"),
            values("korean-unseen.txt"), values("english-unseen.txt"), values("unknown.txt"),
            Set.copyOf(lines("/data/typeshift/protected-words.txt")),
            Set.copyOf(lines("/data/typeshift/korean-technical-words.txt")));
    }
    private List<String> lines(String path) throws IOException {
        InputStream stream = DictionaryLoader.class.getResourceAsStream(path);
        if (stream == null) throw new IOException("Missing dictionary: " + path);
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
        }
    }
    static Map<String, Integer> ranks(List<String> lines, int offset) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) result.putIfAbsent(lines.get(i), offset + i + 1);
        return result;
    }
    private Map<String, Integer> values(String name) throws IOException {
        return parseValues(lines(BASE + name));
    }
    public static Map<String, Integer> parseValues(List<String> lines) throws IOException {
        Map<String, Integer> result = new HashMap<>();
        for (String line : lines) {
            String[] parts = line.strip().split("\\s+");
            try {
                if (parts.length != 2 || result.putIfAbsent(parts[0], Integer.parseInt(parts[1])) != null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) { throw new IOException("Invalid statistic: " + line, ex); }
        }
        return Map.copyOf(result);
    }
}
