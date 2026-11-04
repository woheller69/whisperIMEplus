package com.whisperonnx.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text to token ids for Whisper's multilingual vocabulary. Free of Android dependencies. */
public final class WhisperTokenizer {
    private static final Pattern SPLIT = Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+");

    private final Map<String, Integer> ranks;

    public WhisperTokenizer(InputStream vocabulary) throws IOException {
        ranks = new HashMap<>(70000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(vocabulary, StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf(' ');
                // The vocabulary has one empty placeholder, written as "="
                if (separator <= 0 || line.charAt(0) == '=') continue;
                byte[] token = Base64.getDecoder().decode(line.substring(0, separator));
                ranks.put(new String(token, StandardCharsets.ISO_8859_1), Integer.parseInt(line.substring(separator + 1).trim()));
            }
        }
    }

    public List<Integer> encode(String text) {
        List<Integer> tokens = new ArrayList<>();
        Matcher matcher = SPLIT.matcher(text);
        while (matcher.find()) {
            mergeAndAppend(new String(matcher.group().getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1), tokens);
        }
        return tokens;
    }

    /** Joins as many words as fit in maxTokens, as " one, two, three". */
    public int[] encodeWords(List<String> words, int maxTokens) {
        List<Integer> best = new ArrayList<>();
        StringBuilder prompt = new StringBuilder();
        for (String word : words) {
            String candidate = prompt.length() == 0 ? " " + word : prompt + ", " + word;
            List<Integer> tokens = encode(candidate);
            if (tokens.size() > maxTokens) break;
            prompt = new StringBuilder(candidate);
            best = tokens;
        }
        int[] result = new int[best.size()];
        for (int i = 0; i < result.length; i++) result[i] = best.get(i);
        return result;
    }

    // Byte pair merging: always joins the adjacent pair with the lowest rank
    private void mergeAndAppend(String piece, List<Integer> output) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < piece.length(); i++) parts.add(piece.substring(i, i + 1));
        while (parts.size() > 1) {
            int bestIndex = -1;
            int bestRank = Integer.MAX_VALUE;
            for (int i = 0; i < parts.size() - 1; i++) {
                Integer rank = ranks.get(parts.get(i) + parts.get(i + 1));
                if (rank != null && rank < bestRank) {
                    bestRank = rank;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0) break;
            parts.set(bestIndex, parts.get(bestIndex) + parts.remove(bestIndex + 1));
        }
        for (String part : parts) {
            Integer rank = ranks.get(part);
            if (rank != null) output.add(rank);
        }
    }
}
