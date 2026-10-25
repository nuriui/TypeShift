package com.teashoe.typeshift.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.nio.file.*;

public final class ClientSmokeTest implements ClientModInitializer {
    private boolean done;
    private int previewTicks = -1;
    static class TestChat extends ChatScreen {
        TestChat() { super("", false); }
        @Override protected void init() {
            input = new EditBox(minecraft.font, 4, 4, 300, 20, Component.literal("Smoke chat"));
            input.setMaxLength(256);
            addRenderableWidget(input);
            setInitialFocus(input);
        }
        EditBox box() { return input; }
        @Override public void removed() {}
    }
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (previewTicks >= 0) {
                if (++previewTicks == 20) {
                    previewTicks = -1;
                    net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
                        try (image) {
                            image.writeToFile(Path.of("user-dictionary-screen.png"));
                            Files.writeString(Path.of("smoke-result.txt"), "PASS: chat regression and Mod Menu dictionary add/search/delete, persistence, screenshot\n");
                        } catch (Exception ex) { throw new RuntimeException(ex); }
                        finally { client.execute(() -> { client.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()); client.stop(); }); }
                    });
                }
                return;
            }
            if (done || !client.isGameLoadFinished() || client.gui.overlay() != null) return;
            done = true;
            try {
                run(client);
                dictionaryScreen(client);
                previewTicks = 0;
            } catch (Throwable ex) {
                ex.printStackTrace();
                try { Files.writeString(Path.of("smoke-result.txt"), "FAIL: " + ex + "\n"); }
                catch (Exception ignored) {}
                throw new AssertionError("TypeShift smoke test failed", ex);
            } finally { if (previewTicks < 0) { client.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()); client.stop(); } }
        });
    }
    private static void press(net.minecraft.client.gui.screens.Screen screen, String label) {
        screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button)
            .map(c -> (net.minecraft.client.gui.components.Button)c).filter(b -> b.getMessage().getString().contains(label))
            .findFirst().orElseThrow().onPress(new KeyEvent(0,0,0));
    }
    private static EditBox screenField(net.minecraft.client.gui.screens.Screen screen, int index) {
        return screen.children().stream().filter(c -> c instanceof EditBox).map(c -> (EditBox)c).toList().get(index);
    }
    private static void dictionaryScreen(Minecraft client) throws Exception {
        net.minecraft.client.gui.screens.Screen screen;
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
            if (!com.terraformersmc.modmenu.ModMenu.hasConfigScreen("typeshift")) throw new AssertionError("Missing Mod Menu factory");
            screen = com.terraformersmc.modmenu.ModMenu.getConfigScreen("typeshift", new net.minecraft.client.gui.screens.TitleScreen());
        } else screen = new com.teashoe.typeshift.client.UserDictionaryScreen(new net.minecraft.client.gui.screens.TitleScreen());
        client.gui.setScreen(screen);
        press(screen, "무조건 바꿀 말");
        screenField(screen,1).setValue("야생섭");press(screen,"추가");
        var words = com.teashoe.typeshift.client.TypeshiftClient.userDictionary();
        String key = new com.teashoe.typeshift.core.DubeolsikConverter().toKeys("야생섭");
        equal(com.teashoe.typeshift.core.UserDictionary.Rule.CONVERT,words.rule(key));
        var saved = new com.teashoe.typeshift.core.UserDictionary(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("typeshift-words.properties"));
        equal(com.teashoe.typeshift.core.UserDictionary.Rule.CONVERT,saved.rule(key));
        screenField(screen,0).setValue("야생섭");press(screen,"검색");press(screen,"삭제");
        if (words.rule(key) != null) throw new AssertionError("Delete did not apply");
        screenField(screen,1).setValue("야생섭");press(screen,"추가");
        screen.init(320,240);
        for (var child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                && (widget.getY() < 0 || widget.getY()+widget.getHeight() > 240)) throw new AssertionError("Widget out of bounds");
        }
        screen.init(client.getWindow().getGuiScaledWidth(),client.getWindow().getGuiScaledHeight());
    }
    private static TestChat open(Minecraft client) {
        TestChat chat = new TestChat(); client.gui.setScreen(chat); return chat;
    }
    private static void type(EditBox box, String text) {
        text.codePoints().forEach(c -> box.charTyped(new CharacterEvent(c)));
    }
    private static void typeWithKeys(EditBox box, String text) throws ReflectiveOperationException {
        for (char c : text.toCharArray()) {
            String field = switch (c) {
                case ' ' -> "KEY_SPACE";
                case '.' -> "KEY_PERIOD";
                default -> "KEY_" + Character.toUpperCase(c);
            };
            int physical = InputConstants.class.getField(field).getInt(null);
            int modifiers = Character.isUpperCase(c) ? InputConstants.MOD_SHIFT : 0;
            KeyEvent event = new KeyEvent(physical, Character.toLowerCase(c), modifiers);
            box.keyPressed(event);
            box.charTyped(new CharacterEvent(c));
            box.keyReleased(event);
        }
    }
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void run(Minecraft client) throws ReflectiveOperationException {
        EditBox box = open(client).box();
        for (String prefix : new String[]{"", "dkssud ", "hello "}) {
            for (String name : new String[]{"typeshift", "catchtype", "TypeShift", "CatchType"}) {
                box = open(client).box();
                typeWithKeys(box, prefix);
                String before = box.getValue();
                typeWithKeys(box, name + "fmf ");
                equal(before + name + "를 ", box.getValue());
            }
        }
        box = open(client).box();
        typeWithKeys(box, "dka"); equal("dka", box.getValue());
        typeWithKeys(box, "k"); equal("아마", box.getValue());
        typeWithKeys(box, " "); equal("아마 ", box.getValue());
        box = open(client).box();
        typeWithKeys(box, "Eksrpa"); equal("Eksrpa", box.getValue());
        typeWithKeys(box, " "); equal("딴겜 ", box.getValue());
        box = open(client).box();
        typeWithKeys(box, "eksxk"); equal("eksxk", box.getValue());
        typeWithKeys(box, " "); equal("단타 ", box.getValue());
        box = open(client).box();
        typeWithKeys(box, "ahem"); equal("ahem", box.getValue());
        typeWithKeys(box, " "); equal("모드 ", box.getValue());
        for (String suffix : new String[]{"r", "s", "f"}) {
            box = open(client).box();
            typeWithKeys(box, "ahem"); equal("ahem", box.getValue());
            typeWithKeys(box, suffix);
            equal(new com.teashoe.typeshift.core.DubeolsikConverter().convert("ahem" + suffix), box.getValue());
        }
        box = open(client).box();
        type(box, "dkssudgktpdy"); equal("안녕하세요", box.getValue()); equal(5, box.getCursorPosition());
        box.keyPressed(new KeyEvent(InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE, 0));
        equal("안녕하셍", box.getValue()); type(box, "y"); equal("안녕하세요", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_Z, InputConstants.KEYCODE_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER));
        equal("dkssudgktpdy", box.getValue());
        box = open(client).box(); type(box, "/msg Steve dkssud"); equal("/msg Steve 안녕", box.getValue());
        box = open(client).box(); type(box, "Minecraft dkssud.txt https://dkssud.com");
        equal("Minecraft dkssud.txt https://dkssud.com", box.getValue());
        box = open(client).box(); box.setValue("앞  뒤"); box.setCursorPosition(2); box.setHighlightPos(2);
        type(box, "dkssud"); equal("앞 안녕 뒤", box.getValue()); equal(4, box.getCursorPosition());
        box = open(client).box(); box.preeditUpdated(PreeditEvent.fromSdlTextEditing("ㅎ", 0, 1));
        type(box, "dkssud"); equal("dkssud", box.getValue());
        box.preeditUpdated(null); type(box, "한글 dkssud"); equal("dkssud한글 안녕", box.getValue());
        box = open(client).box(); box.insertText("dkssud"); type(box, "gktpdy"); equal("dkssudgktpdy", box.getValue());
        box = open(client).box();
        var converter = new com.teashoe.typeshift.core.DubeolsikConverter();
        type(box, converter.toKeys("안녕 나는 ") + "TypeShift " + converter.toKeys("를 만들려고 하는데 글자가 제대로 입력되는지 확인하고있어"));
        equal("안녕 나는 TypeShift 를 만들려고 하는데 글자가 제대로 입력되는지 확인하고있어", box.getValue());
        for (String sentence : java.util.List.of("글자 깨지는거 뭐가 문제지?", "이번달에 한 것들", "단축키 설정")) {
            box = open(client).box();
            type(box, converter.toKeys(sentence));
            box.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
            equal(sentence, box.getValue());
        }
        box = open(client).box();
        type(box, "gtnh " + converter.toKeys("이번달에 한 것들"));
        box.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
        equal("gtnh 이번달에 한 것들", box.getValue());
        box = open(client).box();
        type(box, "dkssud epzhslsk Hello world dkssud " + converter.toKeys("뭄바이"));
        equal("안녕 데코니나 Hello world 안녕 뭄바이", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE, 0));
        equal("안녕 데코니나 Hello world 안녕 뭄방", box.getValue());
        type(box, "l"); equal("안녕 데코니나 Hello world 안녕 뭄바이", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_Z, InputConstants.KEYCODE_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER));
        equal("안녕 데코니나 Hello world 안녕 " + converter.toKeys("뭄바이"), box.getValue());
        box = open(client).box();
        type(box, "dkssud hello!");
        box.keyPressed(new KeyEvent(InputConstants.KEY_Z, InputConstants.KEYCODE_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER));
        equal("dkssud hello!", box.getValue());
        box = open(client).box();
        type(box, converter.toKeys("10월 25일 20시 19분인데 지금"));
        equal("10월 25일 20시 19분인데 지금", box.getValue());
        box = open(client).box(); type(box, "10dnjf"); equal("10월", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE, 0));
        equal("10워", box.getValue()); type(box, "f"); equal("10월", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_Z, InputConstants.KEYCODE_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER));
        equal("10dnjf", box.getValue());
        box = open(client).box(); type(box, "10dnjf.txt 123abc https://example.com/10dnjf");
        equal("10dnjf.txt 123abc https://example.com/10dnjf", box.getValue());
        box = open(client).box();
        type(box, converter.toKeys("지금 10월 25일 ") + "TypeShift" + converter.toKeys("를 테스트중이야"));
        equal("지금 10월 25일 TypeShift를 테스트중이야", box.getValue());
        box = open(client).box(); type(box, "TypeShiftfmf"); equal("TypeShift를", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE, 0));
        equal("TypeShift르", box.getValue()); type(box, "f"); equal("TypeShift를", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_Z, InputConstants.KEYCODE_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER));
        equal("TypeShiftfmf", box.getValue());
        box = open(client).box(); type(box, converter.toKeys("안녕하셋요"));
        for (int i = 0; i < 5; i++)
            box.keyPressed(new KeyEvent(0, InputConstants.KEYCODE_BACKSPACE, 0));
        equal("안녕하", box.getValue()); type(box, "tpdy"); equal("안녕하세요", box.getValue());
        box = open(client).box(); type(box, converter.toKeys("안녕하셋요 "));
        box.setCursorPosition(3); box.setHighlightPos(6);
        box.deleteChars(-1); equal("안녕하", box.getValue());
        type(box, "tpdy"); equal("안녕하세요", box.getValue());
        box = open(client).box();
        type(box, converter.toKeys("영어 다음으로 ") + "\"ahem\"" + converter.toKeys("라는 단어가 오면"));
        equal("영어 다음으로 \"ahem\"라는 단어가 오면", box.getValue());
        box = open(client).box();
        type(box, converter.toKeys("지금은 26.3.6버전인데 사용하는데에는 크게 문제가 없는거같아"));
        equal("지금은 26.3.6버전인데 사용하는데에는 크게 문제가 없는거같아", box.getValue());
        box = open(client).box(); type(box, "codex" + converter.toKeys("한테 고쳐달라고 말해야겠다"));
        equal("codex한테 고쳐달라고 말해야겠다", box.getValue());
        box = open(client).box(); type(box, "26.3.6qjwjs"); equal("26.3.6버전", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_Z, InputConstants.KEYCODE_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER));
        equal("26.3.6qjwjs", box.getValue());
        for (String sentence : java.util.List.of("안녕 지금은 9시 1분전 아니 9시야 지금은", "26.3.7같은거", "버전뒤에 나오는 단어")) {
            box = open(client).box(); type(box, converter.toKeys(sentence)); equal(sentence, box.getValue());
        }
        box = open(client).box(); type(box, "1qnswjs"); equal("1분전", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE, 0));
        equal("1분저", box.getValue()); type(box, "s"); equal("1분전", box.getValue());
        box.keyPressed(new KeyEvent(InputConstants.KEY_Z, InputConstants.KEYCODE_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER));
        equal("1qnswjs", box.getValue());
        box = open(client).box();
        typeWithKeys(box, converter.toKeys("9시에 ") + "typeshift" + converter.toKeys("모드를 26.3.7버전을 테스트하고있어"));
        equal("9시에 typeshift모드를 26.3.7버전을 테스트하고있어", box.getValue());
        EditBox unrelated = new EditBox(client.font, 100, 20, Component.literal("Other field"));
        unrelated.setFocused(true); type(unrelated, "dkssud"); equal("dkssud", unrelated.getValue());
    }
}
