package com.whisperonnx.utils;

import static android.content.Context.VIBRATOR_SERVICE;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;

public class HapticFeedback {

    public static void vibrate(Context context){
        if (hapticEnabled(context)){
            Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
            } else {
                VibrationEffect vibrationEffect = VibrationEffect.createOneShot(10, 255);
                vibrator.vibrate(vibrationEffect);
            }
        }
    }

    private static boolean hapticEnabled(Context context){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vibratorManager.getDefaultVibrator().hasVibrator();
        } else {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 1;
        }
    }
}
