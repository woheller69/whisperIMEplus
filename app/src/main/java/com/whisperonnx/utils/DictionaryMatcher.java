package com.whisperonnx.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces whole words or phrases in one pass, ignoring case. Free of Android dependencies. */
public final class DictionaryMatcher {
    private DictionaryMatcher() {}

    public static String apply(String text, Map<String, String> replacements) {
        if (text == null || text.isEmpty() || replacements.isEmpty()) return text;

        Map<String, String> byKey = new HashMap<>();
        List<String> heardPhrases = new ArrayList<>();
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            byKey.put(key(entry.getKey()), entry.getValue());
            heardPhrases.add(entry.getKey());
        }
        // Longest first, so that a phrase wins over a word it contains
        heardPhrases.sort(Comparator.comparingInt(String::length).reversed());

        StringBuilder alternatives = new StringBuilder();
        for (String heard : heardPhrases) {
            if (alternatives.length() > 0) alternatives.append('|');
            alternatives.append(Pattern.quote(heard));
        }
        Pattern pattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = byKey.get(key(matcher.group()));
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String key(String phrase) {
        return phrase.trim().toLowerCase(Locale.ROOT);
    }
}
