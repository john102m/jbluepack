# JBluePack - React Native Bluetooth Module

**A modular React Native package for Bluetooth communication with ESP32C3 devices.**

## Features
✅ **Bluetooth Low Energy (BLE) scanning** for nearby devices  
✅ **Device connection management** for seamless interactions  
✅ **Optimized React Native integration** using a custom native module  
✅ **Modular structure** for easy inclusion in any React Native app  

## Installation
Install the package using npm or yarn:  
```bash
npm install jbluepack --save
or

bash
yarn add jbluepack
Setup
After installation, ensure dependencies are linked correctly:

bash
npx react-native start --reset-cache
npx react-native run-android  # For Android
cd ios && pod install && cd .. && npx react-native run-ios  # For iOS
Usage
Scanning for Devices
tsx
import { BluetoothScanner } from 'jbluepack';

BluetoothScanner.scan()
  .then(devices => console.log(devices))
  .catch(error => console.error(error));
Connecting to a Device
tsx
import { BluetoothManager } from 'jbluepack';

BluetoothManager.connect(deviceId)
  .then(() => console.log('Connected!'))
  .catch(error => console.error('Connection failed:', error));
Development
If contributing to JBluePack, clone the repo and install dependencies:

bash
git clone https://github.com/YourUsername/JBluePack.git
cd JBluePack
npm install
License
📜 MIT License — Free to use and modify.