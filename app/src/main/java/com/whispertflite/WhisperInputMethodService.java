package com.whispertflite;

import static com.whispertflite.MainActivity.ENGLISH_ONLY_VOCAB_FILE;
import static com.whispertflite.MainActivity.MULTILINGUAL_VOCAB_FILE;
import static com.whispertflite.MainActivity.MULTI_LINGUAL_MODEL_SLOW;
import static com.whispertflite.MainActivity.ENGLISH_ONLY_MODEL_EXTENSION;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.Log;
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
import com.whispertflite.utils.HapticFeedback;

import java.io.File;

public class WhisperInputMethodService extends InputMethodService {
    private static final String TAG = "WhisperInputMethodService";
    private ImageButton btnRecord;
    private ImageButton btnKeyboard;
    private ImageButton btnEnter;
    private ImageButton btnDel;
    private TextView tvStatus;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private File sdcardDataFolder = null;
    private File selectedTfliteFile = null;
    private ProgressBar processingBar = null;
    private SharedPreferences sp = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context mContext;
    private CountDownTimer countDownTimer;

    @Override
    public void onCreate() {
        mContext = this;
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
        btnDel = view.findViewById(R.id.btnDel);
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
                    HapticFeedback.vibrate(mContext);
                    handler.post(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));
                    startTranscription();
                }
            }

        });

        btnDel.setOnTouchListener(new View.OnTouchListener() {
            private Runnable initialDeleteRunnable;
            private Runnable repeatDeleteRunnable;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                    // Post the initial delay of 500ms
                    initialDeleteRunnable = new Runnable() {
                        @Override
                        public void run() {
                            getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                            // Start repeating every 100ms
                            repeatDeleteRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                                    handler.postDelayed(this, 100);
                                }
                            };
                            handler.postDelayed(repeatDeleteRunnable, 100);
                        }
                    };
                    handler.postDelayed(initialDeleteRunnable, 500);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    // Remove both callbacks
                    if (initialDeleteRunnable != null) {
                        handler.removeCallbacks(initialDeleteRunnable);
                    }
                    if (repeatDeleteRunnable != null) {
                        handler.removeCallbacks(repeatDeleteRunnable);
                    }
                    initialDeleteRunnable = null;
                    repeatDeleteRunnable = null;
                }
                return true;
            }
        });

        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Pressed
                handler.post(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                if (checkRecordPermission()){
                    if (!mWhisper.isInProgress()) {
                        HapticFeedback.vibrate(this);
                        startRecording();
                        handler.post(() -> processingBar.setProgress(100));
                        countDownTimer = new CountDownTimer(30000, 1000) {
                            @Override
                            public void onTick(long l) {
                                handler.post(() -> processingBar.setProgress((int) (l / 300)));
                            }
                            @Override
                            public void onFinish() {}
                        };
                        countDownTimer.start();
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
        Log.d(TAG, "Initialized: " + modelFile.getName());
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
        if (countDownTimer!=null) { countDownTimer.cancel();}
        handler.post(() -> processingBar.setProgress(0));
        handler.post(() -> processingBar.setIndeterminate(true));
        mWhisper.setAction(Whisper.ACTION_TRANSCRIBE);
        mWhisper.start();
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