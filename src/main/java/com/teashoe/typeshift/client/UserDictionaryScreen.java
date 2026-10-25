package com.teashoe.typeshift.client;

import com.teashoe.typeshift.core.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class UserDictionaryScreen extends Screen {
    private final Screen parent;
    private final UserDictionary dictionary;
    private UserDictionary.Rule tab = UserDictionary.Rule.KEEP;
    private String query = "", input = "", status = "";
    private int page;
    public UserDictionaryScreen(Screen parent) { this(parent, TypeshiftClient.userDictionary()); }
    public UserDictionaryScreen(Screen parent, UserDictionary dictionary) {
        super(Component.literal("TypeShift · 배운 말")); this.parent = parent; this.dictionary = dictionary;
    }
    @Override protected void init() {
        int w = Math.min(440, width - 24), x = (width - w) / 2;
        label(x, 12, w, "TypeShift · 배운 말");
        button(x, 30, w / 2 - 2, (tab == UserDictionary.Rule.KEEP ? "● " : "") + "그대로 둘 말", () -> switchTab(UserDictionary.Rule.KEEP));
        button(x + w / 2 + 2, 30, w / 2 - 2, (tab == UserDictionary.Rule.CONVERT ? "● " : "") + "무조건 바꿀 말", () -> switchTab(UserDictionary.Rule.CONVERT));
        label(x, 55, w, tab == UserDictionary.Rule.KEEP ? "같은 변환을 3번 되돌리면 자동으로 기억합니다." : "공백·Enter에서 영어 키 입력을 한글로 바꿉니다.");
        EditBox search = field(x, 72, w - 65, "검색", query);
        search.setResponder(value -> query = value);
        button(x + w - 60, 72, 60, "검색", () -> { page = 0; rebuildWidgets(); });
        EditBox add = field(x, 98, w - 65, "추가할 단어", input);
        add.setResponder(value -> input = value);
        button(x + w - 60, 98, 60, "추가", () -> mutate(() -> {
            dictionary.put(input, tab); input = ""; page = 0;
        }));
        label(x, 122, w, "한글 또는 영문 키 입력 · 대소문자 구분 · 즉시 저장");
        if (dictionary == null) {
            label(x, 145, w, "사전을 불러오지 못했습니다. 로그를 확인하세요.");
        } else {
            List<UserDictionary.Entry> entries = dictionary.entries().stream().filter(e -> e.rule() == tab)
                .filter(e -> e.keys().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                    || new DubeolsikConverter().convert(e.keys()).contains(query)).toList();
            int rows = Math.max(1, (height - 222) / 24);
            int pages = Math.max(1, (entries.size() + rows - 1) / rows);
            page = Math.min(page, pages - 1);
            if (entries.isEmpty()) label(x, 148, w, "등록된 말이 없습니다.");
            for (int i = page * rows; i < Math.min(entries.size(), (page + 1) * rows); i++) {
                var entry = entries.get(i); int y = 144 + (i - page * rows) * 24;
                String korean = new DubeolsikConverter().convert(entry.keys());
                label(x, y + 4, w - 65, tab == UserDictionary.Rule.KEEP
                    ? korean + " (" + entry.keys() + ") → 입력 그대로" : entry.keys() + " → " + korean);
                button(x + w - 60, y, 60, "삭제", () -> mutate(() -> dictionary.remove(entry.keys())));
            }
            button(x, height - 72, 60, "이전", () -> { if (page > 0) page--; rebuildWidgets(); });
            label(x + 65, height - 68, w - 130, (page + 1) + " / " + pages + " · " + entries.size() + "개");
            button(x + w - 60, height - 72, 60, "다음", () -> { if (page + 1 < pages) page++; rebuildWidgets(); });
        }
        label(x, height - 47, w, status.isEmpty() ? "이 기기에만 저장 · URL·명령어·플레이어 보호 우선" : status);
        button(x, height - 25, w, "완료", this::onClose);
    }
    private void switchTab(UserDictionary.Rule value) { tab = value; page = 0; status = ""; rebuildWidgets(); }
    private EditBox field(int x, int y, int w, String hint, String value) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setMaxLength(64); box.setHint(Component.literal(hint)); box.setValue(value);
        return addRenderableWidget(box);
    }
    private void label(int x, int y, int w, String text) {
        var label = new StringWidget(x, y, w, 12, Component.literal(text), font).setMaxWidth(w);
        label.setTooltip(Tooltip.create(Component.literal(text)));
        addRenderableWidget(label);
    }
    private void button(int x, int y, int w, String text, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, w, 20).build());
    }
    @FunctionalInterface private interface Mutation { void run() throws Exception; }
    private void mutate(Mutation action) {
        if (dictionary == null) { status = "사전을 불러오지 못했습니다."; rebuildWidgets(); return; }
        try { action.run(); status = "저장했습니다."; }
        catch (IllegalArgumentException ex) { status = ex.getMessage(); }
        catch (Exception ex) { status = "저장 실패: 설정 폴더를 확인하세요."; }
        rebuildWidgets();
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
