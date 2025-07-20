package com.whisperonnx;

import static android.speech.SpeechRecognizer.ERROR_CLIENT;
import static android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS;

import static com.whisperonnx.voice_translation.neural_networks.voice.Recognizer.ACTION_TRANSCRIBE;

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

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.whisperonnx.asr.Recorder;
import com.whisperonnx.asr.Whisper;
import com.whisperonnx.asr.WhisperResult;
import com.whisperonnx.utils.HapticFeedback;
import java.util.ArrayList;

public class WhisperRecognitionService extends RecognitionService {
    private static final String TAG = "WhisperRecognitionService";
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private boolean recognitionCancelled = false;
    private SharedPreferences sp = null;

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback callback) {
        String targetLang = recognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        String langCode = sp.getString("recognitionServiceLanguage", "auto");
        Log.d(TAG,"default langCode " + langCode);

        if (targetLang != null) {
            Log.d(TAG,"StartListening in " + targetLang);
            langCode = targetLang.split("[-_]")[0].toLowerCase();   //support both de_DE and de-DE
        } else {
            Log.d(TAG,"StartListening, no language specified");
        }

        checkRecordPermission(callback);

        initModel(callback, langCode);

        mRecorder = new Recorder(this);
        mRecorder.setListener(message -> {
            if (message.equals(Recorder.MSG_RECORDING)){
                try {
                    callback.rmsChanged(10);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                HapticFeedback.vibrate(this);
                try {
                    callback.rmsChanged(-20.0f);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
                startTranscription();
            } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                try {
                    callback.error(ERROR_CLIENT);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        if (!mWhisper.isInProgress()) {
            HapticFeedback.vibrate(this);
            startRecording();
            try {
                callback.beginningOfSpeech();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
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
        stopRecording();
    }

    // Model initialization
    private void initModel(Callback callback, String langCode) {

        mWhisper = new Whisper(this);
        mWhisper.loadModel();
        mWhisper.setLanguage(langCode);
        Log.d(TAG, "Language token " + langCode);
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

                        String result = whisperResult.getResult();
                        if (whisperResult.getLanguage().equals("zh")){
                            boolean simpleChinese = sp.getBoolean("RecognitionServiceSimpleChinese",false);
                            result = simpleChinese ? ZhConverterUtil.toSimple(result) : ZhConverterUtil.toTraditional(result);
                        }

                        resultList.add(result.trim());
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
        mRecorder.initVad();
        mRecorder.start();
        recognitionCancelled = false;
    }

    private void startTranscription() {
        if (!recognitionCancelled){
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(()-> {
                Toast toast = new Toast(this);
                toast.setDuration(Toast.LENGTH_SHORT);
                toast.setText(R.string.processing);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    toast.addCallback(new Toast.Callback() {
                        @Override
                        public void onToastHidden() {
                            super.onToastHidden();
                            if (mWhisper!=null) toast.show();
                        }
                    });
                }
                toast.show();
            });
            mWhisper.setAction(ACTION_TRANSCRIBE);
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
