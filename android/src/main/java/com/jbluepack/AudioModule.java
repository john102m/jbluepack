package com.jbluepack;

import android.media.MediaPlayer;
import android.content.Context;
import android.net.Uri;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

public class AudioModule extends ReactContextBaseJavaModule {
    private MediaPlayer mediaPlayer;

    public AudioModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "AudioModule";
    }

    @ReactMethod
    public void playAudio(String fileName, Promise promise) {
        try {
            Context context = getReactApplicationContext();

            // Get resource ID dynamically
            int resId = context.getResources().getIdentifier(fileName, "raw", context.getPackageName());

            if (resId == 0) {
                promise.reject("ERROR", "Audio file not found: " + fileName);
                return;
            }

            // Release existing MediaPlayer instance if needed
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }

            // Initialize and start playback
            mediaPlayer = MediaPlayer.create(context, resId);
            mediaPlayer.start();

            promise.resolve("Playing: " + fileName);
        } catch (Exception e) {
            promise.reject("ERROR", e);
        }
    }

}
