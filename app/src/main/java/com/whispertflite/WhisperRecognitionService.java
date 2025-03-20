package com.whispertflite;

import static android.speech.SpeechRecognizer.ERROR_CLIENT;
import static android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS;
import static android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE;
import static com.whispertflite.MainActivity.ENGLISH_ONLY_MODEL_EXTENSION;
import static com.whispertflite.MainActivity.ENGLISH_ONLY_VOCAB_FILE;
import static com.whispertflite.MainActivity.MULTILINGUAL_VOCAB_FILE;
import static com.whispertflite.MainActivity.MULTI_LINGUAL_MODEL_SLOW;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.whispertflite.asr.Recorder;
import com.whispertflite.asr.Whisper;
import com.whispertflite.asr.WhisperResult;
import com.whispertflite.utils.InputLang;

import java.io.File;
import java.util.ArrayList;

public class WhisperRecognitionService extends RecognitionService {
    private static final String TAG = "WhisperRecognitionService";
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private File sdcardDataFolder = null;
    private File selectedTfliteFile = null;
    private boolean recognitionCancelled = false;
    private SharedPreferences sp = null;
    private Handler recordingTimeHandler;

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback callback) {
        String targetLang = recognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        String langCode = sp.getString("recognitionServiceLanguage", "auto");
        int langToken = InputLang.getIdForLanguage(InputLang.getLangList(),langCode);
        Log.d(TAG,"default langToken " + langToken);

        if (targetLang != null) {
            Log.d(TAG,"StartListening in " + targetLang);
            langCode = targetLang.split("[-_]")[0].toLowerCase();   //support both de_DE and de-DE
            langToken = InputLang.getIdForLanguage(InputLang.getLangList(),langCode);
        } else {
            Log.d(TAG,"StartListening, no language specified");
        }

        checkRecordPermission(callback);

        int maxRecording_time = sp.getInt("recognitionServiceMaxRecordingTime", 30);
        sdcardDataFolder = this.getExternalFilesDir(null);
        selectedTfliteFile = new File(sdcardDataFolder, sp.getString("recognitionServiceModelName", MULTI_LINGUAL_MODEL_SLOW));

        if (!selectedTfliteFile.exists()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    callback.error(ERROR_LANGUAGE_UNAVAILABLE);
                } else {
                    callback.error(ERROR_CLIENT);
                }
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        } else {
            initModel(selectedTfliteFile, callback, langToken);

            mRecorder = new Recorder(this);
            mRecorder.setListener(message -> {
                if (message.equals(Recorder.MSG_RECORDING)){
                    try {
                        callback.rmsChanged(10);
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    try {
                        callback.rmsChanged(-20.0f);
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                    startTranscription();
                }
            });

            if (!mWhisper.isInProgress()) {
                startRecording();
                try {
                    callback.beginningOfSpeech();
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
                if (recordingTimeHandler != null) { recordingTimeHandler.removeCallbacksAndMessages(null); }
                recordingTimeHandler = new Handler(Looper.getMainLooper());
                recordingTimeHandler.postDelayed(() -> {
                    Log.d(TAG,"Reached max recording time");
                    stopRecording();
                }, maxRecording_time * 1000L);
            }
        }

    }

    private void stopRecording() {
        if (mRecorder != null && mRecorder.isInProgress()) {
            mRecorder.stop();
        }
    }

    @Override
    protected void onCancel(Callback callback) {
        Log.d(TAG,"cancel");
        stopRecording();
        deinitModel();
        recognitionCancelled = true;
    }

    @Override
    protected void onStopListening(Callback callback) {
        Log.d(TAG,"StopListening");
        deinitModel();
        stopRecording();
    }

    // Model initialization
    private void initModel(File modelFile, Callback callback, int langToken) {
        boolean isMultilingualModel = !(modelFile.getName().endsWith(ENGLISH_ONLY_MODEL_EXTENSION));
        String vocabFileName = isMultilingualModel ? MULTILINGUAL_VOCAB_FILE : ENGLISH_ONLY_VOCAB_FILE;
        File vocabFile = new File(sdcardDataFolder, vocabFileName);

        mWhisper = new Whisper(this);
        mWhisper.loadModel(modelFile, vocabFile, isMultilingualModel);
        Log.d(TAG, "Initialized: " + modelFile.getName());
        mWhisper.setLanguage(langToken);
        Log.d(TAG, "Language token " + langToken);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) { }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                if (whisperResult.getResult().trim().length() > 0){
                    Log.d(TAG, whisperResult.getResult().trim());
                    try {
                        callback.endOfSpeech();
                        deinitModel();
                        Bundle results = new Bundle();
                        ArrayList<String> resultList = new ArrayList<>();
                        resultList.add(whisperResult.getResult().trim());
                        results.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, resultList);
                        callback.results(results);
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    private void startRecording() {
        if (sp.getBoolean("voiceActivityDetection",true)) {
            mRecorder.initVad();
        }
        mRecorder.start();
        recognitionCancelled = false;
    }

    private void startTranscription() {
        if (!recognitionCancelled){
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(()-> Toast.makeText(this,getString(R.string.processing), Toast.LENGTH_SHORT).show());
            mWhisper.setAction(Whisper.ACTION_TRANSCRIBE);
            mWhisper.start();
            Log.d(TAG,"Start Transcription");
        }
    }

    @Override
    public void onDestroy (){
        deinitModel();
    }
    private void deinitModel() {
        if (mWhisper != null) {
            mWhisper.unloadModel();
            mWhisper = null;
        }
    }

    private void checkRecordPermission(Callback callback) {
        int permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED){
            Log.d(TAG,getString(R.string.need_record_audio_permission));
            try {
                callback.error(ERROR_INSUFFICIENT_PERMISSIONS);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
