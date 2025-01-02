package com.whispertflite.asr;

public class WhisperResult {
    private final String result;
    private final String language;
    private final Whisper.Action task;

    public WhisperResult(String result, String language, Whisper.Action task){
        this.result = result;
        this.language = language;
        this.task = task;
    }

    public String getResult() {
        return result;
    }

    public String getLanguage() {
        return language;
    }

    public Whisper.Action getTask() {
        return task;
    }
}
