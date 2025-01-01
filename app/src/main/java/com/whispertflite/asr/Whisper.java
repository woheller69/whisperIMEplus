package com.whispertflite.asr;

import android.content.Context;
import android.util.Log;

import com.whispertflite.engine.WhisperEngine;
import com.whispertflite.engine.WhisperEngineJava;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Whisper {

    public interface WhisperListener {
        void onUpdateReceived(String message);
        void onResultReceived(String result);
    }

    private static final String TAG = "Whisper";
    public static final String MSG_PROCESSING = "Processing...";
    public static final String MSG_PROCESSING_DONE = "Processing done...!";
    public static final String MSG_FILE_NOT_FOUND = "Input file doesn't exist..!";

    public static final Action ACTION_TRANSCRIBE = Action.TRANSCRIBE;
    public static final Action ACTION_TRANSLATE = Action.TRANSLATE;
    private String currentModelPath = "";

    private enum Action {
        TRANSLATE, TRANSCRIBE
    }

    private final AtomicBoolean mInProgress = new AtomicBoolean(false);
    private final Queue<float[]> audioBufferQueue = new LinkedList<>();

    private final WhisperEngine mWhisperEngine;
    private Action mAction;
    private WhisperListener mUpdateListener;

    private final Lock taskLock = new ReentrantLock();
    private final Condition hasTask = taskLock.newCondition();
    private volatile boolean taskAvailable = false;

    public Whisper(Context context) {
        this.mWhisperEngine = new WhisperEngineJava(context);

        // Start thread for RecordBuffer transcription
        Thread threadTranscbFile = new Thread(this::transcribeRecordBufferLoop);
        threadTranscbFile.start();

        // Start thread for buffer transcription for live mic feed transcription
        Thread threadTranscbBuffer = new Thread(this::transcribeBufferLoop);
        threadTranscbBuffer.start();
    }

    public void setListener(WhisperListener listener) {
        this.mUpdateListener = listener;
    }

    public void loadModel(File modelPath, File vocabPath, boolean isMultilingual) {
        loadModel(modelPath.getAbsolutePath(), vocabPath.getAbsolutePath(), isMultilingual);
        currentModelPath = modelPath.getAbsolutePath();
    }

    public void loadModel(String modelPath, String vocabPath, boolean isMultilingual) {
        try {
            mWhisperEngine.initialize(modelPath, vocabPath, isMultilingual);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing model...", e);
            sendUpdate("Model initialization failed");
        }
    }

    public String getCurrentModelPath(){
        return currentModelPath;
    }

    public void unloadModel() {
        mWhisperEngine.deinitialize();
        currentModelPath = "";
    }

    public void setAction(Action action) {
        this.mAction = action;
    }

    public void start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Execution is already in progress...");
            return;
        }
        taskLock.lock();
        try {
            taskAvailable = true;
            hasTask.signal();
        } finally {
            taskLock.unlock();
        }
    }

    public void stop() {
        mInProgress.set(false);
    }

    public boolean isInProgress() {
        return mInProgress.get();
    }

    private void transcribeRecordBufferLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            taskLock.lock();
            try {
                while (!taskAvailable) {
                    hasTask.await();
                }
                transcribeRecordBuffer();
                taskAvailable = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                taskLock.unlock();
            }
        }
    }

    private void transcribeRecordBuffer() {
        try {
            if (mWhisperEngine.isInitialized() && RecordBuffer.getOutputBuffer() != null) {
                long startTime = System.currentTimeMillis();
                sendUpdate(MSG_PROCESSING);

                String result = null;
                synchronized (mWhisperEngine) {
                    if (mAction == Action.TRANSCRIBE) {
                        result = mWhisperEngine.transcribeRecordBuffer();
                    } else {
                        Log.d(TAG, "TRANSLATE feature is not implemented");
                    }
                }
                sendResult(result);

                long timeTaken = System.currentTimeMillis() - startTime;
                Log.d(TAG, "Time Taken for transcription: " + timeTaken + "ms");
                sendUpdate(MSG_PROCESSING_DONE);
            } else {
                sendUpdate("Engine not initialized or file path not set");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during transcription", e);
            sendUpdate("Transcription failed: " + e.getMessage());
        } finally {
            mInProgress.set(false);
        }
    }

    private void sendUpdate(String message) {
        if (mUpdateListener != null) {
            mUpdateListener.onUpdateReceived(message);
        }
    }

    private void sendResult(String message) {
        if (mUpdateListener != null) {
            mUpdateListener.onResultReceived(message);
        }
    }

    /////////////////////// Live MIC feed transcription calls /////////////////////////////////
    private void transcribeBufferLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            float[] samples = readBuffer();
            if (samples != null) {
                synchronized (mWhisperEngine) {
                    String result = mWhisperEngine.transcribeBuffer(samples);
                    sendResult(result);
                }
            }
        }
    }

    public void writeBuffer(float[] samples) {
        synchronized (audioBufferQueue) {
            audioBufferQueue.add(samples);
            audioBufferQueue.notify();
        }
    }

    private float[] readBuffer() {
        synchronized (audioBufferQueue) {
            while (audioBufferQueue.isEmpty()) {
                try {
                    audioBufferQueue.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return audioBufferQueue.poll();
        }
    }
}
