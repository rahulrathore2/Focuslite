# FocusLite - Native Android Short-Form Video Blocker

**FocusLite** is a native Android application built in **Kotlin** and **Jetpack Compose (Material 3)** designed to eliminate doom-scrolling by selectively blocking **YouTube Shorts** and **Instagram Reels** using Android's `AccessibilityService`.

---

## 🎯 Features

1. **Selective Content Interception**:
   - Detects the exact moment a YouTube Short or Instagram Reel is opened.
   - Triggers `performGlobalAction(GLOBAL_ACTION_BACK)` immediately to exit the short-form player.
   - **Preserves all other functionality**: Standard YouTube videos, search, playlists, Instagram DMs, regular feed posts, and stories remain completely operational!

2. **Clean Jetpack Compose UI**:
   - Modern Material 3 dashboard.
   - Individual toggles for **Block YouTube Shorts** and **Block Instagram Reels**.
   - Dynamic real-time Accessibility Service status indicator (Green Active / Amber Inactive).
   - One-tap button opening system Accessibility Settings.
   - Auto-refreshing status on screen resume.
   - Digital Wellbeing Focus Metrics (Shorts & Reels blocked counter, estimated time saved).

3. **Production-Ready & Battery Efficient**:
   - Limits package filtering strictly to `com.google.android.youtube` and `com.instagram.android`.
   - Incorporates a 1000ms debounce rate-limiter to prevent back-button loops or accidental app closure.
   - Deep and fast-path node hierarchy scanning with node recycling to prevent memory leaks.
   - 100% on-device processing with zero network permissions.

---

## 📁 Project Architecture & File Structure

```
FocusLite/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── res/
│           │   ├── xml/
│           │   │   ├── accessibility_service_config.xml
│           │   │   ├── backup_rules.xml
│           │   │   └── data_extraction_rules.xml
│           │   └── values/
│           │       ├── colors.xml
│           │       ├── strings.xml
│           │       └── themes.xml
│           └── java/com/focuslite/app/
│               ├── MainActivity.kt
│               ├── FocusBlockerService.kt
│               ├── PreferencesManager.kt
│               ├── AccessibilityUtils.kt
│               └── ui/
│                   ├── MainScreen.kt
│                   └── theme/
│                       ├── Color.kt
│                       ├── Theme.kt
│                       └── Type.kt
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🔍 How Detection Works

### 1. YouTube Shorts Detection
- Target package: `com.google.android.youtube`
- Monitored View IDs:
  - `com.google.android.youtube:id/reel_recycler` (Primary Shorts vertical feed)
  - `com.google.android.youtube:id/shorts_player`
  - `com.google.android.youtube:id/reel_player_page_view`
  - `com.google.android.youtube:id/reel_player_fragment_container`
  - `com.google.android.youtube:id/reel_recycler_fragment`
  - `com.google.android.youtube:id/shorts_container`
- Monitored Content Descriptions & Navigation:
  - "Shorts player", "Sound used in this short", "Pause short", "Play short", "Pivot button"
  - Active bottom navigation bar item with text/description "Shorts" and `isSelected == true`

### 2. Instagram Reels Detection
- Target package: `com.instagram.android`
- Monitored View IDs (Instagram's internal codename for Reels is `clips`):
  - `com.instagram.android:id/clips_viewer_view_pager` (Primary Reels vertical feed)
  - `com.instagram.android:id/clips_video_container`
  - `com.instagram.android:id/clips_viewer_fixed_container`
  - `com.instagram.android:id/clips_swipe_refresh_layout`
  - `com.instagram.android:id/clips_media_component`
  - `com.instagram.android:id/reels_tab` (when selected)
- Monitored Content Descriptions & Navigation:
  - "Watch more Reels", "Original audio - ", "Remix of "
  - Bottom navigation bar item "Reels" when `isSelected == true`

### 3. Exit Mechanism
- When a target element is detected and the respective toggle is ON:
  ```kotlin
  performGlobalAction(GLOBAL_ACTION_BACK)
  ```
- A cooldown timer (`BACK_ACTION_COOLDOWN_MS = 1000L`) prevents multiple rapid triggers while the window transitions back to the previous screen.

---

## 🚀 How to Compile & Run in Android Studio

1. **Open Project**:
   - Open Android Studio (Ladybug, Koala, Iguana, or newer).
   - Select **File > Open** and choose the `FocusLite/` folder.
2. **Sync Gradle**:
   - Allow Gradle to sync dependencies from `gradle/libs.versions.toml`.
3. **Run on Device / Emulator**:
   - Connect an Android device (API 26+ / Android 8.0 through Android 15).
   - Press **Run 'app'** (`Shift + F10`).
4. **Enable the Accessibility Service**:
   - When the app launches, tap **"Enable in Settings"**.
   - In Android Accessibility Settings, find **"FocusLite Blocker Service"** under Downloaded Apps / Installed Services.
   - Toggle it **ON** and accept the system prompt.
   - Switch back to FocusLite; the status banner will instantly turn **Green (Active Protection)**!
