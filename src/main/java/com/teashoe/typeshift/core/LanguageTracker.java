package com.teashoe.typeshift.core;

public final class LanguageTracker {
    public enum Language { UNDECIDED, KOREAN, ENGLISH }
    private Language language = Language.UNDECIDED;
    public Language language() { return language; }
    public boolean prefersKorean() { return language == Language.KOREAN; }
    public void koreanEvidence() { language = Language.KOREAN; }
    public void englishEvidence() { language = Language.ENGLISH; }
    public void reset() { language = Language.UNDECIDED; }
}
