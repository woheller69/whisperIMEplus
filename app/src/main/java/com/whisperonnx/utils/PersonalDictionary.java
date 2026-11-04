package com.whisperonnx.utils;

import android.content.Context;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Words saved on this device only: each word, with optional variants the model writes instead. */
public final class PersonalDictionary {
    private static final String TAG = "PersonalDictionary";
    private static final String PREF_KEY = "personalWords";
    private static final String VOCABULARY_ASSET = "multilingual.tiktoken";
    private static final int MAX_PROMPT_TOKENS = 100;
    private static WhisperTokenizer tokenizer;

    private PersonalDictionary() {}

    /** Word -> variants, in the order they were added. */
    public static Map<String, List<String>> load(Context context) {
        Map<String, List<String>> entries = new LinkedHashMap<>();
        String stored = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, "{}");
        try {
            JSONObject json = new JSONObject(stored);
            Iterator<String> words = json.keys();
            while (words.hasNext()) {
                String word = words.next();
                JSONArray array = json.getJSONArray(word);
                List<String> variants = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) variants.add(array.getString(i));
                entries.put(word, variants);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not read the dictionary", e);
        }
        return entries;
    }

    /** Adds a word, or adds the variants to it if it already exists. */
    public static void add(Context context, String word, List<String> newVariants) {
        Map<String, List<String>> entries = load(context);
        String existing = word;
        for (String candidate : entries.keySet()) {
            if (candidate.equalsIgnoreCase(word)) existing = candidate;
        }
        List<String> variants = entries.containsKey(existing) ? entries.remove(existing) : new ArrayList<>();
        for (String variant : newVariants) {
            boolean known = variant.equalsIgnoreCase(word);
            for (String current : variants) known |= current.equalsIgnoreCase(variant);
            if (!known) variants.add(variant);
        }
        entries.put(word, variants);
        save(context, entries);
    }

    public static void remove(Context context, String word) {
        Map<String, List<String>> entries = load(context);
        entries.remove(word);
        save(context, entries);
    }

    public static String applyCorrections(Context context, String text) {
        Map<String, String> replacements = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> entry : load(context).entrySet()) {
            for (String variant : entry.getValue()) replacements.put(variant, entry.getKey());
        }
        return DictionaryMatcher.apply(text, replacements);
    }

    /** The saved words as tokens for the model. Empty when there are none or on any failure. */
    public static int[] promptTokens(Context context) {
        List<String> words = new ArrayList<>(load(context).keySet());
        if (words.isEmpty()) return new int[0];
        try {
            return getTokenizer(context).encodeWords(words, MAX_PROMPT_TOKENS);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Could not prepare the dictionary for the model", e);
            return new int[0];
        }
    }

    private static synchronized WhisperTokenizer getTokenizer(Context context) throws IOException {
        if (tokenizer == null) {
            try (InputStream vocabulary = context.getAssets().open(VOCABULARY_ASSET)) {
                tokenizer = new WhisperTokenizer(vocabulary);
            }
        }
        return tokenizer;
    }

    private static void save(Context context, Map<String, List<String>> entries) {
        JSONObject json = new JSONObject();
        try {
            for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
                json.put(entry.getKey(), new JSONArray(entry.getValue()));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not save the dictionary", e);
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_KEY, json.toString()).apply();
    }
}
