package com.whispertflite.asr;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.konovalov.vad.silero.Vad;
import com.konovalov.vad.silero.VadSilero;
import com.konovalov.vad.silero.config.FrameSize;
import com.konovalov.vad.silero.config.Mode;
import com.konovalov.vad.silero.config.SampleRate;
import com.whispertflite.R;

import java.io.ByteArrayOutputStream;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Recorder {

    public interface RecorderListener {
        void onUpdateReceived(String message);
    }

    private static final String TAG = "Recorder";
    public static final String ACTION_STOP = "Stop";
    public static final String ACTION_RECORD = "Record";
    public static final String MSG_RECORDING = "Recording...";
    public static final String MSG_RECORDING_DONE = "Recording done...!";

    private final Context mContext;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    private RecorderListener mListener;
    private final Lock lock = new ReentrantLock();
    private final Condition hasTask = lock.newCondition();
    private final Object fileSavedLock = new Object(); // Lock object for wait/notify

    private volatile boolean shouldStartRecording = false;
    private boolean useVAD = false;
    private VadSilero vad = null;
    private static final int VAD_FRAME_SIZE = 512;

    private final Thread workerThread;

    public Recorder(Context context) {
        this.mContext = context;

        // Initialize and start the worker thread
        workerThread = new Thread(this::recordLoop);
        workerThread.start();
    }

    public void setListener(RecorderListener listener) {
        this.mListener = listener;
    }


    public void start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Recording is already in progress...");
            return;
        }
        lock.lock();
        try {
            shouldStartRecording = true;
            hasTask.signal();
        } finally {
            lock.unlock();
        }
    }

    public void initVad(){
        useVAD = true;
        vad = Vad.builder()
                .setContext(mContext)
                .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                .setFrameSize(FrameSize.FRAME_SIZE_512)
                .setMode(Mode.NORMAL)
                .setSilenceDurationMs(800)
                .setSpeechDurationMs(200)
                .build();
    }


    public void stop() {
        mInProgress.set(false);

        // Wait for the recording thread to finish
        synchronized (fileSavedLock) {
            try {
                fileSavedLock.wait(); // Wait until notified by the recording thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }
    }

    public boolean isInProgress() {
        return mInProgress.get();
    }

    private void sendUpdate(String message) {
        if (mListener != null)
            mListener.onUpdateReceived(message);
    }


    private void recordLoop() {
        while (true) {
            lock.lock();
            try {
                while (!shouldStartRecording) {
                    hasTask.await();
                }
                shouldStartRecording = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }

            // Start recording process
            try {
                recordAudio();
            } catch (Exception e) {
                Log.e(TAG, "Recording error...", e);
                sendUpdate(e.getMessage());
            } finally {
                mInProgress.set(false);
            }
        }
    }

    private void recordAudio() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "AudioRecord permission is not granted");
            sendUpdate(mContext.getString(R.string.need_record_audio_permission));
            return;
        }

        int channels = 1;
        int bytesPerSample = 2;
        int sampleRateInHz = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;

        int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        if (bufferSize < VAD_FRAME_SIZE * 2) bufferSize = VAD_FRAME_SIZE * 2;
        AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);
        audioRecord.startRecording();

        // Calculate maximum byte counts for 30 seconds (for saving)
        int bytesForThirtySeconds = sampleRateInHz * bytesPerSample * channels * 30;

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream(); // Buffer for saving data RecordBuffer

        byte[] audioData = new byte[bufferSize];
        int totalBytesRead = 0;

        boolean isSpeech;
        boolean isRecording = false;
        byte[] vadAudioBuffer = new byte[VAD_FRAME_SIZE * 2];  //VAD FRAME_SIZE_512 needs 1024 bytes (16 bit)

        while (mInProgress.get() && totalBytesRead < bytesForThirtySeconds) {
            int bytesRead = audioRecord.read(audioData, 0, VAD_FRAME_SIZE * 2);
            if (bytesRead > 0) {
                outputBuffer.write(audioData, 0, bytesRead);  // Save all bytes read up to 30 seconds
                totalBytesRead += bytesRead;

            } else {
                Log.d(TAG, "AudioRecord error, bytes read: " + bytesRead);
                break;
            }

            if (useVAD){
                byte[] outputBufferByteArray = outputBuffer.toByteArray();
                if (outputBufferByteArray.length >= VAD_FRAME_SIZE * 2) {
                    // Always use the last 1024 bytes from outputBuffer for VAD
                    System.arraycopy(outputBufferByteArray, outputBufferByteArray.length - VAD_FRAME_SIZE * 2, vadAudioBuffer, 0, VAD_FRAME_SIZE * 2);

                    isSpeech = vad.isSpeech(vadAudioBuffer);
                    if (isSpeech) {
                        if (!isRecording) sendUpdate(MSG_RECORDING);
                        isRecording = true;
                    } else {
                        if (isRecording) {
                            vad.close();
                            useVAD = false;
                            vad = null;
                            isRecording = false;
                            mInProgress.set(false);
                        }
                    }
                }
            } else {
                if (!isRecording) sendUpdate(MSG_RECORDING);
                isRecording = true;
            }
        }

        audioRecord.stop();
        audioRecord.release();

        // Save recorded audio data to BufferStore (up to 30 seconds)
        RecordBuffer.setOutputBuffer(outputBuffer.toByteArray());
        sendUpdate(MSG_RECORDING_DONE);

        // Notify the waiting thread that recording is complete
        synchronized (fileSavedLock) {
            fileSavedLock.notify(); // Notify that recording is finished
        }

    }

}
