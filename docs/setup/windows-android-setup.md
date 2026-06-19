# Windows Android Setup for ViviCast

This guide prepares the Windows PC for ViviCast Android TV development before feature work starts.

## 1. Install Required Apps

Install these manually from their official websites if they are not already present:

- Android Studio Stable, preferably Android Studio Quail 1 Patch 2 or newer. Detected locally: `C:\Program Files\Android\Android Studio\bin\studio64.exe`.
- Git, already detected on this PC.
- Scrcpy for controlling physical Android devices from Windows. Detected locally: `C:\Users\Andreas\Tools\scrcpy-win64-v4.0\scrcpy.exe`.
- VLC Media Player for quickly checking stream URLs outside the app.
- DB Browser for SQLite, optional but useful alongside Android Studio Database Inspector.
- ffmpeg/ffprobe, optional for later stream diagnostics.

Avoid installing random IPTV tooling from unknown sources. ViviCast should only need standard Android/media debugging tools.

## 2. Android Studio First Run

Open Android Studio and complete the setup wizard. From this repo you can run:

```powershell
.\scripts\open-android-studio.ps1
```

Use these choices:

- Install Android SDK to `C:\Users\Andreas\AppData\Local\Android\Sdk`.
- Install Android SDK Platform for the current stable Android release.
- Install Android SDK Build-Tools.
- Install Android SDK Platform-Tools.
- Install Android Emulator.
- Install at least one Android TV or Google TV system image.
- Install a Pixel Tablet image later for tablet testing.
- Install a phone image later for low-risk smoke tests instead of using the private main phone.
- Install Android SDK Command-line Tools if you want terminal-based `sdkmanager` and `avdmanager`.

Detected locally:

- Android SDK path exists: `C:\Users\Andreas\AppData\Local\Android\Sdk`.
- Android Studio bundled JDK exists: `C:\Program Files\Android\Android Studio\jbr`.
- SDK platforms exist: `android-36`, `android-36.1`.
- TV system images exist for Android TV and Google TV.
- Android SDK Command-line Tools are installed at `C:\Users\Andreas\AppData\Local\Android\Sdk\cmdline-tools\latest`.
- Android TV AVD exists: `ViviCast_AndroidTV_API36`.
- The older Google TV AVD `ViviCast_TV_1080p_API36` exists, but should not be used for normal development because its first-run Google TV setup can block input and app testing.

In Android Studio settings:

- Set Gradle JDK to the bundled Android Studio JDK.
- Keep Gradle auto-sync enabled.
- Use Logcat filters for `ViviCastPlayer`, `ViviCastImport`, `ViviCastEpg`, and `ViviCastDb`.
- Use Database Inspector for Room database inspection once the app can run.

## 3. Daily UI Development Workflow

Use Android Studio and the emulator as complementary tools:

1. Open the project with `scripts\open-android-studio.ps1` and let Gradle sync finish.
2. Iterate on previewable screen/section composables in Compose Preview. Use representative normal, empty, loading, error, long-text, and dense-data states.
3. Use Interactive Preview or Live Edit for quick visual changes when Android Studio supports the current edit.
4. Run a compile checkpoint after structural UI or behavior changes. Small spacing, color, and copy edits do not require an APK reinstall each time.
5. Start `ViviCast_AndroidTV_API36` through `scripts\start-tv-emulator.ps1` and validate D-pad focus, OK/Back, scrolling, dialogs, navigation, persistence, playback, and real repository data.
6. Batch any fixes and repeat the emulator check once the UI block is coherent.

Compose Preview is a visual development surface, not a substitute for emulator testing. TV focus and remote behavior must be verified on the emulator. Codex can operate Android Studio and the emulator; user interaction is only needed for unavoidable Windows/Android Studio permission, license, login, or blocking setup dialogs.

Useful build checkpoints:

```powershell
.\gradlew.bat :app-tv:compileDebugKotlin
.\gradlew.bat :app-tv:installDebug
```

Current project behavior note:

