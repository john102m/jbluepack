package com.jbluepack;

import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattDescriptor;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Looper;

import java.util.UUID;
import android.util.Log;

public class BLEModule extends ReactContextBaseJavaModule {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private ReactApplicationContext reactContext;
    private static final String TAG = "BLEModule";  // ✅ Define TAG
    private Promise connectionPromise;  // ✅ Store Promise to resolve later
    private static final String ESP32_DEVICE_ADDRESS = "94:A9:90:48:02:FA";//78:1C:3C:A5:B1:36"; // ESP32 BLE MAC address

    public BLEModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    }

    @Override
    public String getName() {
        return "BLEModule"; // Used in React Native
    }

    @ReactMethod
    public void scanBLEDevices(Promise promise) {
        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            promise.reject("BLE Scan Error", "Bluetooth LE scanner unavailable.");
            return;
        }
        Log.d(TAG, "Scanning for BLE devices...");
        scanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String deviceInfo = device.getName() + " - " + device.getAddress();
                Log.d(TAG, "BLE Device Found: " + deviceInfo);
                promise.resolve(deviceInfo);  // Send device info back to React Native
            }
        });

        promise.resolve("Scanning for BLE devices...");
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Subscribed to BLE notifications!");
                WritableMap params = Arguments.createMap();
                params.putString("message", "Subscribed to BLE notifications!");
                sendEvent("BluetoothNotification", params);
                // Notify JS layer if needed
            } else {
                Log.e(TAG, "Failed to write descriptor for notifications.");
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String receivedData = characteristic.getStringValue(0);
            // ✅ Ensure message isn’t reprocessed multiple times
            if (!receivedData.isEmpty()) {
                Log.d(TAG, "Received Notification: " + receivedData);
                WritableMap params = Arguments.createMap();
                params.putString("message", receivedData);
                sendEvent("BluetoothNotification", params);
            }

        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered!");

                for (BluetoothGattService service : gatt.getServices()) {
                    Log.d(TAG, "Found service UUID: " + service.getUuid().toString());

                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.d(TAG, "Found characteristic UUID: " + characteristic.getUuid().toString());
                    }
                }

                BluetoothGattService service = gatt.getService(UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b"));
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8"));
                    if (characteristic != null) {
                        Log.d(TAG, "Characteristic found! Ready for BLE operations.");
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed!");
            }
        }
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            WritableMap params = Arguments.createMap();
            if (newState == BluetoothProfile.STATE_CONNECTED) {

                Log.d(TAG, "Connected to ESP32! Refreshing cache...");

                if (connectionPromise != null) {
                    connectionPromise.resolve("Connected to ESP32!");  // ✅ Resolve the promise when connected
                    connectionPromise = null;  // Clear reference
                }

                params.putString("status", "Connected");
                sendEvent("BLEConnectionStatus", params);  // ✅ Use separate event type for connection status

                gatt.discoverServices();  // Start discovering services
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                Log.d(TAG, "Disconnected from ESP32. Status: " + status);
//                if (status == 133) {  // ✅ Common timeout issue
//                    Log.e(TAG, "Connection timeout—retrying...");
//                    gatt.connect();  // Retry connection
//                }
                if (connectionPromise != null) {
                    Log.d(TAG, "Rejecting promise");
                    connectionPromise.reject("BLE Disconnected", "ESP32 BLE disconnected.");
                    connectionPromise = null;
                }

                params.putString("status", "Disconnected");
                sendEvent("BLEConnectionStatus", params);  // ✅ Use separate event type for connection status
            }
        }
    };
    @ReactMethod
    public void connectToKnownBLEDevice(Promise promise) {
        connectionPromise = promise;  // ✅ Store the promise for later resolution

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(ESP32_DEVICE_ADDRESS);
        //bluetoothGatt = device.connectGatt(getReactApplicationContext(), false, gattCallback);
        bluetoothGatt = device.connectGatt(getReactApplicationContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE);

        Log.d(TAG, "Attempting to connect to ESP32...");
        promise.resolve("Attempting to connect to ESP32...");  // Initial message before real connection is established
    }
    @ReactMethod
    public void connectToBLEDevice(String deviceAddress, Promise promise) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        bluetoothGatt = device.connectGatt(getReactApplicationContext(), false, gattCallback );

        promise.resolve("Connecting to BLE device...");
    }

    @ReactMethod
    public void disconnectBLE(Promise promise) {
        Log.d(TAG, "Attempting to disconnect BLE...");
        if (bluetoothGatt != null) {
            Log.d(TAG, "bluetoothGatt != null");
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
            Log.d(TAG, "bluetoothGatt == null");
            promise.resolve("Disconnected from BLE device");
        } else {
            promise.reject("BLE Not Connected", "No active BLE connection to disconnect.");
        }
    }

    @ReactMethod
    public void writeToBLECharacteristic(String serviceUUID, String characteristicUUID, String data, Promise promise) {
        if (bluetoothGatt == null) {
            promise.reject("BLE Not Connected", "No active BLE connection.");
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUUID));
        if (service == null) {
            promise.reject("Service Not Found", "BLE service not found.");
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUUID));
        if (characteristic == null) {
            promise.reject("Characteristic Not Found", "BLE characteristic not found.");
            return;
        }

        characteristic.setValue(data.getBytes()); // ✅ Convert string to byte array
        boolean success = bluetoothGatt.writeCharacteristic(characteristic); // ✅ Write data

        if (success) {
            Log.d(TAG, "Sent data via BLE: " + data);
            promise.resolve("Data sent successfully!");
        } else {
            promise.reject("Write Failed", "Failed to send BLE data.");
        }
    }


    private void sendEvent(String eventName, WritableMap params) {
        if (reactContext != null) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);
        } else {
            Log.e(TAG, "React Context is null—cannot emit event!");
        }
    }


    @ReactMethod
    public void subscribeToBLENotifications(String serviceUUID, String characteristicUUID, Promise promise) {
        if (bluetoothGatt == null) {
            promise.reject("BLE Not Connected", "No active BLE connection.");
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUUID));
        if (service == null) {
            promise.reject("Service Not Found", "BLE service not found.");
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUUID));
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        // Perform descriptor write after a short delay
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        // ✅ REQUIRED: Enable descriptor for notifications!
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                }
            }
        }, 500); // 500ms delay before attempting to write descriptor

//        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
//        if (descriptor != null) {
//            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            bluetoothGatt.writeDescriptor(descriptor);
//        }
        Log.d(TAG, "Subscribing to BLE notifications.......");
        promise.resolve("Subscribing to BLE notifications......");
    }

    @ReactMethod
    public void unsubscribeFromBLENotifications(String serviceUUID, String characteristicUUID, Promise promise) {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUUID));
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUUID));
                if (characteristic != null) {
                    bluetoothGatt.setCharacteristicNotification(characteristic, false); // ✅ Stop notifications
                    promise.resolve("Unsubscribed from BLE notifications");
                    return;
                }
            }
        }
        promise.reject("BLE Error", "Could not unsubscribe from notifications");
    }


}
