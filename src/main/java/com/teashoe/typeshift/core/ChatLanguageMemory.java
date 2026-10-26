package com.teashoe.typeshift.core;

public final class ChatLanguageMemory {
    private LanguageTracker.Language last = LanguageTracker.Language.UNDECIDED;
    public LanguageTracker.Language last() { return last; }
    void remember(LanguageTracker.Language language) {
        if (language != LanguageTracker.Language.UNDECIDED) last = language;
    }
}
