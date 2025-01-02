package com.whispertflite.engine;

import com.whispertflite.asr.WhisperResult;

import java.io.IOException;

public interface WhisperEngine {
    boolean isInitialized();
    boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException;
    void deinitialize();
    WhisperResult transcribeRecordBuffer();
}
