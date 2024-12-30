package com.whispertflite.utils;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.whispertflite.R;
import com.whispertflite.databinding.ActivityDownloadBinding;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


@SuppressWarnings("ResultOfMethodCallIgnored")
public class Downloader {
    static final String modelMultiLingual = "whisper-small.tflite";
    static final String modelEnglishOnly = "whisper-tiny.en.tflite";
    static final String modelMultiLingualURL = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-small.tflite";
    static final String modelEnglishOnlyURL = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-tiny.en.tflite";
    static final String modelMultiLingualMD5 = "7b10527f410230cf09b553da0213bb6c";
    static final String modelEnglishOnlyMD5 ="2e745cdd5dfe2f868f47caa7a199f91a";
    static final long modelEnglishOnlySize = 41486616;
    static final long modelMultiLingualSize = 387698368;
    static long downloadModelEnglishOnlySize = 0L;
    static long downloadModelMultiLingualSize = 0L;
    static boolean modelEnglishOnlyFinished = false;
    static boolean modelMultiLingualFinished = false;

    public static boolean checkModels(final Activity activity) {
        File modelMultiLingualFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingual);
        File modelEnglishOnlyFile = new File(activity.getExternalFilesDir(null) + "/" + modelEnglishOnly);
        String calcModelMultiLingualMD5 = "";
        String calcModelEnglishOnlyMD5 = "";
        if (modelMultiLingualFile.exists()) {
            try {
                calcModelMultiLingualMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualFile.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        if (modelEnglishOnlyFile.exists()) {
            try {
                calcModelEnglishOnlyMD5 = calculateMD5(String.valueOf(Paths.get(modelEnglishOnlyFile.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        if (modelMultiLingualFile.exists() && !(calcModelMultiLingualMD5.equals(modelMultiLingualMD5))) { modelMultiLingualFile.delete(); modelMultiLingualFinished = false; }
        if (modelEnglishOnlyFile.exists() && !calcModelEnglishOnlyMD5.equals(modelEnglishOnlyMD5)) { modelEnglishOnlyFile.delete(); modelEnglishOnlyFinished = false; }

        return (calcModelMultiLingualMD5.equals(modelMultiLingualMD5)) && calcModelEnglishOnlyMD5.equals(modelEnglishOnlyMD5);
    }

    public static void downloadModels(final Activity activity, ActivityDownloadBinding binding) {
        binding.downloadProgress.setProgress(0);
        File modelMultiLingualFile = new File(activity.getExternalFilesDir(null)+ "/" + modelMultiLingual);
        if (!modelMultiLingualFile.exists()) {
            modelMultiLingualFinished = false;
            Log.d("WhisperASR", "multi-lingual model file does not exist");
            Thread thread = new Thread(() -> {
                try {
                    URL url;

                    url = new URL(modelMultiLingualURL);

                    Log.d("WhisperASR", "Download model");

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    modelMultiLingualFile.createNewFile();

                    FileOutputStream outStream = new FileOutputStream(modelMultiLingualFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (modelMultiLingualFile.exists()) downloadModelMultiLingualSize = modelMultiLingualFile.length();
                        activity.runOnUiThread(() -> {
                            binding.downloadSize.setText((downloadModelEnglishOnlySize+downloadModelMultiLingualSize)/1024/1024 + " MB");
                            binding.downloadProgress.setProgress((int) (((double)(downloadModelEnglishOnlySize + downloadModelMultiLingualSize) / (modelEnglishOnlySize + modelMultiLingualSize)) * 100));
                        });
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();
                    String calcModelMultiLingualMD5="";
                    if (modelMultiLingualFile.exists()) {
                        calcModelMultiLingualMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualFile.getPath())));
                    } else {
                        throw new IOException();  //throw exception if there is no modelMultiLingualFile at this point
                    }

                    if (!(calcModelMultiLingualMD5.equals(modelMultiLingualMD5))){
                        modelMultiLingualFile.delete();
                        modelMultiLingualFinished = false;
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        modelMultiLingualFinished = true;
                        activity.runOnUiThread(() -> {
                            if (modelEnglishOnlyFinished && modelMultiLingualFinished) binding.buttonStart.setVisibility(View.VISIBLE);
                        });
                    }
                } catch (NoSuchAlgorithmException | IOException i) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                    modelMultiLingualFile.delete();
                    Log.w("WhisperASR", activity.getResources().getString(R.string.error_download), i);
                }
            });
            thread.start();
        } else {
            downloadModelMultiLingualSize = modelMultiLingualSize;
            modelMultiLingualFinished = true;
            activity.runOnUiThread(() -> {
                if (modelEnglishOnlyFinished && modelMultiLingualFinished) binding.buttonStart.setVisibility(View.VISIBLE);
            });
        }

        File modelEnglishOnlyFile = new File(activity.getExternalFilesDir(null) + "/" + modelEnglishOnly);
        if (!modelEnglishOnlyFile.exists()) {
            modelEnglishOnlyFinished = false;
            Log.d("WhisperASR", "English only model file does not exist");
            Thread thread = new Thread(() -> {
                try {
                    URL url = new URL(modelEnglishOnlyURL);
                    Log.d("WhisperASR", "Download English only model");

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    modelEnglishOnlyFile.createNewFile();

                    FileOutputStream outStream = new FileOutputStream(modelEnglishOnlyFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (modelEnglishOnlyFile.exists()) downloadModelEnglishOnlySize = modelEnglishOnlyFile.length();
                        activity.runOnUiThread(() -> {
                            binding.downloadSize.setText((downloadModelEnglishOnlySize+downloadModelMultiLingualSize)/1024/1024 + " MB");
                            binding.downloadProgress.setProgress((int) (((double)(downloadModelEnglishOnlySize + downloadModelMultiLingualSize) / (modelEnglishOnlySize + modelMultiLingualSize)) * 100));                        });
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();

                    String calcEnglishOnlyModelMD5="";
                    if (modelEnglishOnlyFile.exists()) {
                        calcEnglishOnlyModelMD5 = calculateMD5(String.valueOf(Paths.get(modelEnglishOnlyFile.getPath())));
                    } else {
                        throw new IOException();  //throw exception if there is no modelMultiLingualFile at this point
                    }

                    if (!calcEnglishOnlyModelMD5.equals(modelEnglishOnlyMD5)){
                        modelEnglishOnlyFile.delete();
                        modelEnglishOnlyFinished = false;
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        modelEnglishOnlyFinished = true;
                        activity.runOnUiThread(() -> {
                            if (modelEnglishOnlyFinished && modelMultiLingualFinished) binding.buttonStart.setVisibility(View.VISIBLE);
                        });
                    }
                } catch (NoSuchAlgorithmException | IOException i) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                    modelEnglishOnlyFile.delete();
                    Log.w("WhisperASR", activity.getResources().getString(R.string.error_download), i);
                }
            });
            thread.start();
        } else {
            downloadModelEnglishOnlySize = modelEnglishOnlySize;
            modelEnglishOnlyFinished = true;
            activity.runOnUiThread(() -> {
                if (modelEnglishOnlyFinished && modelMultiLingualFinished) binding.buttonStart.setVisibility(View.VISIBLE);
            });
        }

    }

    public static String calculateMD5(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new BufferedInputStream(new FileInputStream(filePath))) {
            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        byte[] hash = md.digest();
        return new BigInteger(1, hash).toString(16);
    }
}