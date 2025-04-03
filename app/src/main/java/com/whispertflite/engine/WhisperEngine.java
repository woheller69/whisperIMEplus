package com.whispertflite.engine;

import com.whispertflite.asr.Whisper;
import com.whispertflite.asr.WhisperResult;

import java.io.IOException;

public interface WhisperEngine {
    boolean isInitialized();
    void initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException;
    void deinitialize();
    WhisperResult processRecordBuffer(Whisper.Action mAction, int mLangToken);
}
