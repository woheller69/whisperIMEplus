package com.whispertflite;

import static com.whispertflite.MainActivity.ENGLISH_ONLY_MODEL;
import static com.whispertflite.MainActivity.ENGLISH_ONLY_VOCAB_FILE;
import static com.whispertflite.MainActivity.MULTILINGUAL_VOCAB_FILE;
import static com.whispertflite.MainActivity.MULTI_LINGUAL_MODEL;
import static com.whispertflite.MainActivity.ENGLISH_ONLY_MODEL_EXTENSION;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;

import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.whispertflite.asr.Recorder;
import com.whispertflite.asr.Whisper;
import com.whispertflite.utils.WaveUtil;

import java.io.File;

public class WhisperInputMethodService extends InputMethodService {
    private ImageButton btnRecord;
    private ImageButton btnKeyboard;
    private ImageButton btnEnter;
    private TextView tvStatus;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private File sdcardDataFolder = null;
    private File recWavFile = null;
    private File selectedTfliteFile = null;
    private ProgressBar processingBar = null;
    private SharedPreferences sp = null;
    private boolean multiLingual;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

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
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        multiLingual = sp.getBoolean("multiLingual",true);

        selectedTfliteFile = new File(sdcardDataFolder, multiLingual ? MULTI_LINGUAL_MODEL : ENGLISH_ONLY_MODEL);
        recWavFile = new File(sdcardDataFolder+"/"+ WaveUtil.RECORDING_FILE);

        checkRecordPermission();

        // Audio recording functionality
        mRecorder = new Recorder(this);
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.equals(Recorder.MSG_RECORDING)) {
                    btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed);
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    btnRecord.setBackgroundResource(R.drawable.rounded_button_background);
                }
            }

            @Override
            public void onDataReceived(float[] samples) {

            }
        });

        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Pressed
                if (mWhisper != null) stopTranscription();
                startRecording();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Released
                if (mRecorder != null && mRecorder.isInProgress()) {
                    mRecorder.stop();
                    if (mWhisper == null)
                        initModel(selectedTfliteFile);
                    if (!mWhisper.isInProgress()) {
                        startTranscription(recWavFile.getAbsolutePath());
                    } else {
                        stopTranscription();
                    }

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
        mRecorder.setFilePath(recWavFile.getAbsolutePath());
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
            public void onResultReceived(String result) {
                processingBar.setIndeterminate(false);
                if (result.trim().length() > 0) getCurrentInputConnection().commitText(result.trim()+" ",1);
            }
        });
    }

    private void startTranscription(String waveFilePath) {
        mWhisper.setFilePath(waveFilePath);
        mWhisper.setAction(Whisper.ACTION_TRANSCRIBE);
        mWhisper.start();
        processingBar.setIndeterminate(true);
    }

    private void stopTranscription() {
        processingBar.setIndeterminate(false);
        mWhisper.stop();
    }

    private void checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(getString(R.string.need_record_audio_permission));
        }
    }
}