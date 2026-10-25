package com.teashoe.typeshift.core;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public record AutoLangConfig(boolean enabled, double sensitivity, boolean experimentalReverse) {
    public AutoLangConfig {
        if (!Double.isFinite(sensitivity) || sensitivity < 0 || sensitivity > 1) throw new IllegalArgumentException("sensitivity must be 0..1");
    }
    public static AutoLangConfig defaults() { return new AutoLangConfig(true, 0.5, false); }
    public static AutoLangConfig load(Path path) throws IOException {
        Properties p = new Properties();
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            p.setProperty("enabled", "true"); p.setProperty("sensitivity", "0.5"); p.setProperty("experimentalReverse", "false");
            try (var writer = Files.newBufferedWriter(path)) { p.store(writer, "TypeShift: sensitivity 0 (conservative) to 1; restart to reload"); }
        } else try (var reader = Files.newBufferedReader(path)) { p.load(reader); }
        try { return new AutoLangConfig(Boolean.parseBoolean(p.getProperty("enabled", "true")),
            Double.parseDouble(p.getProperty("sensitivity", "0.5")), Boolean.parseBoolean(p.getProperty("experimentalReverse", "false"))); }
        catch (IllegalArgumentException ex) { throw new IOException("Invalid TypeShift configuration", ex); }
    }
}
