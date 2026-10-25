package com.teashoe.typeshift.core;

public final class DubeolsikConverter {
    private static final String INITIAL = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String VOWEL = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String FINAL = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";
    private static final String KEYS = "rRseEfaqQtTdwWczxvgkoiOjpuPhynbml";
    private static final String JAMO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅏㅐㅑㅒㅓㅔㅕㅖㅗㅛㅜㅠㅡㅣ";
    private static final String[] PAIRS = {"ㅗㅏㅘ", "ㅗㅐㅙ", "ㅗㅣㅚ", "ㅜㅓㅝ", "ㅜㅔㅞ", "ㅜㅣㅟ", "ㅡㅣㅢ",
        "ㄱㅅㄳ", "ㄴㅈㄵ", "ㄴㅎㄶ", "ㄹㄱㄺ", "ㄹㅁㄻ", "ㄹㅂㄼ", "ㄹㅅㄽ", "ㄹㅌㄾ", "ㄹㅍㄿ", "ㄹㅎㅀ", "ㅂㅅㅄ"};

    public String convert(String keys) {
        StringBuilder out = new StringBuilder();
        char lead = 0, vowel = 0, tail = 0;
        for (char key : keys.toCharArray()) {
            int index = KEYS.indexOf(key);
            if (index < 0) index = KEYS.indexOf(Character.toLowerCase(key));
            char j = index < 0 ? key : JAMO.charAt(index);
            if (VOWEL.indexOf(j) >= 0) {
                if (vowel == 0) vowel = j;
                else if (tail != 0) {
                    String split = split(tail);
                    append(out, lead, vowel, split == null ? 0 : split.charAt(0));
                    lead = split == null ? tail : split.charAt(1);
                    vowel = j; tail = 0;
                } else {
                    char merged = combine(vowel, j);
                    if (merged != 0) vowel = merged;
                    else { append(out, lead, vowel, tail); lead = 0; vowel = j; }
                }
            } else if (INITIAL.indexOf(j) >= 0) {
                if (lead == 0 && vowel == 0) lead = j;
                else if (lead != 0 && vowel != 0 && tail == 0 && FINAL.indexOf(j) > 0) tail = j;
                else if (tail != 0 && combine(tail, j) != 0) tail = combine(tail, j);
                else { append(out, lead, vowel, tail); lead = j; vowel = 0; tail = 0; }
            } else {
                append(out, lead, vowel, tail); lead = vowel = tail = 0; out.append(j);
            }
        }
        append(out, lead, vowel, tail);
        return out.toString();
    }

    private static char combine(char a, char b) {
        for (String p : PAIRS) if (p.charAt(0) == a && p.charAt(1) == b) return p.charAt(2);
        return 0;
    }
    private static String split(char c) {
        for (String p : PAIRS) if (p.charAt(2) == c) return p;
        return null;
    }
    private static void append(StringBuilder out, char l, char v, char t) {
        if (l != 0 && v != 0) out.append((char) (0xAC00 + (INITIAL.indexOf(l) * 21 + VOWEL.indexOf(v)) * 28 + (t == 0 ? 0 : FINAL.indexOf(t))));
        else { if (l != 0) out.append(l); if (v != 0) out.append(v); }
    }
    public String toKeys(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int n = c - 0xAC00;
                appendKeys(out, INITIAL.charAt(n / 588));
                appendKeys(out, VOWEL.charAt(n / 28 % 21));
                if (n % 28 != 0) appendKeys(out, FINAL.charAt(n % 28));
            } else appendKeys(out, c);
        }
        return out.toString();
    }
    private static void appendKeys(StringBuilder out, char c) {
        int i = JAMO.indexOf(c);
        if (i >= 0) out.append(KEYS.charAt(i));
        else {
            String p = split(c);
            if (p == null) out.append(c);
            else { appendKeys(out, p.charAt(0)); appendKeys(out, p.charAt(1)); }
        }
    }
}
