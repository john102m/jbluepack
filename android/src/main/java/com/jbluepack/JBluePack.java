package com.jbluepack;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import android.util.Log;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;


public class JBluePack  implements ReactPackage {
    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        Log.d("JBlue Package", "Initializing BluetoothModule & BLEModule");

        return Arrays.asList(
            new BLEModule(reactContext),         // Bluetooth Low Energy (BLE)
            new BluetoothModule(reactContext)         // Bluetooth Classic 
            );
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }
}

