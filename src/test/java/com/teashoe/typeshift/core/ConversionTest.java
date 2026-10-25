package com.teashoe.typeshift.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.teashoe.typeshift.core.ReplacementManager.*;

class ConversionTest {
    static DictionaryLoader.Data data;
    static ConversionEngine engine;
    @BeforeAll static void load() throws Exception {
        data = new DictionaryLoader().load();
        engine = new ConversionEngine(data, AutoLangConfig.defaults());
    }
    @BeforeEach void resetLearning() { engine = new ConversionEngine(data, AutoLangConfig.defaults()); }
    static class Harness {
        final InputController controller = new InputController(engine, new CommandProtector(data.protectedWords()));
        Snapshot state = new Snapshot("", 0, 0);
        List<Integer> changes = new ArrayList<>();
        void type(String text) {
            int count = 0;
            for (int c : text.codePoints().toArray()) {
                count++;
                Snapshot before = state;
                int from = Math.min(state.cursor(), state.anchor()), to = Math.max(state.cursor(), state.anchor());
                String added = new String(Character.toChars(c));
                state = new Snapshot(state.text().substring(0, from) + added + state.text().substring(to), from + added.length(), from + added.length());
                var edit = controller.typed(before, state, c);
                if (edit.isPresent()) { changes.add(count); state = edit.get().result(); }
            }
        }
        void external(String text, int cursor, InputController.Source source) {
            controller.external(source); state = new Snapshot(text, cursor, cursor);
        }
        void undo() { controller.undo(state).ifPresent(e -> state = e.result()); }
    }
    @Test void compoundEnglishNamesWithParticlesAcrossContexts() {
        assertFalse(data.protectedWords().contains("catchtype"));
        assertFalse(data.english().containsKey("catchtype"));
        for (String prefix : List.of("", "dkssud ", "hello ")) {
            for (String name : List.of("typeshift", "TypeShift", "catchtype", "CatchType")) {
                for (String particle : List.of("를", "을", "가", "에서", "으로")) {
                    Harness h = new Harness(); h.type(prefix);
                    String before = h.state.text();
                    h.type(name + new DubeolsikConverter().toKeys(particle));
                    assertEquals(before + name + particle, h.state.text(), prefix + name + particle);
                    h.type(" "); assertEquals(before + name + particle + " ", h.state.text());
                }
            }
        }
    }
    @Test void compoundParticleUndoDeletionAndProtection() {
        Harness h = new Harness(); h.type("catchtypefmf"); assertEquals("catchtype를", h.state.text());
        h.state = h.controller.backspace(h.state).orElseThrow().result(); assertEquals("catchtype르", h.state.text());
        h.type("f"); assertEquals("catchtype를", h.state.text());
        h.undo(); assertEquals("catchtypefmf", h.state.text());
        for (String token : List.of("catchtypefmf.txt", "https://catchtypefmf.com", "/give Steve catchtypefmf", "catchtypefmf123")) {
            Harness protectedToken = new Harness(); protectedToken.type(token + " ");
            assertEquals(token + " ", protectedToken.state.text());
        }
        Harness player = new Harness(); player.controller.setPlayers(List.of("catchtypefmf"));
        player.type("catchtypefmf "); assertEquals("catchtypefmf ", player.state.text());
    }
    @Test void knownShortFirstWordUsesKeyNormalizedPrefixScore() {
        Harness h = new Harness(); h.type("dka"); assertEquals("dka", h.state.text());
        assertTrue(h.changes.isEmpty());
        h.type("k"); assertEquals("아마", h.state.text());
        assertEquals(2, h.state.cursor()); assertEquals(2, h.state.anchor());
        h.type(" "); assertEquals("아마 ", h.state.text());
        h.undo(); assertEquals("dkak ", h.state.text());
        Harness enter = new Harness(); enter.type("dkak");
        enter.controller.finish(enter.state).ifPresent(e -> enter.state = e.result());
        assertEquals("아마", enter.state.text());
    }
    @Test void prefixScoresExcludeEndMarkersAndShareKeyDenominator() {
        var fixture = new DictionaryLoader.Data(data.korean(), data.english(), data.commonSyllables(), data.subtitleOnly(),
            Map.of("^아", -100, "아마", -200, "마$", -300),
            Map.of("^^d", -100, "^dk", -200, "dka", -300, "kak", -400, "ak$", -500),
            data.koreanUnseen(), data.englishUnseen(), data.unknown(), data.protectedWords(), data.localKoreanWords());
        var scorer = new NgramScorer(fixture);
        assertEquals(-0.75, scorer.korean("아마", 4, false), 1e-9);
        assertEquals(-1.5, scorer.korean("아마", 4, true), 1e-9);
        assertEquals(-2.5, scorer.english("dkak", false), 1e-9);
        assertEquals(-3.75, scorer.english("dkak", true), 1e-9);
        assertEquals(0, scorer.korean("", 0, false));
        assertEquals(0, scorer.english("", true));
    }
    @Test void rareSyllableFirstWordWaitsForBoundary() {
        assertFalse(data.korean().containsKey("딴겜"));
        assertFalse(data.localKoreanWords().contains("딴겜"));
        assertFalse(data.commonSyllables().contains("겜"));
        Harness h = new Harness(); h.type("Eksrpa");
        assertEquals("Eksrpa", h.state.text()); assertTrue(h.changes.isEmpty());
        h.type(" "); assertEquals("딴겜 ", h.state.text());
        assertEquals(3, h.state.cursor()); assertEquals(3, h.state.anchor());
        h.undo(); assertEquals("Eksrpa ", h.state.text());
        Harness enter = new Harness(); enter.type("Eksrpa");
        enter.state = enter.controller.finish(enter.state).orElseThrow().result();
        assertEquals("딴겜", enter.state.text());
        enter.state = enter.controller.undo(enter.state).orElseThrow().result();
        assertEquals("Eksrpa", enter.state.text());
    }
    @Test void rareSyllableProtectionAndCase() {
        for (String text : List.of("Eksrpa.txt", "https://Eksrpa.com", "/give Steve Eksrpa", "123Eksrpa",
                "hello", "world", "foobar", "qwerty", "asdfgh", "Minecraft", "TypeShift")) {
            Harness h = new Harness(); h.type(text + " "); assertEquals(text + " ", h.state.text(), text);
        }
        Harness player = new Harness(); player.controller.setPlayers(List.of("Eksrpa"));
        player.type("Eksrpa "); assertEquals("Eksrpa ", player.state.text());
        Harness lower = new Harness(); lower.type("eksrpa "); assertEquals("단겜 ", lower.state.text());
        assertFalse(new ConversionEngine(data, new AutoLangConfig(true, 0, false)).evaluate("Eksrpa", true).convert());
        assertFalse(new ConversionEngine(data, new AutoLangConfig(false, 1, false)).evaluate("Eksrpa", true).convert());
    }
    @Test void rareSyllableReplacementInMiddleAndContinuedTyping() {
        Harness h = new Harness(); h.external("앞  뒤", 2, InputController.Source.MOUSE);
        h.type("Eksrpa "); assertEquals("앞 딴겜  뒤", h.state.text());
        h.type("gkwk"); assertEquals("앞 딴겜 하자 뒤", h.state.text());
        Harness message = new Harness(); message.type("/msg Steve Eksrpa ");
        assertEquals("/msg Steve 딴겜 ", message.state.text());
    }
    @Test void unknownWordUsesSyllableBackoffAtBoundary() {
        assertFalse(data.korean().containsKey("단타"));
        assertFalse(data.localKoreanWords().contains("단타"));
        assertFalse(data.koreanPairs().containsKey("단타"));
        Harness h = new Harness(); h.type("eksxk");
        assertEquals("eksxk", h.state.text());
        assertTrue(h.changes.isEmpty());
        h.type(" "); assertEquals("단타 ", h.state.text());
        assertEquals(3, h.state.cursor()); assertEquals(3, h.state.anchor());
        h.undo(); assertEquals("eksxk ", h.state.text());
        Harness enter = new Harness(); enter.type("eksxk");
        enter.state = enter.controller.finish(enter.state).orElseThrow().result();
        assertEquals("단타", enter.state.text());
    }
    @ParameterizedTest
    @CsvSource({"단타가", "단타를", "단타"})
    void unknownWordInflectionsAndMiddleReplacement(String text) {
        Harness h = new Harness(); h.external("앞  뒤", 2, InputController.Source.MOUSE);
        String raw = new DubeolsikConverter().toKeys(text);
        h.type(raw + " "); assertEquals("앞 " + text + "  뒤", h.state.text());
        h.undo(); assertEquals("앞 " + raw + "  뒤", h.state.text());
    }
    @Test void unknownWordBackoffRetainsProtectionAndSensitivity() {
        for (String token : List.of("hello", "world", "modrinth", "github", "foobar", "qwerty", "asdfgh",
                "eksxk.txt", "https://eksxk.com", "/give Steve eksxk", "123eksxk")) {
            Harness h = new Harness(); h.type(token + " "); assertEquals(token + " ", h.state.text(), token);
        }
        Harness player = new Harness(); player.controller.setPlayers(List.of("eksxk"));
        player.type("eksxk "); assertEquals("eksxk ", player.state.text());
        Harness message = new Harness(); message.type("/msg Steve eksxk ");
        assertEquals("/msg Steve 단타 ", message.state.text());
        assertFalse(new ConversionEngine(data, new AutoLangConfig(false, 0.5, false)).evaluate("eksxk", true).convert());
        assertFalse(new ConversionEngine(data, new AutoLangConfig(true, 0, false)).evaluate("eksxk", true).convert());
        assertFalse(engine.evaluate(new DubeolsikConverter().toKeys("똠쀍쀍"), true).convert());
    }
    @ParameterizedTest
    @CsvSource({"단타", "광클", "데코니나"})
    void unknownBackoffDoesNotDependOnAddedWords(String word) {
        assertFalse(data.korean().containsKey(word));
        assertFalse(data.localKoreanWords().contains(word));
        Harness h = new Harness(); h.type(new DubeolsikConverter().toKeys(word) + " ");
        assertEquals(word + " ", h.state.text());
    }
    @Test void ambiguousEnglishWordWaitsForBoundary() {
        Harness h = new Harness();
        for (String key : List.of("a", "h", "e", "m")) h.type(key);
        assertEquals("ahem", h.state.text());
        assertTrue(h.changes.isEmpty());
        h.type(" ");
        assertEquals("모드 ", h.state.text());
        assertEquals(3, h.state.cursor());
        assertEquals(3, h.state.anchor());
        h.undo(); assertEquals("ahem ", h.state.text());
        Harness finish = new Harness(); finish.type("ahem");
        finish.state = finish.controller.finish(finish.state).orElseThrow().result();
        assertEquals("모드", finish.state.text());
    }
    @ParameterizedTest
    @CsvSource({"r,모득", "s,모든", "f,모들"})
    void ambiguousWordReevaluatesAtFifthKey(String key, String expected) {
        Harness h = new Harness(); h.type("ahem" + key);
        assertEquals(expected, h.state.text());
        assertEquals(List.of(5), h.changes);
        h.state = h.controller.backspace(h.state).orElseThrow().result();
        h.type(key); assertEquals(expected, h.state.text());
        h.undo(); assertEquals("ahem" + key, h.state.text());
    }
    @Test void ambiguousWordContinuesAndPreservesMiddleSuffix() {
        Harness h = new Harness(); h.external("앞  뒤", 2, InputController.Source.MOUSE);
        h.type("ahemrk "); assertEquals("앞 모드가  뒤", h.state.text());
        assertEquals(6, h.state.cursor());
        h.undo(); assertEquals("앞 ahemrk  뒤", h.state.text());
    }
    @Test void ambiguousReevaluationKeepsEnglishAndProtectedTokens() {
        for (String token : List.of("hello", "world", "then", "the", "with", "some", "home", "game",
                "ahem.txt", "ahemr.txt", "https://ahemr.com", "Minecraft", "TypeShift", "/give Steve ahem")) {
            Harness h = new Harness(); h.type(token + " "); assertEquals(token + " ", h.state.text(), token);
        }
        Harness quoted = new Harness(); quoted.type("\"ahem\" "); assertEquals("\"ahem\" ", quoted.state.text());
        Harness player = new Harness(); player.controller.setPlayers(List.of("ahem"));
        player.type("ahem "); assertEquals("ahem ", player.state.text());
        Harness message = new Harness(); message.type("/msg Steve ahem "); assertEquals("/msg Steve 모드 ", message.state.text());
    }
    @ParameterizedTest
    @CsvSource({"dkssud,안녕", "dkssudgktpdy,안녕하세요", "rPthrgo,계속해", "rkqt,값", "rkqtdl,값이", "ekfr,닭", "ekfrdl,닭이", "rhk,과", "rnj,궈", "rml,긔", "Rk,까", "hk,ㅘ"})
    void dubeolsik(String raw, String expected) {
        var converter = new DubeolsikConverter();
        assertEquals(expected, converter.convert(raw));
        assertEquals(raw, converter.toKeys(expected));
    }
    @Test void allHangulRoundTrips() {
        var converter = new DubeolsikConverter();
        for (char c = 0xAC00; c <= 0xD7A3; c++) {
            String value = String.valueOf(c);
            assertEquals(value, converter.convert(converter.toKeys(value)), value);
        }
    }
    @ParameterizedTest
    @CsvSource({"dkssud,안녕,6", "dkssudgktpdy,안녕하세요,6", "rPthrgo,계속해,5"})
    void exactFirstConversionKeystroke(String raw, String expected, int first) {
        Harness h = new Harness(); h.type(raw);
        assertEquals(expected, h.state.text());
        assertEquals(first, h.changes.getFirst());
        assertEquals(expected.length(), h.state.cursor());
        assertEquals(expected.length(), h.state.anchor());
        assertEquals(raw, h.controller.replacements().history().getLast().raw());
    }
    @Test void exactContinuousTrace() {
        Harness h = new Harness(); List<String> trace = new ArrayList<>();
        for (char c : "dkssudgktpdy".toCharArray()) { h.type("" + c); trace.add(h.state.text()); }
        assertEquals(List.of("d", "dk", "dks", "dkss", "dkssu", "안녕", "안녕ㅎ", "안녕하", "안녕핫", "안녕하세", "안녕하셍", "안녕하세요"), trace);
    }
    @ParameterizedTest
    @CsvSource({"Minecraft", "Fabric", "Sodium", "NeoForge", "https://dkssud.com", "C:\\dkssud\\hello.txt", "dkssud.txt", "dkssud123", "hello", "/give Steve dkssud"})
    void protectedTokens(String text) {
        Harness h = new Harness(); h.type(text); assertEquals(text, h.state.text());
    }
    @Test void commandAndPlayerProtection() {
        Harness h = new Harness(); h.type("/msg Steve dkssud");
        assertEquals("/msg Steve 안녕", h.state.text());
        Harness player = new Harness(); player.controller.setPlayers(List.of("dkssudPlayer")); player.type("dkssudPlayer");
        assertEquals("dkssudPlayer", player.state.text());
    }
    @Test void undoAndSuppression() {
        Harness h = new Harness(); h.type("dkssudgktpdy"); h.undo();
        assertEquals("dkssudgktpdy", h.state.text());
        h.type("a"); assertEquals("dkssudgktpdya", h.state.text());
        h.type(" dkssud"); assertEquals("dkssudgktpdya 안녕", h.state.text());
    }
    @Test void undoAfterBoundary() {
        Harness h = new Harness(); h.type("dkssud "); h.undo(); assertEquals("dkssud ", h.state.text());
    }
    @Test void middleInsertionAndRawBackspace() {
        Harness h = new Harness(); h.external("앞  뒤", 2, InputController.Source.MOUSE); h.type("dkssud");
        assertEquals("앞 안녕 뒤", h.state.text()); assertEquals(4, h.state.cursor());
        h.state = h.controller.backspace(h.state).orElseThrow().result();
        assertEquals("앞 안녀 뒤", h.state.text());
        h.type("d"); assertEquals("앞 안녕 뒤", h.state.text());
        h.external("앞 안 뒤", 3, InputController.Source.NAVIGATION); h.type("x");
        assertEquals("앞 안x 뒤", h.state.text());
    }
    @Test void replacementPreservesOutsideAndSelectionDirection() {
        var manager = new ReplacementManager();
        var edit = manager.replace(new Snapshot("aa dkssud zz", 11, 1), 3, 9, "안녕");
        assertEquals(new Snapshot("aa 안녕 zz", 7, 1), edit.result());
        var reverse = manager.replace(new Snapshot("aa dkssud zz", 1, 11), 3, 9, "안녕");
        assertEquals(new Snapshot("aa 안녕 zz", 1, 7), reverse.result());
    }
    @Test void mixedAndFastInput() {
        Harness h = new Harness(); h.type("한글 Minecraft dkssud hello rPthrgo");
        assertEquals("한글 Minecraft 안녕 hello 계속해", h.state.text());
        for (int i = 0; i < 100; i++) { Harness rapid = new Harness(); rapid.type("dkssudgktpdy rPthrgo"); assertEquals("안녕하세요 계속해", rapid.state.text()); }
    }
    @Test void externalOperationsAndImeDoNotConvert() {
        for (var source : InputController.Source.values()) {
            Harness h = new Harness(); h.type("dkssud"); h.external("dkssud", 6, source); h.type("gktpdy");
            assertEquals("dkssudgktpdy", h.state.text()); h.undo(); assertEquals("dkssudgktpdy", h.state.text());
        }
        Harness ime = new Harness(); ime.controller.preedit(true); ime.type("dkssud");
        assertEquals("dkssud", ime.state.text());
        ime.controller.preedit(false); ime.type("한글 dkssud");
        assertEquals("dkssud한글 안녕", ime.state.text());
    }
    @Test void recursionGuardRestoredEvenOnFailure() {
        var manager = new ReplacementManager();
        assertThrows(IllegalStateException.class, () -> manager.guarded(() -> {
            assertTrue(manager.modifying()); manager.guarded(() -> fail("Recursive write")); throw new IllegalStateException();
        }));
        assertFalse(manager.modifying());
    }
    @Test void realDataAndMalformedLoader() throws Exception {
        assertTrue(data.korean().size() > 30000); assertTrue(data.english().size() > 30000);
        assertEquals(131, data.korean().get("안녕"));
        assertTrue(data.koreanPairs().containsKey("^가")); assertTrue(data.englishTriples().containsKey("^^a"));
        assertEquals(-778, data.unknown().get("korean")); assertEquals(-340, data.unknown().get("english"));
        assertEquals(Map.of("abc", -123), DictionaryLoader.parseValues(List.of("abc -123")));
        assertThrows(IOException.class, () -> DictionaryLoader.parseValues(List.of("broken")));
        assertThrows(IOException.class, () -> DictionaryLoader.parseValues(List.of("abc nan")));
        assertThrows(IOException.class, () -> DictionaryLoader.parseValues(List.of("abc -1", "abc -2")));
    }
    @Test void disabledAndSensitivity() {
        assertFalse(new ConversionEngine(data, new AutoLangConfig(false, 0.5, false)).evaluate("dkssud", false).convert());
        assertFalse(new ConversionEngine(data, new AutoLangConfig(true, 0, false)).evaluate("eksxk", true).convert());
        assertTrue(new ConversionEngine(data, new AutoLangConfig(true, 1, false)).evaluate("dkssud", false).convert());
        assertEquals("ㅗ디ㅣㅐ", engine.experimentalReverse("ㅗ디ㅣㅐ"));
        assertEquals("hello", new ConversionEngine(data, new AutoLangConfig(true, .5, true)).experimentalReverse("ㅗ디ㅣㅐ"));
    }
    @Test void configPersistence(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path path = dir.resolve("typeshift.properties"); assertEquals(AutoLangConfig.defaults(), AutoLangConfig.load(path));
        Files.writeString(path, "enabled=false\nsensitivity=0.8\nexperimentalReverse=true\n");
        assertEquals(new AutoLangConfig(false, .8, true), AutoLangConfig.load(path));
        Files.writeString(path, "sensitivity=NaN"); assertThrows(IOException.class, () -> AutoLangConfig.load(path));
    }
    @Test void focusResetIsNotImeComposition() {
        Harness h = new Harness(); h.controller.preedit(false); h.type("dkssud");
        assertEquals("안녕", h.state.text());
    }
    @Test void unknownWordsUseStatistics() {
        var converter = new DubeolsikConverter();
        for (String word : List.of("사랑하겠어요", "행복하시네요")) {
            assertFalse(data.korean().containsKey(word));
            assertTrue(engine.evaluate(converter.toKeys(word), false).convert(), word);
        }
    }
    @Test void rawLimitAndEmptyBackspaceSession() {
        Harness h = new Harness(); h.type("dkssud");
        assertTrue(h.controller.wouldExceedRawLimit(h.state, 'g', 6));
        assertFalse(h.controller.wouldExceedRawLimit(h.state, 'g', 7));
        for (int i = 0; i < 6; i++) h.state = h.controller.backspace(h.state).orElseThrow().result();
        assertEquals("", h.state.text()); h.type("hello"); assertEquals("hello", h.state.text());
    }
    @Test void undoPreservesLaterUnconvertedInput() {
        Harness h = new Harness(); h.type("dkssud hello!"); h.undo();
        assertEquals("dkssud hello!", h.state.text());
        Harness spaced = new Harness(); spaced.type("dkssud   "); spaced.undo();
        assertEquals("dkssud   ", spaced.state.text());
    }
    @Test void reportedMixedSentence() {
        Harness h = new Harness();
        var converter = new DubeolsikConverter();
        h.type(converter.toKeys("안녕 나는 ") + "TypeShift " + converter.toKeys("를 만들려고 하는데 글자가 제대로 입력되는지 확인하고있어"));
        assertEquals("안녕 나는 TypeShift 를 만들려고 하는데 글자가 제대로 입력되는지 확인하고있어", h.state.text());
    }
    @Test void koreanStateComposesBeforeBoundary() {
        Harness h = new Harness(); h.type("dkssud sksms");
        assertEquals("안녕 나는", h.state.text()); h.type(" ");
        assertEquals("안녕 나는 ", h.state.text());
        h.type("TypeShift fmf"); assertEquals("안녕 나는 TypeShift 를", h.state.text());
        h.type(" "); assertEquals("안녕 나는 TypeShift 를 ", h.state.text());
        h.type("dlqfurehlsmswl "); assertEquals("안녕 나는 TypeShift 를 입력되는지 ", h.state.text());
    }
    @Test void contextualProtectionAndIsolatedShortWord() {
        Harness isolated = new Harness(); isolated.type("fmf "); assertEquals("fmf ", isolated.state.text());
        Harness h = new Harness(); h.type("dkssud TypeShift hello Minecraft https://example.com dkssud.txt 123 ");
        assertEquals("안녕 TypeShift hello Minecraft https://example.com dkssud.txt 123 ", h.state.text());
        assertFalse(engine.evaluate("fmf", true).convert());
        assertTrue(engine.evaluate("dlqfurehlsmswl", true, true).convert());
    }
    @ParameterizedTest
    @CsvSource({"글자 깨지는거 뭐가 문제지?", "이번달에 한 것들", "단축키 설정"})
    void reportedForumText(String sentence) {
        Harness h = new Harness(); h.type(new DubeolsikConverter().toKeys(sentence));
        h.controller.finish(h.state).ifPresent(edit -> h.state = edit.result());
        assertEquals(sentence, h.state.text());
    }
    @Test void englishPrefixBeforeForumSentence() {
        Harness h = new Harness(); h.type("gtnh " + new DubeolsikConverter().toKeys("이번달에 한 것들"));
        h.controller.finish(h.state).ifPresent(edit -> h.state = edit.result());
        assertEquals("gtnh 이번달에 한 것들", h.state.text());
    }
    @Test void wordEvidenceUsesComponentsAndPreservesOriginalData() {
        var analyzer = new KoreanWordAnalyzer(data);
        assertFalse(data.korean().containsKey("깨지는거"));
        assertTrue(data.korean().containsKey("깨지는"));
        assertTrue(analyzer.evidence("깨지는거") > 0);
        assertTrue(analyzer.evidence("이번달에") > 0);
        assertFalse(data.korean().containsKey("단축키"));
        assertTrue(data.localKoreanWords().contains("단축키"));
        assertEquals(0, analyzer.evidence("똠쀍쀍"));
        assertFalse(engine.evaluate(new DubeolsikConverter().toKeys("똠쀍쀍"), true, true).convert());
    }
    @Test void finishProtectsExternalImeAndEnglish() {
        for (String value : List.of("Minecraft", "gtnh", "hello", "https://example.com", "dkssud.txt")) {
            Harness h = new Harness(); h.type(value);
            h.controller.finish(h.state).ifPresent(edit -> h.state = edit.result());
            assertEquals(value, h.state.text());
        }
        Harness pasted = new Harness(); pasted.external("ekscnrzl", 8, InputController.Source.PASTE);
        assertTrue(pasted.controller.finish(pasted.state).isEmpty());
        Harness ime = new Harness(); ime.type("ekscnrzl"); ime.controller.preedit(true);
        assertTrue(ime.controller.finish(ime.state).isEmpty());
    }
    @Test void punctuationBoundaryAndUndo() {
        Harness h = new Harness(); h.type("ekscnrzl!"); assertEquals("단축키!", h.state.text());
        h.undo(); assertEquals("ekscnrzl!", h.state.text());
        Harness finalWord = new Harness(); finalWord.type("ekscnrzl");
        finalWord.state = finalWord.controller.finish(finalWord.state).orElseThrow().result();
        assertEquals("단축키", finalWord.state.text()); finalWord.undo(); assertEquals("ekscnrzl", finalWord.state.text());
    }
    @Test void unknownNamesInheritKoreanWithoutDictionaryEntries() {
        var converter = new DubeolsikConverter();
        assertFalse(data.korean().containsKey("데코니나"));
        assertFalse(data.localKoreanWords().contains("데코니나"));
        Harness h = new Harness(); h.type("dkssud ");
        assertEquals(LanguageTracker.Language.KOREAN, h.controller.language());
        for (String name : List.of("데코니나", "뭄바이", "쀍똠", "미쿠")) {
            String before = h.state.text(); h.type(converter.toKeys(name));
            assertEquals(before + name, h.state.text(), name);
            h.type(" ");
            assertEquals(LanguageTracker.Language.KOREAN, h.controller.language());
        }
    }
    @Test void immediateJamoCompositionAfterWhitespace() {
        Harness h = new Harness(); h.type("dkssud "); h.type("e"); assertEquals("안녕 e", h.state.text());
        h.type("p"); assertEquals("안녕 데", h.state.text());
        h.type("zhslsk"); assertEquals("안녕 데코니나", h.state.text());
    }
    @Test void englishRestoresOriginalKeysAndSwitchesBack() {
        Harness h = new Harness(); h.type("dkssud ");
        h.type("he"); assertEquals("안녕 ㅗㄷ", h.state.text());
        h.type("llo "); assertEquals("안녕 hello ", h.state.text());
        assertEquals(LanguageTracker.Language.ENGLISH, h.controller.language());
        h.type("world "); assertEquals("안녕 hello world ", h.state.text());
        h.type("dkssud "); assertEquals("안녕 hello world 안녕 ", h.state.text());
        assertEquals(LanguageTracker.Language.KOREAN, h.controller.language());
        h.type("epzhslsk"); assertEquals("안녕 hello world 안녕 데코니나", h.state.text());
    }
    @Test void protectedTokensDoNotChangeSurroundingKoreanMode() {
        Harness h = new Harness(); h.type("dkssud "); h.controller.setPlayers(List.of("dkssudPlayer"));
        for (String token : List.of("Minecraft", "TypeShift", "dkssudPlayer", "https://example.com", "dkssud.txt", "123")) {
            String before = h.state.text(); h.type(token + " ");
            assertEquals(before + token + " ", h.state.text());
            assertEquals(LanguageTracker.Language.KOREAN, h.controller.language());
        }
        h.type("epzhslsk"); assertTrue(h.state.text().endsWith("데코니나"));
    }
    @Test void undoOverridesKoreanStateAndProtectsNextToken() {
        Harness h = new Harness(); h.type("dkssud epzhslsk"); h.undo();
        assertEquals("안녕 epzhslsk", h.state.text());
        assertEquals(LanguageTracker.Language.ENGLISH, h.controller.language());
        h.type(" epzhslsk"); assertEquals("안녕 epzhslsk epzhslsk", h.state.text());
    }
    @Test void contextResetsOnExternalOperationsAndUnannouncedCursorMove() {
        for (var source : InputController.Source.values()) {
            Harness h = new Harness(); h.type("dkssud "); h.external(h.state.text(), h.state.cursor(), source);
            assertEquals(LanguageTracker.Language.UNDECIDED, h.controller.language());
            h.type("epzhslsk"); assertEquals("안녕 epzhslsk", h.state.text());
        }
        Harness h = new Harness(); h.type("dkssud  ");
        h.state = new Snapshot(h.state.text(), 3, 3); h.type("epzhslsk");
        assertEquals("안녕 epzhslsk ", h.state.text());
        assertEquals(LanguageTracker.Language.UNDECIDED, h.controller.language());
    }
    @Test void inheritedNameBackspaceAndUndoPreserveSuffix() {
        Harness h = new Harness(); h.external("앞  뒤", 2, InputController.Source.MOUSE);
        h.type("dkssud epzhslsk"); assertEquals("앞 안녕 데코니나 뒤", h.state.text());
        h.state = h.controller.backspace(h.state).orElseThrow().result();
        assertEquals("앞 안녕 데코닌 뒤", h.state.text());
        h.type("k"); assertEquals("앞 안녕 데코니나 뒤", h.state.text());
        h.undo(); assertEquals("앞 안녕 epzhslsk 뒤", h.state.text());
    }
    @Test void englishWinsAtBoundaryWithoutEditingNativeIme() {
        Harness h = new Harness(); h.type("dkssud is");
        assertEquals("안녕 ㅑㄴ", h.state.text());
        h.controller.finish(h.state).ifPresent(edit -> h.state = edit.result());
        assertEquals("안녕 is", h.state.text());
        assertEquals(LanguageTracker.Language.ENGLISH, h.controller.language());
        h.controller.preedit(true); h.type("한글"); h.controller.preedit(false);
        assertTrue(h.state.text().endsWith("한글"));
        assertEquals(LanguageTracker.Language.UNDECIDED, h.controller.language());
    }
    @Test void statefulTypingPreservesCaseAndRapidChanges() {
        Harness h = new Harness(); StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            h.type("dkssud epzhslsk Hello world ");
            expected.append("안녕 데코니나 Hello world ");
            assertEquals(expected.toString(), h.state.text());
        }
    }
    @Test void lengthReserveIncludesPreviousUndoAcrossWhitespace() {
        Harness h = new Harness(); h.type("dkssud ");
        assertTrue(h.controller.wouldExceedRawLimit(h.state, 'e', 7));
        assertFalse(h.controller.wouldExceedRawLimit(h.state, 'e', 8));
    }
    @Test void reportedDateAndTimeSentence() {
        var converter = new DubeolsikConverter();
        for (String sentence : List.of("10월 25일 20시 19분인데 지금", "안녕 10월 25일 20시 19분인데 지금")) {
            Harness h = new Harness(); h.type(converter.toKeys(sentence));
            h.controller.finish(h.state).ifPresent(edit -> h.state = edit.result());
            assertEquals(sentence, h.state.text());
        }
    }
    @ParameterizedTest
    @CsvSource({"10월", "25일", "20시", "19분인데", "2026년", "30초", "10월부터", "25일까지", "20시에", "19분입니다"})
    void numericUnitsConvertWithoutPriorKorean(String text) {
        Harness h = new Harness(); h.type(new DubeolsikConverter().toKeys(text));
        assertEquals(text, h.state.text());
    }
    @Test void numericUnitUndoAndBackspacePreserveDigits() {
        Harness h = new Harness(); h.type("10dnjf"); assertEquals("10월", h.state.text());
        h.state = h.controller.backspace(h.state).orElseThrow().result(); assertEquals("10워", h.state.text());
        h.type("f"); assertEquals("10월", h.state.text()); h.undo(); assertEquals("10dnjf", h.state.text());
        h.type("d"); assertEquals("10dnjfd", h.state.text());
    }
    @ParameterizedTest
    @CsvSource({"123abc", "10dnjf.txt", "10dnjf123", "https://example.com/10dnjf", "C:\\10dnjf", "/give Steve 10dnjf", "v10dnjf", "10.5dnjf"})
    void numericExemptionDoesNotAffectProtectedIdentifiers(String text) {
        Harness h = new Harness(); h.type(text); assertEquals(text, h.state.text());
    }
    @Test void numericUnitsDoNotConvertPastedTokensOrPlayerNames() {
        Harness pasted = new Harness(); pasted.external("10", 2, InputController.Source.PASTE);
        pasted.type("dnjf"); assertEquals("10dnjf", pasted.state.text());
        Harness player = new Harness(); player.controller.setPlayers(List.of("10dnjf"));
        player.type("10dnjf"); assertEquals("10dnjf", player.state.text());
        Harness command = new Harness(); command.type("/msg Steve 10dnjf"); assertEquals("/msg Steve 10월", command.state.text());
    }
    @Test void reportedAttachedParticleSentence() {
        Harness h = new Harness(); var converter = new DubeolsikConverter();
        h.type(converter.toKeys("지금 10월 25일 ") + "TypeShift" + converter.toKeys("를 테스트중이야"));
        assertEquals("지금 10월 25일 TypeShift를 테스트중이야", h.state.text());
    }
    @ParameterizedTest
    @CsvSource({"TypeShift,를", "Minecraft,에서", "Fabric,으로", "Sodium,은", "apple,을", "Java,랑"})
    void englishStemWithAttachedParticle(String english, String particle) {
        for (boolean korean : List.of(false, true)) {
            Harness h = new Harness(); if (korean) h.type("dkssud ");
            h.type(english + new DubeolsikConverter().toKeys(particle));
            assertEquals((korean ? "안녕 " : "") + english + particle, h.state.text());
        }
    }
    @Test void mixedStemIsPreservedDuringCompositionAndDeletion() {
        Harness h = new Harness(); h.type("dkssud TypeShiftf"); assertEquals("안녕 TypeShiftㄹ", h.state.text());
        h.type("mf"); assertEquals("안녕 TypeShift를", h.state.text());
        h.state = h.controller.backspace(h.state).orElseThrow().result(); assertEquals("안녕 TypeShift르", h.state.text());
        h.state = h.controller.backspace(h.state).orElseThrow().result(); assertEquals("안녕 TypeShiftㄹ", h.state.text());
        h.state = h.controller.backspace(h.state).orElseThrow().result(); assertEquals("안녕 TypeShift", h.state.text());
        h.type("fmf"); assertEquals("안녕 TypeShift를", h.state.text()); h.undo(); assertEquals("안녕 TypeShiftfmf", h.state.text());
    }
    @Test void attachedParticleProtectionAndSelection() {
        for (String raw : List.of("TypeShiftfmf.txt", "https://TypeShiftfmf", "/give Steve TypeShiftfmf", "TypeShiftfmf123", "different", "software")) {
            Harness h = new Harness(); h.type(raw); assertEquals(raw, h.state.text());
        }
        Harness player = new Harness(); player.controller.setPlayers(List.of("TypeShiftfmf"));
        player.type("TypeShiftfmf"); assertEquals("TypeShiftfmf", player.state.text());
        Harness middle = new Harness(); middle.external("앞  뒤", 2, InputController.Source.MOUSE);
        middle.type("TypeShiftfmf"); assertEquals("앞 TypeShift를 뒤", middle.state.text());
        assertEquals(12, middle.state.cursor());
        middle.undo(); assertEquals("앞 TypeShiftfmf 뒤", middle.state.text());
    }
    @Test void wholeKoreanWordsAreNotSplitAsEnglishPlusParticle() {
        Harness h = new Harness(); var converter = new DubeolsikConverter();
        h.type("dkssud " + converter.toKeys("나를 너는 우리는"));
        assertEquals("안녕 나를 너는 우리는", h.state.text());
        h.state = h.controller.backspace(h.state).orElseThrow().result();
        assertEquals("안녕 나를 너는 우리느", h.state.text());
    }
    @Test void attachedStemSurvivesFurtherKoreanComposition() {
        Harness h = new Harness(); h.type("dkssud TypeShift" + new DubeolsikConverter().toKeys("를테스트"));
        assertEquals("안녕 TypeShift를테스트", h.state.text());
    }
    @Test void unknownKoreanNameDoesNotAcquireFalseEnglishStem() {
        Harness h = new Harness(); h.type("dkssud " + new DubeolsikConverter().toKeys("뭄바이"));
        assertEquals("안녕 뭄바이", h.state.text());
        h.state = h.controller.backspace(h.state).orElseThrow().result();
        assertEquals("안녕 뭄방", h.state.text()); h.type("l"); assertEquals("안녕 뭄바이", h.state.text());
    }
    @Test void typoBackspaceAndRetype() {
        Harness h = new Harness(); h.type(new DubeolsikConverter().toKeys("안녕하셋요"));
        for (int i = 0; i < 5; i++) h.state = h.controller.backspace(h.state).orElseThrow().result();
        assertEquals("안녕하", h.state.text()); h.type("tpdy"); assertEquals("안녕하세요", h.state.text());
    }
    @Test void vanillaDeletionRecoversOnlyOwnedRawPrefix() {
        Harness h = new Harness(); h.type(new DubeolsikConverter().toKeys("안녕하셋요 "));
        Snapshot before = h.state;
        Snapshot after = new Snapshot("안녕하", 3, 3);
        h.controller.deleted(before, after); h.state = after;
        h.type("tpdy"); assertEquals("안녕하세요", h.state.text());
        Harness pasted = new Harness(); pasted.external("안녕하셋요", 5, InputController.Source.PASTE);
        pasted.controller.deleted(pasted.state, after); pasted.state = after;
        pasted.type("tpdy"); assertEquals("안녕하tpdy", pasted.state.text());
    }
    @Test void quotesPermitFollowingKoreanAndProtectEnglish() {
        Harness h = new Harness(); var converter = new DubeolsikConverter();
        h.type(converter.toKeys("영어 다음으로 ") + "\"ahem\"" + converter.toKeys("라는 단어가 오면"));
        assertEquals("영어 다음으로 \"ahem\"라는 단어가 오면", h.state.text());
        Harness protectedText = new Harness(); protectedText.type("dkssud \"Minecraft\" ");
        assertEquals("안녕 \"Minecraft\" ", protectedText.state.text());
    }
    @Test void navigationThenSuffixDeletionRetainsKnownProvenance() {
        Harness h = new Harness(); h.type(new DubeolsikConverter().toKeys("안녕하셋요"));
        h.controller.external(InputController.Source.NAVIGATION);
        Snapshot before = new Snapshot(h.state.text(), 3, 5), after = new Snapshot("안녕하", 3, 3);
        h.controller.deleted(before, after); h.state = after; h.type("tpdy");
        assertEquals("안녕하세요", h.state.text());
    }
    @Test void deletionRecoveryPreservesMixedAndNumericPrefixes() {
        Harness mixed = new Harness(); mixed.type("TypeShiftfmf ");
        Snapshot after = new Snapshot("TypeShift르", 10, 10);
        mixed.controller.deleted(mixed.state, after); mixed.state = after; mixed.type("f");
        assertEquals("TypeShift르f", mixed.state.text());
        Harness numeric = new Harness(); numeric.type("19qnsdlsep ");
        Snapshot shortened = new Snapshot("19분", 3, 3);
        numeric.controller.deleted(numeric.state, shortened); numeric.state = shortened; numeric.type("dlsep");
        assertEquals("19분인데", numeric.state.text());
    }
    @Test void versionWordAfterDottedNumber() {
        var converter = new DubeolsikConverter();
        for (String text : List.of("26.3.6버전인데 사용하는데", "지금은 26.3.6버전인데 사용하는데에는 크게 문제가 없는거같아", "1.2버전", "26버전으로", "1.2.3버전입니다")) {
            Harness h = new Harness(); h.type(converter.toKeys(text));
            assertEquals(text, h.state.text());
        }
    }
    @Test void codexWithAttachedKorean() {
        var converter = new DubeolsikConverter();
        Harness h = new Harness(); h.type("codex" + converter.toKeys("한테 고쳐달라고 말해야겠다"));
        assertEquals("codex한테 고쳐달라고 말해야겠다", h.state.text());
    }
    @ParameterizedTest
    @CsvSource({"26.3.6", "26.3.6.exe", "26.3.6qjwjs.txt", "https://example.com/26.3.6qjwjs", "1.2.3abc", "10.5dnjf", "1..2qjwjs", "/give Steve 26.3.6qjwjs"})
    void dottedNumericExceptionDoesNotExposeOtherProtectedTokens(String text) {
        Harness h = new Harness(); h.type(text); assertEquals(text, h.state.text());
    }
    @Test void versionSuffixUndoAndDeletionRecovery() {
        Harness h = new Harness(); h.type("26.3.6qjwjs"); assertEquals("26.3.6버전", h.state.text());
        h.undo(); assertEquals("26.3.6qjwjs", h.state.text());
        Harness deleted = new Harness(); deleted.type("26.3.6qjwjsdlsep ");
        Snapshot shortened = new Snapshot("26.3.6버전", 8, 8);
        deleted.controller.deleted(deleted.state, shortened); deleted.state = shortened; deleted.type("dlsep");
        assertEquals("26.3.6버전인데", deleted.state.text());
        Harness pasted = new Harness(); pasted.external("26.3.6", 6, InputController.Source.PASTE);
        pasted.type("qjwjs"); assertEquals("26.3.6qjwjs", pasted.state.text());
    }
    @Test void naturalTextAfterNumbersAndVersions() {
        var converter = new DubeolsikConverter();
        for (String text : List.of("안녕 지금은 9시 1분전 아니 9시야 지금은", "1분전", "9시야", "26.3.7같은거", "26.3.7버전을 테스트하고있어", "버전뒤에 나오는 단어")) {
            Harness h = new Harness(); h.type(converter.toKeys(text));
            assertEquals(text, h.state.text());
        }
    }
    @Test void numericContinuationPreservesUnknownNamesAndProtection() {
        Harness h = new Harness(); h.type("dkssud 26.3.7epzhslsk");
        assertEquals("안녕 26.3.7데코니나", h.state.text());
        for (String raw : List.of("26.3.7abc", "26.3.7hello", "26.3.7qjwjsdmf.jar", "123abc", "10.5dnjf")) {
            Harness identifier = new Harness(); identifier.type(raw); assertEquals(raw, identifier.state.text());
        }
    }
    @Test void numericContinuationUndoAndBackspace() {
        Harness h = new Harness(); h.type("1qnswjs"); assertEquals("1분전", h.state.text());
        h.state = h.controller.backspace(h.state).orElseThrow().result(); assertEquals("1분저", h.state.text());
        h.type("s"); assertEquals("1분전", h.state.text()); h.undo(); assertEquals("1qnswjs", h.state.text());
    }
}
