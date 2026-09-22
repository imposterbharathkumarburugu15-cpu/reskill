# Start here

This is **SOVARIX**, the real Android Phone Twin from your shared conversation.

1. Extract the ZIP.
2. Open the inner **SOVARIX** folder in Android Studio.
3. Select JDK 17 for Gradle. Install Android SDK 35 and Build Tools 35.0.0 through SDK Manager.
4. Allow the included Gradle wrapper to download its dependencies.
5. Connect your Android 10+ phone, enable USB debugging, select the device, and press **Run**.

Command-line build:

```bash
./gradlew :core:test :app:assembleDebug
```

Windows:

```powershell
.\gradlew.bat :core:test :app:assembleDebug
```

The resulting development APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## First real demonstration

Start Twin session → select your workload → collect two minutes of real readings → Future → pin a two-minute forecast → wait two more minutes → inspect predicted versus measured temperature.

Stop the session to update Device DNA. Export evidence from Black Box. Battery temperature is not CPU temperature; unavailable Q-chip access correctly uses the standard path.

Read `docs/BUILD_REPORT.md` before presenting the prototype. The included statistical model is local; a language model is not bundled. A successful core test does not replace installation and sensor testing on your iQOO.
