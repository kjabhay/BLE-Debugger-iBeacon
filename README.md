# 📡 BLE Beacon Debugger

A modern, highly accurate Android utility for scanning, tracking, and debugging iBeacons. Built specifically to handle custom ESP32 beacons and overcome the strict BLE privacy limitations introduced in Android 12+.

[![Download APK](https://img.shields.io/badge/Download-APK_v1.0.1-green.svg?style=for-the-badge&logo=android)](https://github.com/kjabhay/BLE-Debugger-iBeacon/releases/download/v1.0.1/BLE-Debugger.apk)

## ✨ Why this app exists
Standard BLE scanners often fail to detect custom iBeacons (like those built on ESP32) because modern Android operating systems actively filter out location-tracking packets if apps don't handle the `BLUETOOTH_SCAN` and `ACCESS_FINE_LOCATION` permissions perfectly. 

This app natively parses Apple Manufacturer Data (`0x004C`), bypasses the negative-byte sign-extension bug common in Kotlin, and forces a low-latency hardware scan to ensure you never miss a packet.

## 🚀 Key Features
* **🎯 Custom UUID Targeting:** Enter your specific 16-byte UUID directly in the UI (e.g., `49495448-2d41-5454-454e-44414e434520` for "IITH-ATTENDANCE ").
* **📈 Live RSSI Graph:** Visualizes signal strength in real-time to help map out RF blind spots, body-blocking interference, and distance.
* **🛡️ Modern Android Support:** Fully compatible with API 26 (Android 8.0) through Android 14+. Automatically handles the new Android 12+ permission requirements.
* **⚡ Low-Latency Scanning:** Forces `SCAN_MODE_LOW_LATENCY` to catch packets that background apps miss.

## 📥 Download & Installation

Since this is a debugging tool, it is not available on the Google Play Store. You can download and install it directly via the APK.

1. **[Download the Latest APK (v1.0.1)](https://github.com/kjabhay/BLE-Debugger-iBeacon/releases/download/v1.0.1/BLE-Debugger.apk)** directly to your Android device.
2. Tap the downloaded `.apk` file to open it.
3. If prompted, tap **Settings** and enable **"Allow from this source"**.
4. Tap **Install**.

> **⚠️ CRITICAL: First-Time Setup**
> When you open the app for the first time, Android will ask for permission to access your location. You **must** select **Precise Location** and **While using the app**. Because iBeacons are treated as location-trackers by the OS, denying location access will cause Android to hide all beacons from the scanner!

## 🛠️ Tech Stack
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Graphing:** [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)
* **Hardware Target:** ESP32 (via NimBLE) / Standard iBeacons

## 📝 License
This project is open-source and available for educational and debugging purposes.
