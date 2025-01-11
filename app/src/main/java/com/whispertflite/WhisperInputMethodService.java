package com.whispertflite;

import static com.whispertflite.MainActivity.ENGLISH_ONLY_VOCAB_FILE;
import static com.whispertflite.MainActivity.MULTILINGUAL_VOCAB_FILE;
import static com.whispertflite.MainActivity.MULTI_LINGUAL_MODEL_SLOW;
import static com.whispertflite.MainActivity.ENGLISH_ONLY_MODEL_EXTENSION;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;

import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.whispertflite.asr.Recorder;
import com.whispertflite.asr.Whisper;
import com.whispertflite.asr.WhisperResult;

import java.io.File;

public class WhisperInputMethodService extends InputMethodService {
    private ImageButton btnRecord;
    private ImageButton btnKeyboard;
    private ImageButton btnEnter;
    private TextView tvStatus;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private File sdcardDataFolder = null;
    private File selectedTfliteFile = null;
    private ProgressBar processingBar = null;
    private SharedPreferences sp = null;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        deinitModel();
        super.onDestroy();
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting){
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        selectedTfliteFile = new File(sdcardDataFolder, sp.getString("modelName", MULTI_LINGUAL_MODEL_SLOW));

        if (mWhisper == null)
            initModel(selectedTfliteFile);
        else {
            if (!mWhisper.getCurrentModelPath().equals(selectedTfliteFile.getAbsolutePath())){
                deinitModel();
                initModel(selectedTfliteFile);
            }
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateInputView() {
        View view = getLayoutInflater().inflate(R.layout.voice_service, null);
        btnRecord = view.findViewById(R.id.btnRecord);
        btnKeyboard = view.findViewById(R.id.btnKeyboard);
        btnEnter = view.findViewById(R.id.btnEnter);
        processingBar = view.findViewById(R.id.processing_bar);
        tvStatus = view.findViewById(R.id.tv_status);
        sdcardDataFolder = this.getExternalFilesDir(null);

        checkRecordPermission();

        // Audio recording functionality
        mRecorder = new Recorder(this);
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.equals(Recorder.MSG_RECORDING)) {
                    handler.post(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    handler.post(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));
                    startTranscription();
                }
            }

        });

        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Pressed
                handler.post(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                if (checkRecordPermission()){
                    if (!mWhisper.isInProgress()) {
                        startRecording();
                        handler.post(() -> tvStatus.setText(""));
                    } else {
                        handler.post(() -> tvStatus.setText(getString(R.string.please_wait)));
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Released
                handler.post(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));
                if (mRecorder != null && mRecorder.isInProgress()) {
                    mRecorder.stop();
                }
            }
            return true;
        });

        btnKeyboard.setOnClickListener(v -> {
            if (mWhisper != null) stopTranscription();
            switchToPreviousInputMethod();
        });

        btnEnter.setOnClickListener(v -> {
            getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        });

        return view;
    }

    private void startRecording() {
        mRecorder.start();
    }

    // Model initialization
    private void initModel(File modelFile) {
        boolean isMultilingualModel = !(modelFile.getName().endsWith(ENGLISH_ONLY_MODEL_EXTENSION));
        String vocabFileName = isMultilingualModel ? MULTILINGUAL_VOCAB_FILE : ENGLISH_ONLY_VOCAB_FILE;
        File vocabFile = new File(sdcardDataFolder, vocabFileName);

        mWhisper = new Whisper(this);
        mWhisper.loadModel(modelFile, vocabFile, isMultilingualModel);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
            }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                handler.post(() -> processingBar.setIndeterminate(false));
                handler.post(() -> tvStatus.setText(""));

                if (whisperResult.getResult().trim().length() > 0) getCurrentInputConnection().commitText(whisperResult.getResult().trim() + " ",1);
            }
        });
    }

    private void startTranscription() {
        mWhisper.setAction(Whisper.ACTION_TRANSCRIBE);
        mWhisper.start();
        handler.post(() -> processingBar.setIndeterminate(true));
    }

    private void stopTranscription() {
        handler.post(() -> processingBar.setIndeterminate(false));
        mWhisper.stop();
    }

    private boolean checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            tvStatus.setText(getString(R.string.need_record_audio_permission));
        }
        return (permission == PackageManager.PERMISSION_GRANTED);
    }

    private void deinitModel() {
        if (mWhisper != null) {
            mWhisper.unloadModel();
            mWhisper = null;
        }
    }
}