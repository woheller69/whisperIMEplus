package com.whispertflite.asr;

public class WhisperResult {
    private final String result;
    private final String language;

    public WhisperResult(String result, String language){
        this.result = result;
        this.language = language;
    }

    public String getResult() {
        return result;
    }

    public String getLanguage() {
        return language;
    }
}
