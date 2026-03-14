# Canon Projector IR Remote — Huawei P30 Pro

A single-button Android app that sends IR power ON/OFF signals to Canon projectors using the Huawei P30 Pro's built-in IR blaster.

## What It Does
- **One big red button** → toggles Canon projector power
- **25 different Canon IR codes** built in (LV, SX, WUX, XEED, REALiS series)
- **Long-press** the button to pick a different code
- **"Test ALL"** mode cycles through every code so you can find the right one
- **Remembers** your working code between app launches

## How to Build & Install

### Option 1: Android Studio (Recommended)
1. Install [Android Studio](https://developer.android.com/studio)
2. Open Android Studio → **File → Open** → select the `CanonProjectorRemote` folder
3. Wait for Gradle sync to finish
4. Connect your Huawei P30 Pro via USB (enable USB Debugging in Developer Options)
5. Click the green **Run ▶** button
6. The app installs and launches on your phone

### Option 2: Build APK from Command Line
```bash
cd CanonProjectorRemote
./gradlew assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
# Transfer to phone and install (enable "Install from unknown sources")
```

## How to Enable USB Debugging on Huawei P30 Pro
1. Go to **Settings → About Phone**
2. Tap **Build Number** 7 times rapidly (this unlocks Developer Options)
3. Go back to **Settings → System → Developer Options**
4. Enable **USB Debugging**

## How to Use
1. **Point** your P30 Pro's IR blaster (top edge of phone) at the projector
2. **Tap** the big red POWER button
3. If the projector doesn't respond:
   - **Long-press** the button to open the code selector
   - Try a different code, or tap **"Test ALL Codes"** — it will send each code with a 2-second pause so you can watch for the projector to react
4. Once you find the working code, it's saved automatically

## Supported Canon Projector Series
- LV-5200, LV-7100, LV-7200, LV-7210, LV-7215, LV-7220, LV-7225, LV-7230,
  LV-7260, LV-7265, LV-7275, LV-7285, LV-7300, LV-8200, LV-8300
- LV-X300, LV-WX300, LV-S300
- SX50, SX60, SX80
- WUX450, LX-MU500
- XEED series, REALiS series

## Troubleshooting
- **"No IR blaster found"** → Make sure you're on a Huawei P30 Pro (or another phone with IR blaster)
- **No response from projector** → Try ALL codes using the test feature. Aim directly at the projector's IR receiver (usually front panel). Get within 3-5 meters.
- **Works intermittently** → Hold the phone steady, IR beam is directional. The app already sends each signal 3x for reliability.

## Project Structure
```
CanonProjectorRemote/
├── app/src/main/
│   ├── java/.../MainActivity.java   ← All app logic + IR codes
│   ├── res/layout/activity_main.xml ← UI layout  
│   ├── res/drawable/power_button_bg.xml ← Button style
│   └── AndroidManifest.xml          ← Permissions & config
├── build.gradle                     ← Project build config
└── settings.gradle
```
