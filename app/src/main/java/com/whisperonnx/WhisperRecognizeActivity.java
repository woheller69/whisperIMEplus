package com.whisperonnx;

import static com.whisperonnx.voice_translation.neural_networks.voice.Recognizer.ACTION_TRANSCRIBE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.whisperonnx.asr.Recorder;
import com.whisperonnx.asr.Whisper;
import com.whisperonnx.asr.WhisperResult;
import com.whisperonnx.utils.HapticFeedback;

import java.util.ArrayList;

public class WhisperRecognizeActivity extends AppCompatActivity {
    private static final String TAG = "WhisperRecognizeActivity";
    private ImageButton btnRecord;
    private ImageButton btnCancel;
    private ImageButton btnModeAuto;
    private ProgressBar processingBar = null;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private SharedPreferences sp = null;
    private Context mContext;
    private CountDownTimer countDownTimer;
    private boolean modeAuto = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        sp = PreferenceManager.getDefaultSharedPreferences(this);

        String targetLang = getIntent().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        String langCode = sp.getString("language", "auto");
        Log.d("WhisperRecognition","default langCode " + langCode);

        if (targetLang != null) {
            Log.d("WhisperRecognition","StartListening in " + targetLang);
            langCode = targetLang.split("[-_]")[0].toLowerCase();  //support both de_DE and de-DE
        } else {
            Log.d("WhisperRecognition","StartListening, no language specified");
        }

        initModel(langCode);

        setContentView(R.layout.activity_recognize);

        // Set the window layout parameters
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height =  WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.BOTTOM; // Position at the bottom of the screen

        btnCancel = findViewById(R.id.btnCancel);
        btnRecord = findViewById(R.id.btnRecord);
        btnModeAuto = findViewById(R.id.btnModeAuto);
        processingBar = findViewById(R.id.processing_bar);

        modeAuto = sp.getBoolean("imeModeAuto",false);
        btnModeAuto.setImageResource(modeAuto ? R.drawable.ic_auto_on_36dp : R.drawable.ic_auto_off_36dp);

        // Audio recording functionality
        mRecorder = new Recorder(this);
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.equals(Recorder.MSG_RECORDING)) {
                    runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    HapticFeedback.vibrate(mContext);
                    runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));
                    startTranscription();
                } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                    HapticFeedback.vibrate(mContext);
                    if (countDownTimer!=null) { countDownTimer.cancel();}
                    runOnUiThread(() -> {
                        btnRecord.setBackgroundResource(R.drawable.rounded_button_background);
                        processingBar.setProgress(0);
                        Toast.makeText(mContext,R.string.error_no_input,Toast.LENGTH_SHORT).show();
                    });
                }
            }

        });

        if (modeAuto) {
            btnRecord.setVisibility(View.GONE);
            HapticFeedback.vibrate(this);
            startRecording();
            runOnUiThread(() -> processingBar.setProgress(100));
            countDownTimer = new CountDownTimer(30000, 1000) {
                @Override
                public void onTick(long l) {
                    runOnUiThread(() -> processingBar.setProgress((int) (l / 300)));
                }
                @Override
                public void onFinish() {}
            };
            countDownTimer.start();
        }

        btnModeAuto.setOnClickListener(v -> {
            modeAuto = !modeAuto;
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean("imeModeAuto", modeAuto);
            editor.apply();
            btnRecord.setVisibility(modeAuto ? View.GONE : View.VISIBLE);
            btnModeAuto.setImageResource(modeAuto ? R.drawable.ic_auto_on_36dp : R.drawable.ic_auto_off_36dp);
            if (mWhisper != null) stopTranscription();
            setResult(RESULT_CANCELED, null);
            finish();
        });

        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Pressed
                runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                if (checkRecordPermission()){
                    if (!mWhisper.isInProgress()) {
                        HapticFeedback.vibrate(this);
                        startRecording();
                        runOnUiThread(() -> processingBar.setProgress(100));
                        countDownTimer = new CountDownTimer(30000, 1000) {
                            @Override
                            public void onTick(long l) {
                                runOnUiThread(() -> processingBar.setProgress((int) (l / 300)));
                            }
                            @Override
                            public void onFinish() {}
                        };
                        countDownTimer.start();
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, getString(R.string.please_wait),Toast.LENGTH_SHORT).show());
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Released
                runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));
                if (mRecorder != null && mRecorder.isInProgress()) {
                    mRecorder.stop();
                }
            }
            return true;
        });

        btnCancel.setOnClickListener(v -> {
            if (mWhisper != null) stopTranscription();
            setResult(RESULT_CANCELED, null);
            finish();
        });

    }
    private void startRecording() {
        if (modeAuto) mRecorder.initVad();
        mRecorder.start();
    }

    // Model initialization
    private void initModel(String langCode) {

        mWhisper = new Whisper(this);
        mWhisper.loadModel();
        mWhisper.setLanguage(langCode);
        Log.d(TAG, "Language code " + langCode);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) { }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                runOnUiThread(() -> processingBar.setIndeterminate(false));

                String result = whisperResult.getResult();
                if (whisperResult.getLanguage().equals("zh")){
                    boolean simpleChinese = sp.getBoolean("simpleChinese",false);
                    result = simpleChinese ? ZhConverterUtil.toSimple(result) : ZhConverterUtil.toTraditional(result);
                }
                if (result.trim().length() > 0){
                    sendResult(result.trim());
                }
            }
        });
    }

    private void sendResult(String result) {
        Intent sendResultIntent = new Intent();
        ArrayList<String> results = new ArrayList<>();
        results.add(result);
        sendResultIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results);
        sendResultIntent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, new float[]{1.0f});
        setResult(RESULT_OK, sendResultIntent);
        finish();
    }

    private void startTranscription() {
        if (countDownTimer!=null) { countDownTimer.cancel();}
        runOnUiThread(() -> {
            processingBar.setProgress(0);
            processingBar.setIndeterminate(true);
        });
        if (mWhisper!=null){
            mWhisper.setAction(ACTION_TRANSCRIBE);
            mWhisper.start();
            Log.d(TAG,"Start Transcription");
        }
    }

    private void stopTranscription() {
        runOnUiThread(() -> processingBar.setIndeterminate(false));
        mWhisper.stop();
    }

    private boolean checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.need_record_audio_permission),Toast.LENGTH_SHORT).show();
        }
        return (permission == PackageManager.PERMISSION_GRANTED);
    }

    private void deinitModel() {
        if (mWhisper != null) {
            mWhisper.unloadModel();
            mWhisper = null;
        }
    }

    @Override
    public void onDestroy() {
        deinitModel();
        if (mRecorder != null && mRecorder.isInProgress()) {
            mRecorder.stop();
        }
        super.onDestroy();
    }
}
