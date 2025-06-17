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
import android.bluetooth.le.ScanRecord;
import android.os.ParcelUuid;
import java.util.Map;

import android.os.Handler;
import android.os.Looper;
import java.util.List;

import java.util.UUID;
import android.util.Log;

public class BLEModule extends ReactContextBaseJavaModule {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private BluetoothLeScanner bleScanner;
    private Promise scanPromise;

    private ReactApplicationContext reactContext;
    private static final String TAG = "BLEModule";  // ✅ Define TAG
    private Promise connectionPromise;  // ✅ Store Promise to resolve later
    private static final String ESP32_DEVICE_ADDRESS = "94:A9:90:48:02:FA";//78:1C:3C:A5:B1:36"; // ESP32 BLE MAC address
    private static final String SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
    private static final String CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";

    public BLEModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    @Override
    public String getName() {
        return "BLEModule"; // Used in React Native
    }

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceInfo = device.getName() + " - " + device.getAddress();
            Log.d(TAG, "BLE Device Found: " + deviceInfo);

            ScanRecord record = result.getScanRecord();
            byte[] data = record.getManufacturerSpecificData(0x02E5);// Espressif's company ID
            if (data != null) {
                // Convert raw bytes to sensor data, if that's what you’re sending
                Log.d(TAG, "Sensor Data: " + new String(data));
            }
            Map<ParcelUuid, byte[]> serviceData = record.getServiceData();

            if (scanPromise != null) {
                scanPromise.resolve(deviceInfo);
                scanPromise = null;
            }

            if (bleScanner != null) {
                bleScanner.stopScan(this);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE scan failed with code: " + errorCode);
            if (scanPromise != null) {
                scanPromise.reject("BLE Scan Failed", "Error code: " + errorCode);
                scanPromise = null;
            }
        }
    };

    @ReactMethod
    public void scanBLEDevices(Promise promise) {
        if (bleScanner == null) {
            promise.reject("BLE Scan Error", "Bluetooth LE scanner unavailable.");
            return;
        }
        Log.d(TAG, "Starting BLE scan...");
        this.scanPromise = promise;
        bleScanner.startScan(bleScanCallback);
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
            WritableMap params = Arguments.createMap();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered!");

//                for (BluetoothGattService service : gatt.getServices()) {
//                    Log.d(TAG, "Found service UUID: " + service.getUuid().toString());
//
//                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
//                        Log.d(TAG, "Found characteristic UUID: " + characteristic.getUuid().toString());
//                    }
//                }

                BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                    if (characteristic != null) {
                        Log.d(TAG, "Characteristic found! Ready for BLE operations.");
                        params.putString("message", "Characteristic found! Ready for BLE operations.");
                        sendEvent("BluetoothNotification", params);  // ✅ Use separate event type for connection status
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
                sendEvent("BluetoothNotification", params);  // ✅ Use separate event type for connection status

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
                sendEvent("BluetoothNotification", params);  // ✅ Use separate event type for connection status
                // 🚀 Attempt reconnection
                //reconnectDevice(gatt.getDevice());
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
    private void reconnectDevice(BluetoothDevice device) {
        Log.d(TAG, "Attempting to reconnect...");

        BluetoothGatt gatt = device.connectGatt(getReactApplicationContext(), false, gattCallback);

        if (gatt == null) {
            Log.e(TAG, "Reconnect failed!");
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
        if (characteristic == null) {
            promise.reject("Characteristic Not Found", "BLE characteristic not found.");
            return;
        }

        int properties = characteristic.getProperties();
        boolean supportsNotify = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
        boolean supportsIndicate = (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
        Log.d(TAG, "Characteristic supports notify: " + supportsNotify + ", indicate: " + supportsIndicate);

        if (!supportsNotify && !supportsIndicate) {
            promise.reject("Unsupported", "Characteristic does not support notifications or indications.");
            return;
        }

        // Enable local notifications
        boolean notificationSet = bluetoothGatt.setCharacteristicNotification(characteristic, true);
        Log.d(TAG, "setCharacteristicNotification result: " + notificationSet);

        // Check for CCCD descriptor
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));

        if (descriptor == null) {
            // Log all descriptors found (if any)
            List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
            if (descriptors.isEmpty()) {
                Log.w(TAG, "No descriptors found on characteristic");
            } else {
                for (BluetoothGattDescriptor d : descriptors) {
                    Log.w(TAG, "Found descriptor UUID: " + d.getUuid());
                }
            }

            // Fallback: Resolve anyway if notifications are working without descriptor write
            Log.w(TAG, "CCCD descriptor not found. Proceeding without writing descriptor.");
            promise.resolve("Subscribed to notifications without descriptor write.");
            return;
        }

        // Set descriptor value depending on notify or indicate support
        if (supportsIndicate) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        } else {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        }

        // Write the descriptor to enable notifications on the peripheral
        boolean writeDescriptorStarted = bluetoothGatt.writeDescriptor(descriptor);
        Log.d(TAG, "Started writing descriptor: " + writeDescriptorStarted);

        // IMPORTANT: The writeDescriptor is asynchronous.
        // You need to listen for onDescriptorWrite() callback to confirm success.
        // For now, resolve immediately and handle errors in the callback.

        promise.resolve("Subscribing to BLE notifications started.");
    }
    @ReactMethod
    public void subscribeToBLENotificationsMine(String serviceUUID, String characteristicUUID, Promise promise) {
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
                Log.d(TAG, "Looking for CCCD descriptor: " + descriptor);

                if (descriptor != null) {
                    Log.d(TAG, "Writing Descriptor.......");
                    //descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE | BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    //descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    //BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE // = {0x01, 0x00}
                    //BluetoothGattDescriptor.ENABLE_INDICATION_VALUE   // = {0x02, 0x00}
                    descriptor.setValue(new byte[] {0x03, 0x00});

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
