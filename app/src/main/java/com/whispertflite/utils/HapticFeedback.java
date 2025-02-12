package com.whispertflite.utils;

import static android.content.Context.VIBRATOR_SERVICE;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class HapticFeedback {

    public static void vibrate(Context context){
        Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
        } else {
            VibrationEffect vibrationEffect = VibrationEffect.createOneShot(10, 255);
            vibrator.vibrate(vibrationEffect);
        }
    }
}
