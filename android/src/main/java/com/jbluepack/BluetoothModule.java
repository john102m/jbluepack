package com.jbluepack;

import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Set;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.UUID;
import android.util.Log;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;


public class BluetoothModule extends ReactContextBaseJavaModule {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private String lastConnectedDeviceAddress;

    private static final String TAG = "BlutoothModule";
    private static final String EVENT_TAG = "BluetoothData";
    public BluetoothModule(ReactApplicationContext reactContext) {
        super(reactContext);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public String getName() {
        return "BluetoothModule";
    }

    @ReactMethod
    public void isBluetoothAvailable(Promise promise) {
        promise.resolve(bluetoothAdapter != null);
    }

    @ReactMethod
    public void isBluetoothEnabled(Promise promise) {
        promise.resolve(bluetoothAdapter != null && bluetoothAdapter.isEnabled());
    }

    @ReactMethod
    public void enableBluetooth(Promise promise) {
        if (bluetoothAdapter != null) {
            bluetoothAdapter.enable();
            promise.resolve(true);
        } else {
            Log.d(TAG, "This device does not support Bluetooth.");
            promise.reject("Bluetooth Not Supported", "This device does not support Bluetooth.");
        }
    }

    @ReactMethod
    public void scanDevices(Promise promise) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            promise.reject("Bluetooth Disabled", "Enable Bluetooth first.");
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        WritableArray devicesArray = Arguments.createArray();

        for (BluetoothDevice device : pairedDevices) {
            WritableMap deviceInfo = Arguments.createMap();
            deviceInfo.putString("id", device.getAddress());
            deviceInfo.putString("name", device.getName());
            devicesArray.pushMap(deviceInfo);
        }

        promise.resolve(devicesArray);
    }
    @ReactMethod
    public void sendData(String message, Promise promise) {
        if (bluetoothSocket == null) {
            Log.d(TAG, "No active Bluetooth connection.");
            promise.reject("Not Connected", "No active Bluetooth connection.");
            return;
        }
        Log.d(TAG, "Sending message: " + message);
        try {
            OutputStream outputStream = bluetoothSocket.getOutputStream();
            outputStream.write(message.getBytes());
            promise.resolve("Message Sent: " + message);
        } catch (IOException e) {
            promise.reject("Send Failed", e.getMessage());
            reconnectBluetooth();  // Attempt reconnection
        }
    }
    @ReactMethod
    public void connectToDevice(String deviceAddress, Promise promise) {
        Log.d(TAG, "Connecting to " + deviceAddress);
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            promise.reject("Bluetooth Disabled", "Enable Bluetooth first.");
            return;
        }

        try {
            Log.d(TAG, "Getting Remote Device " + deviceAddress);
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            Log.d(TAG, "Attempting to connect to " + deviceAddress);
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            bluetoothSocket.connect();
            lastConnectedDeviceAddress = deviceAddress;
            Log.d(TAG, "Connected to " + deviceAddress);
            sendEvent(EVENT_TAG, "Connected to " + deviceAddress);
            promise.resolve("Connected to " + deviceAddress);
        } catch (IOException e) {
            Log.e(TAG, "Connection Failed " + e.getMessage());
            promise.reject("Connection Failed", e.getMessage());
        }
    }
    private void reconnectBluetooth() {
        if (lastConnectedDeviceAddress == null) {
            Log.e("BluetoothModule", "No previous device to reconnect to.");
            return;
        }
        try {
            Log.d("BluetoothModule", "Attempting to reconnect to " + lastConnectedDeviceAddress);
            if (bluetoothSocket != null) {
                bluetoothSocket.close();  // Close existing socket
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(lastConnectedDeviceAddress);
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            bluetoothSocket.connect();
            Log.d("BluetoothModule", "Reconnected to " + lastConnectedDeviceAddress);
            sendEvent(EVENT_TAG, "Reconnected to " + lastConnectedDeviceAddress);
        } catch (IOException e) {
            sendEvent(EVENT_TAG, "Reconnected to " + lastConnectedDeviceAddress);
            Log.e("BluetoothModule", "Reconnection failed: " + e.getMessage());
        }
    }

    @ReactMethod
    public void startListeningForData() {
        Log.d("BluetoothModule", "Starting to listen for data...");
        if (bluetoothSocket == null || !bluetoothSocket.isConnected()) {
            Log.e("BluetoothModule", "No active Bluetooth connection.");
            return;
        }
        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader reader = new BufferedReader(inputStreamReader);
                while (bluetoothSocket.isConnected()) {
                    String receivedMessage = reader.readLine();  // Read full message

                    if (receivedMessage != null) {
                        Log.d("BluetoothModule", "Received: " + receivedMessage);
                        sendEvent(EVENT_TAG, receivedMessage);  // Emit event
                    }
                }
            } catch (IOException e) {
                Log.e("BluetoothModule", "Error reading Bluetooth data: " + e.getMessage());
                reconnectBluetooth();  // Attempt reconnection
            }
        }).start();
    }
    private void sendEvent(String eventName, String eventData) {
        ReactContext reactContext = getReactApplicationContext();
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, eventData);
        }
    }
}
