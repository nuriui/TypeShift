package com.teashoe.typeshift.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.teashoe.typeshift.core.ReplacementManager.*;

class UserDictionaryTest {
    static DictionaryLoader.Data data;
    @BeforeAll static void load() throws Exception { data = new DictionaryLoader().load(); }
    @TempDir Path dir;
    static class Input {
        final InputController controller;
        Snapshot state = new Snapshot("",0,0);
        Input(UserDictionary words) { controller = new InputController(new ConversionEngine(data, AutoLangConfig.defaults(), words), new CommandProtector(data.protectedWords())); }
        void type(String text) {
            for (char c : text.toCharArray()) {
                Snapshot before=state;
                int at=state.cursor();
                state=new Snapshot(state.text().substring(0,at)+c+state.text().substring(at),at+1,at+1);
                state=controller.typed(before,state,c).map(Edit::result).orElse(state);
            }
        }
        void undo() { state=controller.undo(state).orElseThrow().result(); }
    }
    @Test void rulePersistenceConflictAndDeletion() throws Exception {
        Path file=dir.resolve("words.properties"); var words=new UserDictionary(file);
        words.put("딴겜",UserDictionary.Rule.CONVERT);
        assertEquals(UserDictionary.Rule.CONVERT,new UserDictionary(file).rule("Eksrpa"));
        words.put("Eksrpa",UserDictionary.Rule.KEEP);
        assertEquals(1,words.entries().size());
        assertEquals(UserDictionary.Rule.KEEP,new UserDictionary(file).rule("Eksrpa"));
        words.remove("Eksrpa"); assertTrue(new UserDictionary(file).entries().isEmpty());
    }
    @Test void forcedBoundaryAndProtection() throws Exception {
        var words=new UserDictionary(); words.put("야생섭",UserDictionary.Rule.CONVERT);
        var c=new DubeolsikConverter(); String keys=c.toKeys("야생섭");
        Input input=new Input(words);input.type(keys);assertEquals(keys,input.state.text());
        input.type(" ");assertEquals("야생섭 ",input.state.text());input.undo();assertEquals(keys+" ",input.state.text());
        Input enter=new Input(words);enter.type(keys);enter.state=enter.controller.finish(enter.state).orElseThrow().result();assertEquals("야생섭",enter.state.text());
        for(String text:List.of(keys+".txt","https://"+keys,"/give Steve "+keys,keys+"123")) {
            Input protectedInput=new Input(words);protectedInput.type(text+" ");assertEquals(text+" ",protectedInput.state.text());
        }
        Input player=new Input(words);player.controller.setPlayers(List.of(keys));player.type(keys+" ");assertEquals(keys+" ",player.state.text());
        Input msg=new Input(words);msg.type("/msg Steve "+keys+" ");assertEquals("/msg Steve 야생섭 ",msg.state.text());
    }
    @Test void keepWinsOverKoreanModeAndRemovalAppliesImmediately() throws Exception {
        var words=new UserDictionary();words.put("dkak",UserDictionary.Rule.KEEP);
        Input input=new Input(words);input.type("dkssud dkak ");assertEquals("안녕 dkak ",input.state.text());
        words.remove("dkak");input.type("dkak ");assertEquals("안녕 dkak 아마 ",input.state.text());
    }
    @Test void threeUndosLearnAcrossControllersAndRestarts() throws Exception {
        Path file=dir.resolve("words.properties");
        for(int i=1;i<=3;i++) {
            var words=new UserDictionary(file);Input input=new Input(words);input.type("dkak");assertEquals("아마",input.state.text());input.undo();
            assertEquals("dkak",input.state.text());assertEquals(i==3?UserDictionary.Rule.KEEP:null,new UserDictionary(file).rule("dkak"));
        }
        var words=new UserDictionary(file);Input learned=new Input(words);learned.type("dkak ");assertEquals("dkak ",learned.state.text());
        words.remove("dkak");Input reset=new Input(words);reset.type("dkak");reset.undo();assertNull(words.rule("dkak"));
    }
    @Test void validationAndSaveFailureAreNonDestructive() throws Exception {
        var words=new UserDictionary();
        for(String invalid:List.of(""," ","a b","a.txt","123","ㄱ","a".repeat(65)))
            assertThrows(IllegalArgumentException.class,()->words.put(invalid,UserDictionary.Rule.KEEP));
        Path parent=dir.resolve("parent");Files.createDirectory(parent);Path file=parent.resolve("words.properties");
        var disk=new UserDictionary(file);Files.delete(parent);Files.writeString(parent,"blocked");
        assertThrows(IOException.class,()->disk.put("dkak",UserDictionary.Rule.KEEP));assertTrue(disk.entries().isEmpty());
    }
    @Test void forcedRulesRespectExternalInputAndDisabledMode() throws Exception {
        var words = new UserDictionary(); words.put("Minecraft",UserDictionary.Rule.CONVERT);
        Input protectedWord = new Input(words);protectedWord.type("Minecraft ");assertEquals("Minecraft ",protectedWord.state.text());
        words.put("야생섭",UserDictionary.Rule.CONVERT);String key=UserDictionary.keys("야생섭");
        Input paste=new Input(words);paste.controller.external(InputController.Source.PASTE);
        paste.state=new Snapshot(key,key.length(),key.length());paste.type(" ");assertEquals(key+" ",paste.state.text());
        Input ime=new Input(words);ime.controller.preedit(true);ime.type(key+" ");assertEquals(key+" ",ime.state.text());
        var disabled=new InputController(new ConversionEngine(data,new AutoLangConfig(false,.5,false),words),new CommandProtector(data.protectedWords()));
        assertTrue(disabled.typed(new Snapshot("",0,0),new Snapshot("d",1,1),'d').isEmpty());
    }
    @Test void failedLearningSaveDoesNotPreventUndo() throws Exception {
        Path parent=dir.resolve("readonly");Files.createDirectory(parent);
        var words=new UserDictionary(parent.resolve("words.properties"));
        Input input=new Input(words);input.type("dkak");assertEquals("아마",input.state.text());
        Files.delete(parent);Files.writeString(parent,"blocked");
        input.undo();assertEquals("dkak",input.state.text());assertNull(words.rule("dkak"));
    }
}
