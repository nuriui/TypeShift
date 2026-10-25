package com.teashoe.typeshift.core;

import java.util.*;
import static com.teashoe.typeshift.core.ReplacementManager.*;

public final class InputController {
    public enum Source { PASTE, COMPLETION, HISTORY, MOUSE, NAVIGATION, EXTERNAL, IME }
    private final ConversionEngine engine;
    private final CommandProtector protector;
    private final ReplacementManager replacements = new ReplacementManager();
    private final CompositionTracker composition = new CompositionTracker();
    private final LanguageTracker language = new LanguageTracker();
    private InputSession session;
    private ConversionRecord undo;
    private ConversionRecord owned;
    private int ownedLiteralPrefix;
    private boolean ownedNumeric;
    private boolean ownedNumericVersion;
    private LanguageTracker.Language quoteLanguage;
    private ConversionRecord precedingUndo;
    private Snapshot expectedState;
    private boolean suppressed;
    private boolean pendingDigits;
    private boolean numericSession;
    private boolean numericVersion;
    private boolean numericGeneralSuffix;
    private boolean ownedNumericGeneralSuffix;
    private String pendingNumber = "";
    private final NumericSuffixMatcher numericSuffixes = new NumericSuffixMatcher();
    public InputController(ConversionEngine engine, CommandProtector protector) { this.engine = engine; this.protector = protector; }
    public ReplacementManager replacements() { return replacements; }
    public CompositionTracker composition() { return composition; }
    public LanguageTracker.Language language() { return language.language(); }
    public void setPlayers(Collection<String> names) { protector.setPlayers(names); }
    public void external(Source source) {
        if (source != Source.NAVIGATION && source != Source.MOUSE) owned = null;
        quoteLanguage = null;
        session = null; undo = null; precedingUndo = null; suppressed = true; expectedState = null; language.reset();
        pendingDigits = false; numericSession = false; numericVersion = false; numericGeneralSuffix = false; pendingNumber = "";
    }
    public void preedit(boolean active) {
        if (active || composition.composing()) external(Source.IME);
        composition.preedit(active);
    }
    public Optional<Edit> typed(Snapshot before, Snapshot after, int codepoint) {
        if (!engine.enabled() || replacements.modifying()) return Optional.empty();
        if (expectedState != null && !expectedState.equals(before)) external(Source.NAVIGATION);
        Optional<Edit> edit = handleTyped(before, after, codepoint);
        expectedState = edit.map(Edit::result).orElse(after);
        if (owned != null) {
            if ((owned.after().text().equals(before.text()) || owned.after().text().equals(expectedState.text()))
                && expectedState.text().startsWith(owned.displayed(), owned.start()))
                owned = new ConversionRecord(owned.start(), owned.raw(), owned.displayed(), owned.before(), expectedState);
            else owned = null;
        }
        return edit;
    }
    private Optional<Edit> handleTyped(Snapshot before, Snapshot after, int codepoint) {
        if (!composition.acceptAscii(codepoint)) { external(Source.IME); return Optional.empty(); }
        String added = new String(Character.toChars(codepoint));
        int from = Math.min(before.cursor(), before.anchor()), to = Math.max(before.cursor(), before.anchor());
        String expected = before.text().substring(0, from) + added + before.text().substring(to);
        if (!expected.equals(after.text()) || after.cursor() != from + added.length()) { external(Source.EXTERNAL); return Optional.empty(); }
        if (undo != null) {
            if (undo.after().text().equals(before.text()) && from >= undo.start() + undo.displayed().length())
                undo = new ConversionRecord(undo.start(), undo.raw(), undo.displayed(), undo.before(), after);
            else undo = null;
        }
        if (session != null && !session.matches(before)) external(Source.NAVIGATION);
        boolean letter = codepoint >= 'a' && codepoint <= 'z' || codepoint >= 'A' && codepoint <= 'Z';
        if (!letter) {
            Optional<Edit> result = Optional.empty();
            if (session != null) {
                if (":/\\.@_~0123456789".indexOf(codepoint) >= 0) {
                    result = restoreRaw(after, before.cursor());
                } else if (Character.isWhitespace(codepoint) || "!?;,\"".indexOf(codepoint) >= 0) {
                    result = decide(after, before.cursor(), true);
                }
            }
            boolean freshNumber = from == 0 || Character.isWhitespace(before.text().charAt(from - 1))
                || before.text().charAt(from - 1) == '"';
            if (codepoint >= '0' && codepoint <= '9' && (freshNumber || session == null && !pendingNumber.isEmpty())) {
                pendingNumber = (freshNumber ? "" : pendingNumber) + (char) codepoint;
                pendingDigits = true;
            } else if (codepoint == '.' && pendingDigits && session == null) {
                pendingNumber += '.';
                pendingDigits = false;
            } else {
                pendingNumber = "";
                pendingDigits = false;
            }
            if (codepoint == '"') {
                if (quoteLanguage == null) quoteLanguage = language.language();
                else {
                    switch (quoteLanguage) {
                        case KOREAN -> language.koreanEvidence();
                        case ENGLISH -> language.englishEvidence();
                        case UNDECIDED -> language.reset();
                    }
                    quoteLanguage = null;
                }
            }
            session = null;
            numericSession = false;
            suppressed = !Character.isWhitespace(codepoint) && "!?;,\"".indexOf(codepoint) < 0;
            return result;
        }
        if (suppressed) {
            if (pendingDigits || from == 0 || Character.isWhitespace(before.text().charAt(from - 1))) suppressed = false;
            else return Optional.empty();
        }
        if (session == null) {
            session = new InputSession(from);
            numericSession = pendingDigits;
            numericVersion = pendingDigits && pendingNumber.contains(".");
            numericGeneralSuffix = pendingDigits && (!numericVersion || pendingNumber.chars().filter(c -> c == '.').count() >= 2);
            pendingDigits = false; pendingNumber = "";
            precedingUndo = undo;
        }
        session.append((char) codepoint);
        Optional<Edit> result = decide(after, after.cursor(), false);
        if (result.isEmpty()) session.display(session.raw(), false);
        return result;
    }
    private Optional<Edit> decide(Snapshot state, int end, boolean boundary) {
        String rawContext = state.text().substring(0, session.start()) + session.raw() + state.text().substring(end);
        var numericMatch = numericSession ? numericSuffixes.match(session.raw(), numericVersion) : NumericSuffixMatcher.Match.NONE;
        String numericCandidate = numericSession ? engine.compose(session.raw()) : "";
        boolean numericEvidence = numericSession && numericGeneralSuffix && numericCandidate.length() >= 2
            && numericCandidate.chars().allMatch(c -> c >= 0xAC00 && c <= 0xD7A3)
            && !engine.isEnglishWord(session.raw())
            && (language.prefersKorean() || engine.evaluate(session.raw(), boundary, false, false).convert());
        boolean numericContinuation = numericSession && session.converted();
        if (protector.protectedRange(rawContext, session.start(), session.start() + session.raw().length(),
                numericSession && (numericMatch != NumericSuffixMatcher.Match.NONE || numericEvidence || numericContinuation), numericVersion))
            return restoreRaw(state, end);
        if (numericSession) {
            if (numericMatch == NumericSuffixMatcher.Match.COMPLETE || numericEvidence) {
                language.koreanEvidence();
                return Optional.of(convert(state, end, engine.compose(session.raw())));
            }
            if (numericContinuation && !engine.prefersEnglish(session.raw(), boundary))
                return Optional.of(convert(state, end, numericCandidate));
            return restoreRaw(state, end);
        }
        var rule = engine.userDictionary().rule(session.raw());
        if (rule == UserDictionary.Rule.KEEP) return restoreRaw(state, end);
        if (rule == UserDictionary.Rule.CONVERT) {
            if (!boundary) return restoreRaw(state, end);
            language.koreanEvidence();
            return Optional.of(convert(state, end, engine.compose(session.raw())));
        }
        if (engine.knownKorean(session.raw())) session.literalPrefixLength(0);
        var decision = engine.evaluate(session.raw(), boundary, koreanContext(state.text(), session.start()));
        var attached = engine.attachedParticle(session.raw());
        if (!decision.convert() && attached.isPresent() && session.displayed().startsWith(
                session.raw().substring(0, attached.get().prefixLength()))) {
            var match = attached.get();
            if (match.complete() || !boundary && language.prefersKorean()) {
                session.literalPrefixLength(match.prefixLength());
                if (match.complete()) language.koreanEvidence();
                return Optional.of(convert(state, end, match.candidate()));
            }
            return restoreRaw(state, end);
        }
        if (quoteLanguage != null && engine.isEnglishWord(session.raw())) {
            language.englishEvidence();
            return restoreRaw(state, end);
        }
        if (decision.convert()) {
            language.koreanEvidence();
            return Optional.of(convert(state, end, decision.candidate()));
        }
        if (engine.prefersEnglish(session.raw(), boundary)) {
            language.englishEvidence();
            return restoreRaw(state, end);
        }
        if (language.prefersKorean()) {
            int literalEnd = Math.min(session.literalPrefixLength(), session.raw().length());
            String candidate = session.raw().substring(0, literalEnd) + engine.compose(session.raw().substring(literalEnd));
            return Optional.of(convert(state, end, candidate));
        }
        return restoreRaw(state, end);
    }
    private Optional<Edit> restoreRaw(Snapshot state, int end) {
        if (!session.converted()) return Optional.empty();
        Edit edit = replacements.replace(state, session.start(), end, session.raw());
        session.display(session.raw(), false);
        if (owned != null && owned.start() == session.start()) owned = null;
        if (undo != null && undo.start() == session.start()) {
            undo = precedingUndo == null ? null : new ConversionRecord(precedingUndo.start(), precedingUndo.raw(),
                precedingUndo.displayed(), precedingUndo.before(), edit.result());
        }
        return Optional.of(edit);
    }
    public Optional<Edit> finish(Snapshot state) {
        if (!engine.enabled() || composition.composing() || replacements.modifying() || suppressed
            || session == null || !session.matches(state)) return Optional.empty();
        if (expectedState != null && !expectedState.equals(state)) { external(Source.NAVIGATION); return Optional.empty(); }
        Optional<Edit> edit = decide(state, state.cursor(), true);
        expectedState = edit.map(Edit::result).orElse(state);
        session = null;
        return edit;
    }
    private boolean koreanContext(String text, int start) {
        String prefix = text.substring(Math.max(0, start - 64), start).stripTrailing();
        String[] words = prefix.split("\\s+");
        int syllables = 0;
        for (int i = Math.max(0, words.length - 3); i < words.length; i++)
            syllables += (int) words[i].chars().filter(c -> c >= 0xAC00 && c <= 0xD7A3).count();
        return syllables >= 2;
    }
    private Edit convert(Snapshot state, int end, String candidate) {
        Edit edit = replacements.replace(state, session.start(), end, candidate);
        session.display(candidate, true);
        undo = new ConversionRecord(session.start(), session.raw(), candidate, state, edit.result());
        owned = undo;
        ownedLiteralPrefix = session.literalPrefixLength();
        ownedNumeric = numericSession;
        ownedNumericVersion = numericVersion;
        ownedNumericGeneralSuffix = numericGeneralSuffix;
        replacements.record(undo);
        return edit;
    }
    public boolean wouldExceedRawLimit(Snapshot state, int codepoint, int limit) {
        int reserve = 0;
        if (session != null && session.converted() && session.matches(state))
            reserve = session.raw().length() - session.displayed().length();
        if (undo != null && undo.after().text().equals(state.text()))
            reserve = Math.max(reserve, undo.raw().length() - undo.displayed().length());
        return codepoint >= 32 && codepoint < 127 && state.text().length() + reserve + 1 > limit;
    }
    public void deleted(Snapshot before, Snapshot after) {
        if (replacements.modifying()) return;
        ConversionRecord previous = owned;
        if (composition.composing() || previous == null || !previous.after().text().equals(before.text())) {
            external(Source.EXTERNAL); expectedState = after; return;
        }
        int removed = before.text().length() - after.text().length();
        int from = after.cursor(), end = from + removed;
        if (removed <= 0 || after.cursor() != after.anchor() || end > before.text().length()
            || from < previous.start() || from > previous.start() + previous.displayed().length()
            || !after.text().equals(before.text().substring(0, from) + before.text().substring(end))) {
            external(Source.EXTERNAL); expectedState = after; return;
        }
        String retained = after.text().substring(previous.start(), from);
        int literal = ownedLiteralPrefix;
        for (int length = previous.raw().length(); length >= 0; length--) {
            String raw = previous.raw().substring(0, length);
            int prefix = Math.min(literal, length);
            if (!(raw.substring(0, prefix) + engine.compose(raw.substring(prefix))).equals(retained)) continue;
            session = new InputSession(previous.start());
            for (char c : raw.toCharArray()) session.append(c);
            session.literalPrefixLength(prefix);
            session.display(retained, true);
            suppressed = false; pendingDigits = false; pendingNumber = ""; numericSession = ownedNumeric; numericVersion = ownedNumericVersion; numericGeneralSuffix = ownedNumericGeneralSuffix;
            language.koreanEvidence(); expectedState = after;
            undo = new ConversionRecord(previous.start(), raw, retained, before, after);
            owned = undo;
            return;
        }
        external(Source.EXTERNAL); expectedState = after;
    }
    public Optional<Edit> backspace(Snapshot state) {
        if (composition.composing() || session == null || !session.converted() || !session.matches(state)) return Optional.empty();
        session.backspace();
        int literalEnd = Math.min(session.literalPrefixLength(), session.raw().length());
        String candidate = session.raw().substring(0, literalEnd) + engine.compose(session.raw().substring(literalEnd));
        Edit edit = convert(state, state.cursor(), candidate);
        if (literalEnd > 0 && literalEnd == session.raw().length()) {
            session.display(candidate, false);
            undo = precedingUndo == null ? null : new ConversionRecord(precedingUndo.start(), precedingUndo.raw(),
                precedingUndo.displayed(), precedingUndo.before(), edit.result());
        }
        expectedState = edit.result();
        if (session.raw().isEmpty()) { session = null; undo = null; }
        return Optional.of(edit);
    }
    public Optional<Edit> undo(Snapshot state) {
        if (composition.composing() || undo == null || !undo.after().text().equals(state.text())) return Optional.empty();
        try { engine.userDictionary().reversed(undo.raw()); }
        catch (java.io.IOException ex) { System.getLogger("TypeShift").log(System.Logger.Level.WARNING, "Could not save learned word", ex); }
        Edit edit = replacements.replace(state, undo.start(), undo.start() + undo.displayed().length(), undo.raw());
        owned = null;
        session = null; undo = null; precedingUndo = null; suppressed = true; pendingDigits = false; pendingNumber = ""; numericSession = false; numericVersion = false; language.englishEvidence(); expectedState = edit.result();
        return Optional.of(edit);
    }
}
