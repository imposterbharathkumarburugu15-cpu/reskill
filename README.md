# SOVARIX — Intelligence Within (Android Phone Twin)

Native Android prototype of the adaptive **Phone Twin** telemetry, gaming analysis, and thermal prediction engine. Built with **Kotlin**, **Jetpack Compose**, **Coroutines**, and an independently testable numerical core.

> **Zero Cloud / Complete Privacy**: SOVARIX operates **100% offline** on your device. It requires **no account**, **no internet permission**, **no root**, and **no external server**.

---

## Table of Contents

1. [Where to Find the APK](#where-to-find-the-apk)
2. [How to Install the APK](#how-to-install-the-apk)
   - [Method 1: Direct Install on Phone (Easiest)](#method-1-direct-install-on-phone-easiest)
   - [Method 2: Install via ADB / USB Debugging (Fastest for Devs)](#method-2-install-via-adb--usb-debugging-fastest-for-devs)
   - [Method 3: Build from Source in Android Studio](#method-3-build-from-source-in-android-studio)
3. [Required Permissions & First-Time Setup](#required-permissions--first-time-setup)
4. [How to Use This App](#how-to-use-this-app)
   - [Overview of the 5 Main Tabs](#overview-of-the-5-main-tabs)
   - [1. Gaming Tab (Moments & Performance)](#1-gaming-tab-moments--performance)
   - [2. Home / Twin Tab (Real-Time Telemetry & Session Control)](#2-home--twin-tab-real-time-telemetry--session-control)
   - [3. Capture Studio Tab (Gaming Highlights & Recordings)](#3-capture-studio-tab-gaming-highlights--recordings)
   - [4. Insights Hub (Incidents, Lab A/B Experiments, Future Forecasts)](#4-insights-hub-incidents-lab-ab-experiments-future-forecasts)
   - [5. More Hub (Black Box, Device DNA, Hardware Audit, Overhead)](#5-more-hub-black-box-device-dna-hardware-audit-overhead)
5. [Step-by-Step: Your First 5-Minute Demonstration](#step-by-step-your-first-5-minute-demonstration)
6. [Exporting Evidence](#exporting-evidence)
7. [Technical Scope & Architecture](#technical-scope--architecture)
8. [Evidence Boundaries & Disclaimers](#evidence-boundaries--disclaimers)

---

## Where to Find the APK

The pre-compiled development APK is located directly inside the project directory:

```text
SOVARIX/app/build/outputs/apk/debug/app-debug.apk
```

- **File size**: ~28 MB
- **Minimum Android version**: Android 10 (API 29)
- **Target Android version**: Android 15 (API 35)
- **Signing**: Development debug key

---

## How to Install the APK

### Method 1: Direct Install on Phone (Easiest)

1. **Transfer the APK to your phone**:
   - Connect your phone to your PC via USB cable and copy `app-debug.apk` to your phone's **Downloads** folder.
   - Alternatively, send it to yourself via Google Drive, Telegram, WhatsApp, or Quick Share / Nearby Share.
2. **Open File Manager**:
   - Open your phone's Files / File Manager app and locate `app-debug.apk`.
   - Tap on the APK file.
3. **Allow Unknown Sources** (if prompted):
   - Android will prompt: *"For your security, your phone is not allowed to install unknown apps from this source"*.
   - Tap **Settings** &rarr; toggle on **"Allow from this source"** &rarr; go back.
4. **Google Play Protect warning**:
   - Tap **"More details"** &rarr; tap **"Install anyway"** (this is standard for development/debug APKs not downloaded from the Google Play Store).
5. **Tap Open**:
   - The SOVARIX app will launch.

---

### Method 2: Install via ADB / USB Debugging (Fastest for Devs)

1. Enable **Developer Options** on your phone:
   - Go to **Settings** &rarr; **About Phone** &rarr; tap **Build Number** 7 times.
   - Go to **Settings** &rarr; **System** &rarr; **Developer Options** &rarr; enable **USB Debugging**.
2. Connect your phone to your computer with a USB cable (allow USB debugging prompt on phone).
3. Open a terminal or PowerShell in the repository directory and run:

```powershell
adb install -r SOVARIX\app\build\outputs\apk\debug\app-debug.apk
```

---

### Method 3: Build from Source in Android Studio

1. Launch **Android Studio** (Ladybug / Koala / Hedgehog or newer).
2. Click **Open** and select the `SOVARIX` directory (`SOVARIX-Android-Prototype/SOVARIX`).
3. Ensure JDK 17 is selected in **Settings &rarr; Build, Execution, Deployment &rarr; Build Tools &rarr; Gradle**.
4. Allow Gradle to sync dependencies.
5. Connect your device and click the green **Run (Play)** button, or build the APK via terminal:

```powershell
# Windows
cd SOVARIX
.\gradlew.bat :core:test :app:assembleDebug

# macOS / Linux
cd SOVARIX
./gradlew :core:test :app:assembleDebug
```

---

## Required Permissions & First-Time Setup

When you open SOVARIX for the first time, grant these permissions for full functionality:

| Permission | Why SOVARIX Needs It | How to Grant |
|---|---|---|
| **Notifications** (`POST_NOTIFICATIONS`) | Shows the background Twin monitoring bar and quick **Stop** button while gaming. | Tap **Allow** when prompted on startup. |
| **Usage Access** (`PACKAGE_USAGE_STATS`) | Detects when you launch games (e.g., BGMI, Call of Duty, Asphalt) to auto-adjust observation frequency. | Tap the **"Grant Usage Access"** banner &rarr; find SOVARIX &rarr; toggle **On**. |
| **Screen Capture & Audio** (`MEDIA_PROJECTION`) | Allows Capture Studio to record short video replay clips during key gaming moments. | Tap **Start Recording** in Gaming tab &rarr; tap **Start Now**. |

> 🔒 **Privacy Guarantee**: SOVARIX has **NO internet permission** (`android.permission.INTERNET` is omitted from the manifest). All sensor data, recordings, and mathematical models remain entirely on your phone.

---

## How to Use This App

SOVARIX uses a **5-Tab Navigation** layout at the bottom of the screen:

```
[ Gaming ]   [ Home (Twin) ]   [ Capture ]   [ Insights ]   [ More ]
```

---

### 1. Gaming Tab (Moments & Performance)

Designed for when you are actively playing games:
- **Game Detection & Profiles**: Automatically identifies active games or allows manual game selection (e.g. Battle Royale, Racing, FPS).
- **Stability Status Meter**: Real-time indication of whether performance is `STABLE`, `WARM`, or `THROTTLED`.
- **Special Moments**: Tracks game events (clutches, intense firefights, thermal peaks) and coordinates with Capture Studio to save clips.
- **One-Tap Actions**: Quick-start gaming session or trigger an app intervention to reduce thermal load.

---

### 2. Home / Twin Tab (Real-Time Telemetry & Session Control)

Your main cockpit for device vitals:
- **Start Twin Session**: Tap the prominent **"Start Twin Session"** button to start the observation service. A persistent notification will appear so monitoring continues even when you minimize the app.
- **Live Vitals Cards**:
  - **Battery**: Real-time percentage, drain rate (%/hr), and charging current (mA).
  - **Temperature**: Battery thermal sensor readout in °C.
  - **Thermal Headroom**: Android OS thermal status (Normal, Light, Moderate, Severe, Throttled).
  - **Memory (RAM)**: Current process PSS usage and system available RAM.
- **Intervention Testing**: Tap **"Dim this app"** to test whether lowering screen brightness stabilizes battery temperature.
- **Stop Session**: Tap **"Stop Twin Session"** to end monitoring and save recorded data to Device DNA.

---

### 3. Capture Studio Tab (Gaming Highlights & Recordings)

A dedicated replay viewer for your gaming clips:
- **Recorded Clips**: Browse video clips saved during intense gaming moments.
- **Filter by Type**: Filter clips by `All`, `Kills`, `Clutches`, or `Thermal Spikes`.
- **In-App Video Player**: Tap any clip to play it directly in the app with synchronized telemetry stats (FPS, temperature, and battery drain during that play).

---

### 4. Insights Hub (Incidents, Lab A/B Experiments, Future Forecasts)

Contains three sub-sections switchable via the top pill bar:

#### A. Incidents
- An automated log of whenever your phone encountered thermal throttling, rapid battery drop, or system memory pressure.
- Displays the exact timestamp, temperature peak, and root cause evidence.

#### B. Lab (A/B Testing)
- Run controlled experiments to measure real-world thermal impacts:
  - Example: Test temperature difference with 60 FPS cap vs 120 FPS, or gaming while charging vs on battery.
  - Generates clear comparative before-and-after metrics.

#### C. Future (Forecast & Verification)
- **Predictive Twin**: Calculates a 2-minute and 5-minute projection of future battery temperature and battery drain using real-time linear regression.
- **Pin Prediction & Verify**:
  1. Once the app has collected at least 2 minutes of continuous telemetry, tap **"Pin Prediction"**.
  2. Continue gaming or using your phone for another 2 minutes until the timer elapses.
  3. The app automatically compares the predicted temperature against the actual measured temperature and shows the signed error (e.g., `+0.3°C deviation`).

---

### 5. More Hub (Black Box, Device DNA, Hardware Audit, Overhead)

Administrative and technical deep-dive tools:

#### A. Black Box
- Stores an atomic log of the last 200 telemetry events and 60 prediction verifications.
- **Export JSON**: Tap **"Export Evidence"** to save your complete session log as a standardized JSON file to your device storage (via Storage Access Framework).

#### B. Device DNA
- Displays the learned baseline profile of your device across different states (Idle, Gaming, Charging, Heavy Load).
- Shows how forecast calibration improves over time as more sessions are recorded.

#### C. Hardware Diagnostics
- Detailed hardware audit: Device manufacturer, exact model, Android OS version, public SoC name, CPU cluster architecture, and vendor adapter compatibility status.

#### D. Resource Governor & Economy Mode
- Transparent overhead monitoring: Shows SOVARIX's own CPU consumption (normalized to 1 core) and memory usage.
- **Economy Mode Toggle**: Reduces background telemetry sampling frequency (from every 10s to every 30s) to minimize battery impact.
- **Purge History**: Safely reset all stored sessions and baseline data.

---

## Step-by-Step: Your First 5-Minute Demonstration

Follow this quick test to verify the complete SOVARIX engine on your phone:

1. **Start Monitoring**:
   - Open SOVARIX &rarr; tap **Home** tab &rarr; tap **"Start Twin Session"**.
   - Keep the session running for **2 minutes** under normal use or while playing a game.
2. **View Live Telemetry**:
   - Observe the live battery temperature and charging/discharging current update in real time.
3. **Pin a Forecast**:
   - Go to **Insights** tab &rarr; select **Future** sub-tab.
   - Tap **"Pin prediction & verify"** on the 2-minute horizon.
4. **Wait for Verification**:
   - Keep using your phone for 2 more minutes until the countdown completes.
   - Observe the verification card update with **Actual vs. Predicted** temperature and signed error.
5. **Stop & Export**:
   - Return to **Home** tab &rarr; tap **"Stop Twin Session"**.
   - Go to **More** tab &rarr; **Black Box** &rarr; tap **"Export JSON"** to save the verified telemetry log.

---

## Exporting Evidence

You can export session logs anytime:
1. Navigate to **More** &rarr; **Black Box**.
2. Tap **"Export Evidence"** (or **"Export JSON"**).
3. The Android system file picker will open; choose a folder (e.g., Documents or Downloads) and tap **Save**.
4. The exported JSON includes raw timestamps, battery millivolts, current, temperature, thermal headroom, and prediction error logs for independent verification.

---

## Technical Scope & Architecture

| Component | Responsibility |
|---|---|
| `core/Models.kt` | Strictly-typed telemetry, observation state, prediction and verification structures. |
| `core/Engines.kt` | Resource Governor, Observation Controller, Numerical Forecast Engine, Anomaly Detector, and Verification Core. |
| `core/TwinEngine.kt` | Thread-safe, mutex-guarded twin state machine and telemetry coordinator. |
| `app/telemetry/TwinService.kt` | Foreground service with persistent notification and low-overhead sensor sampling. |
| `app/gaming/GamingSessionManager.kt` | Gaming session orchestration and automated special-moment capture triggers. |
| `app/data/` | Atomic file-backed JSON persistence and Storage Access Framework export. |
| `app/ui/SovarixScreen.kt` | Complete 5-tab Jetpack Compose user interface. |

---

## Evidence Boundaries & Disclaimers

- **Battery vs. CPU Temperature**: Battery temperature is read from the Android battery manager. It is not equivalent to CPU/GPU die temperature, which typically requires manufacturer-specific vendor adapters or root.
- **Local Deterministic Modeling**: Predictions are calculated on-device via local numerical regression algorithms. No cloud large language model (LLM) or neural net is bundled.
- **Forecast Preconditions**: Forecasting requires at least 8 samples (~2 minutes) in a continuous state. Changing workloads or plugging/unplugging chargers flags a context shift and resets forecast history.
- **Zero Internet Usage**: SOVARIX does not send any telemetry off your phone.
