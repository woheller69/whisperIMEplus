package com.whisperonnx.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/** Replacements saved on this device only: what the model hears -> what should be written. */
public final class PersonalDictionary {
    private static final String TAG = "PersonalDictionary";
    private static final String PREF_KEY = "personalDictionary";

    private PersonalDictionary() {}

    public static TreeMap<String, String> load(Context context) {
        TreeMap<String, String> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            JSONObject json = new JSONObject(sp.getString(PREF_KEY, "{}"));
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String heard = keys.next();
                entries.put(heard, json.getString(heard));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not read the dictionary", e);
        }
        return entries;
    }

    public static void put(Context context, String heard, String correct) {
        TreeMap<String, String> entries = load(context);
        entries.put(heard.trim(), correct.trim());
        save(context, entries);
    }

    public static void remove(Context context, String heard) {
        TreeMap<String, String> entries = load(context);
        entries.remove(heard);
        save(context, entries);
    }

    public static String apply(Context context, String text) {
        return DictionaryMatcher.apply(text, load(context));
    }

    private static void save(Context context, Map<String, String> entries) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY, new JSONObject(entries).toString())
                .apply();
    }
}
