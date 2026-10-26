package com.teashoe.typeshift.core;

public final class LanguageTracker {
    public enum Language { UNDECIDED, KOREAN, ENGLISH }
    private Language language = Language.UNDECIDED;
    private final ChatLanguageMemory memory;
    public LanguageTracker() { this(new ChatLanguageMemory()); }
    public LanguageTracker(ChatLanguageMemory memory) { this.memory = memory; language = memory.last(); }
    public Language language() { return language; }
    public boolean prefersKorean() { return language == Language.KOREAN; }
    public void koreanEvidence() { language = Language.KOREAN; memory.remember(language); }
    public void englishEvidence() { language = Language.ENGLISH; memory.remember(language); }
    public void reset() { language = Language.UNDECIDED; }
}
