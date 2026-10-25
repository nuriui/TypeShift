package com.teashoe.typeshift.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class UserDictionary {
    public enum Rule { KEEP, CONVERT }
    public record Entry(String keys, Rule rule) {}
    private final Path path;
    private final Map<String, Rule> rules = new TreeMap<>();
    private final Map<String, Integer> reversals = new TreeMap<>();
    public UserDictionary() { path = null; }
    public UserDictionary(Path path) throws IOException {
        this.path = path;
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(reader); }
        try {
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("rule.")) rules.put(validate(key.substring(5)), Rule.valueOf(p.getProperty(key)));
                else if (key.startsWith("undo.")) reversals.put(validate(key.substring(5)), Math.min(3, Math.max(0, Integer.parseInt(p.getProperty(key)))));
            }
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid user dictionary", ex); }
    }
    public static String keys(String word) {
        String trimmed = word.strip();
        if (!trimmed.codePoints().allMatch(c -> c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= 0xAC00 && c <= 0xD7A3))
            throw new IllegalArgumentException("영문 또는 완성된 한글 단어 하나를 입력하세요.");
        return validate(new DubeolsikConverter().toKeys(trimmed));
    }
    private static String validate(String keys) {
        if (keys.isEmpty() || keys.length() > 64 || !keys.matches("[a-zA-Z]+"))
            throw new IllegalArgumentException("단어는 영문 키 기준 1~64자로 입력하세요.");
        return keys;
    }
    public synchronized Rule rule(String keys) { return rules.get(keys); }
    public synchronized List<Entry> entries() { return rules.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList(); }
    public synchronized void put(String word, Rule rule) throws IOException {
        String key = keys(word);
        change(() -> { rules.put(key, Objects.requireNonNull(rule)); reversals.remove(key); });
    }
    public synchronized void remove(String key) throws IOException {
        change(() -> { rules.remove(key); reversals.remove(key); });
    }
    public synchronized void reversed(String raw) throws IOException {
        if (!raw.matches("[a-zA-Z]{1,64}")) return;
        change(() -> {
            int count = Math.min(3, reversals.getOrDefault(raw, 0) + 1);
            reversals.put(raw, count);
            if (count >= 3) rules.put(raw, Rule.KEEP);
        });
    }
    private void change(Runnable mutation) throws IOException {
        var oldRules = new TreeMap<>(rules); var oldReversals = new TreeMap<>(reversals);
        mutation.run();
        try { save(); }
        catch (IOException ex) {
            rules.clear(); rules.putAll(oldRules); reversals.clear(); reversals.putAll(oldReversals); throw ex;
        }
    }
    private void save() throws IOException {
        if (path == null) return;
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Properties p = new Properties();
        rules.forEach((k,v) -> p.setProperty("rule." + k, v.name()));
        reversals.forEach((k,v) -> p.setProperty("undo." + k, v.toString()));
        Path temp = Files.createTempFile(target.getParent(), "typeshift-words-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { p.store(writer, "TypeShift local word rules"); }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
