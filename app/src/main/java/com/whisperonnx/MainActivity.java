package com.whisperonnx;

import static com.whisperonnx.voice_translation.neural_networks.voice.Recognizer.ACTION_TRANSCRIBE;
import static com.whisperonnx.voice_translation.neural_networks.voice.Recognizer.ACTION_TRANSLATE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.whisperonnx.asr.Recorder;
import com.whisperonnx.asr.Whisper;
import com.whisperonnx.asr.WhisperResult;
import com.whisperonnx.utils.HapticFeedback;
import com.whisperonnx.utils.LanguagePairAdapter;
import com.whisperonnx.voice_translation.neural_networks.voice.Recognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private Context mContext;
    private static final String TAG = "MainActivity";

    private TextView tvStatus;
    private TextView tvResult;
    private FloatingActionButton fabCopy;
    private ImageButton btnRecord;
    private LinearLayout layoutModeChinese;
    private LinearLayout layoutTTS;
    private CheckBox append;
    private CheckBox translate;
    private CheckBox modeSimpleChinese;
    private CheckBox modeTTS;
    private ProgressBar processingBar;
    private ImageButton btnInfo;

    private Recorder mRecorder = null;
    private Whisper mWhisper = null;

    private SharedPreferences sp = null;

    private CountDownTimer countDownTimer;
    private Spinner spinnerLanguage;
    private String langCode = "";
    private long startTime = 0;
    private TextToSpeech tts;

    @Override
    protected void onDestroy() {
        deinitModel();
        deinitTTS();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        stopProcessing();
        super.onPause();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        checkInputMethodEnabled();
        processingBar = findViewById(R.id.processing_bar);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        append = findViewById(R.id.mode_append);

        layoutTTS = findViewById(R.id.layout_tts);
        modeTTS = findViewById(R.id.mode_tts);
        modeTTS.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (isChecked) {
                tts = new TextToSpeech(mContext, status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = tts.setLanguage(Locale.US);
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            runOnUiThread(() -> {
                                Toast.makeText(mContext, mContext.getString(R.string.tts_language_not_supported),Toast.LENGTH_SHORT).show();
                                modeTTS.setChecked(false);
                            });

                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(mContext, mContext.getString(R.string.tts_initialization_failed),Toast.LENGTH_SHORT).show());
                    }
                });
            } else {
                deinitTTS();
            }
        });

        translate = findViewById(R.id.mode_translate);
        translate.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            layoutTTS.setVisibility(isChecked ? View.VISIBLE:View.GONE);
            if (layoutTTS.getVisibility() == View.GONE) modeTTS.setChecked(false);
        });


        // Initialize default model to use
        initModel();

        btnInfo = findViewById(R.id.btnInfo);
        btnInfo.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/woheller69/whisperIMEplus#Donate"))));

        spinnerLanguage = findViewById(R.id.spnrLanguage);

        List<Pair<String, String>> languagePairs = LanguagePairAdapter.getLanguagePairs(this);
        LanguagePairAdapter languagePairAdapter = new LanguagePairAdapter(this, android.R.layout.simple_spinner_item, languagePairs);
        languagePairAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(languagePairAdapter);
        langCode = sp.getString("language", "auto");
        spinnerLanguage.setSelection(languagePairAdapter.getIndexByCode(langCode));

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                langCode = languagePairs.get(i).first;
                SharedPreferences.Editor editor = sp.edit();
                editor.putString("language",languagePairs.get(i).first);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        // Implementation of record button functionality
        btnRecord = findViewById(R.id.btnRecord);

        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Pressed
                runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                Log.d(TAG, "Start recording...");
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
                } else (Toast.makeText(this,getString(R.string.please_wait),Toast.LENGTH_SHORT)).show();

            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Released
                runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));
                if (mRecorder != null && mRecorder.isInProgress()) {
                    Log.d(TAG, "Recording is in progress... stopping...");
                    stopRecording();
                }
            }
            return true;
        });

        layoutModeChinese = findViewById(R.id.layout_mode_chinese);
        modeSimpleChinese = findViewById(R.id.mode_simple_chinese);
        modeSimpleChinese.setChecked(sp.getBoolean("simpleChinese",false));  //default to traditional Chinese
        modeSimpleChinese.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean("simpleChinese", isChecked);
            editor.apply();
            tvResult.setText("");
        });

        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        fabCopy = findViewById(R.id.fabCopy);
        fabCopy.setOnClickListener(v -> {
            // Get the text from tvResult
            String textToCopy = tvResult.getText().toString().trim();

            // Copy the text to the clipboard
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(getString(R.string.model_output), textToCopy);
            clipboard.setPrimaryClip(clip);
        });

        // Audio recording functionality
        mRecorder = new Recorder(this);
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Update is received, Message: " + message);
                if (message.equals(Recorder.MSG_RECORDING)) {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.record_button) +"…"));
                    if (!append.isChecked()) runOnUiThread(() -> tvResult.setText(""));
                    runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    HapticFeedback.vibrate(mContext);
                    runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));

                    if (translate.isChecked()) startProcessing(ACTION_TRANSLATE);
                    else startProcessing(ACTION_TRANSCRIBE);
                } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                    HapticFeedback.vibrate(mContext);
                    if (countDownTimer!=null) { countDownTimer.cancel();}
                    runOnUiThread(() -> {
                        btnRecord.setBackgroundResource(R.drawable.rounded_button_background);
                        processingBar.setProgress(0);
                        tvStatus.setText(getString(R.string.error_no_input));
                    });
                }
            }

        });

        if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(this, "https://github.com/woheller69/whisperIMEplus");
        // Assume this Activity is the current activity, check record permission
        checkPermissions();

    }

    private void checkInputMethodEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> enabledInputMethodList = imm.getEnabledInputMethodList();

        String myInputMethodId = getPackageName() + "/" + WhisperInputMethodService.class.getName();
        boolean inputMethodEnabled = false;
        for (InputMethodInfo imi : enabledInputMethodList) {
            if (imi.getId().equals(myInputMethodId)) {
                inputMethodEnabled = true;
                break;
            }
        }
        if (!inputMethodEnabled) {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        }
    }

    // Model initialization
    private void initModel() {
        mWhisper = new Whisper(this);
        mWhisper.loadModel();
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Update is received, Message: " + message);

                if (message.equals(Whisper.MSG_PROCESSING)) {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.processing)));
                    startTime = System.currentTimeMillis();
                }
            }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                long timeTaken = System.currentTimeMillis() - startTime;
                runOnUiThread(() -> tvStatus.setText(getString(R.string.processing_done) + timeTaken + "\u2009ms" + "\n"+ getString(R.string.language) + whisperResult.getLanguage().toUpperCase() + " " + (whisperResult.getTask() == ACTION_TRANSCRIBE ? getString(R.string.mode_transcription) : getString(R.string.mode_translation))));
                runOnUiThread(() -> processingBar.setIndeterminate(false));
                Log.d(TAG, "Result: " + whisperResult.getResult() + " " + whisperResult.getLanguage() + " " + (whisperResult.getTask() == ACTION_TRANSCRIBE ? "transcribing" : "translating"));
                if ((whisperResult.getLanguage().equals("zh")) && (whisperResult.getTask() == ACTION_TRANSCRIBE)){
                    runOnUiThread(() -> layoutModeChinese.setVisibility(View.VISIBLE));
                    boolean simpleChinese = sp.getBoolean("simpleChinese",false);  //convert to desired Chinese mode
                    String result = simpleChinese ? ZhConverterUtil.toSimple(whisperResult.getResult()) : ZhConverterUtil.toTraditional(whisperResult.getResult());
                    runOnUiThread(() -> tvResult.append(result));
                } else {
                    runOnUiThread(() -> layoutModeChinese.setVisibility(View.GONE));
                    runOnUiThread(() -> tvResult.append(whisperResult.getResult()));
                }
                if (modeTTS.isChecked()){
                    tts.speak(whisperResult.getResult(), TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        });
    }

    private void deinitModel() {
        if (mWhisper != null) {
            mWhisper.unloadModel();
            mWhisper = null;
        }
    }

    private void deinitTTS(){
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private void checkPermissions() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO);
            Toast.makeText(this, getString(R.string.need_record_audio_permission), Toast.LENGTH_SHORT).show();
        }
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)){
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!perms.isEmpty()) {
            requestPermissions(perms.toArray(new String[] {}), 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        } else {
            Log.d(TAG, "Record permission is not granted");
        }
    }

    // Recording calls
    private void startRecording() {
        checkPermissions();
        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
    }

    // Transcription calls
    private void startProcessing(Recognizer.Action action) {
        if (countDownTimer!=null) { countDownTimer.cancel();}
        runOnUiThread(() -> {
            processingBar.setProgress(0);
            processingBar.setIndeterminate(true);
        });
        mWhisper.setAction(action);
        mWhisper.setLanguage(langCode);
        mWhisper.start();
    }

    private void stopProcessing() {
        processingBar.setIndeterminate(false);
        if (mWhisper != null && mWhisper.isInProgress()) mWhisper.stop();
    }

}