- Debug builds may still auto-import the local demo provider when no providers exist.
- That is development-only behavior and should not be treated as the final first-run user experience.

## 3a. Project-Local Android Skills

Official Android skills from `android/skills` are installed only for this repository under `.agents/skills`.

Use them selectively for Android implementation, migration, testing, profiling, Compose, navigation, and build-tooling work. They complement the local docs here; they do not replace `docs/PLAN.md` as the project source of truth.

## 4. Configure Windows Environment

After Android Studio installs the SDK, run:

```powershell
.\scripts\configure-android-env.ps1
```

Then close and reopen PowerShell and verify:

```powershell
adb version
git --version
scrcpy --version
```

If `scrcpy` is not installed yet, its version check can fail. That is fine until physical-device mirroring is needed.

If `adb` is still not found in a new shell, rerun `.\scripts\configure-android-env.ps1` or use the direct fallback path temporarily:

```powershell
C:\Users\Andreas\AppData\Local\Android\Sdk\platform-tools\adb.exe version
```

## 5. Android TV Device over Wi-Fi

Use the Android TV as the main real-device test target.

On Android TV:

- Connect it to the same Wi-Fi as the Windows PC.
- Enable Developer options.
- Enable USB debugging or Wireless debugging.
- Note the TV IP address.

From PowerShell:

```powershell
adb connect <tv-ip>:5555
adb devices
```

Or use:

```powershell
.\scripts\connect-android-tv.ps1 -HostAddress <tv-ip>:5555
```

Current physical TV connection:

- IP: `192.168.178.40`
- Device: `Xiaomi Mi Smart TV 4S`
- Model: `MiTV-MSSp3`
- OS: Android 9
- ADB state: `device`
- ViviCast TV debug APK installs and launches successfully.

On newer Android TV versions, Wireless debugging may require a pairing code:

```powershell
adb pair <tv-ip>:<pairing-port>
adb connect <tv-ip>:<adb-port>
adb devices
```

Use the physical remote for acceptance testing. Keyboard arrows in an emulator are helpful, but they do not replace real D-pad testing.

Important workflow rule: Codex should install APKs on the physical Android TV only when the user explicitly asks for it. Normal development installs and smoke tests should use the Android TV emulator.

## 6. Emulator Setup

The primary Android TV emulator has already been created:

- `ViviCast_AndroidTV_API36`

Use this Android TV image for development instead of the Google TV image. It avoids the Google account first-run setup flow and gives us a cleaner D-pad test target.

Start it from PowerShell:

```powershell
.\scripts\start-tv-emulator.ps1
```

Important: use this script for normal development instead of manually starting an AVD. It always targets `ViviCast_AndroidTV_API36`. Do not use `ViviCast_TV_1080p_API36` unless explicitly needed for a separate Google TV test.

Additional Android Virtual Devices can be created in Android Studio:

- Pixel Tablet emulator for later tablet UI.
- Pixel phone emulator for later phone smoke tests.

Run the TV emulator first and verify D-pad navigation. Mobile and tablet testing should stay secondary until the Android TV MVP is usable.

## 7. Smartphone and Tablet Safety

Use the tablet as the preferred mobile physical test device.

For the private main smartphone:

- Do not root.
- Do not unlock the bootloader.
- Do not flash images.
- Do not install system-level tools.
- Only install normal debug APKs if needed for low-risk smoke testing.

Normal Android debug APK installation is enough for ViviCast development and should not create brick risk.

## 8. Setup Acceptance Checklist

Before feature development:

- `adb version` works.
- `git --version` works.
- Android Studio opens the project.
- Android Studio detects the SDK.
- Gradle JDK uses the bundled Android Studio JDK.
- Android TV emulator starts: `ViviCast_AndroidTV_API36`.
- ViviCast TV debug APK installs and launches on `ViviCast_AndroidTV_API36`.
- Physical Android TV appears in `adb devices`: `192.168.178.40:5555`.
- A debug APK can be installed on the emulator and Android TV.
