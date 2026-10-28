# Android Nextcloud Player

A simple Android audio player for music and audiobooks stored on a **Nextcloud** server (and on the phone itself).
Built because the Nextcloud app's media player makes it hard to find and pick files with long names.

**Download:** get **m21-nextcloud-player.apk** from the [latest release](../../releases/latest/download/m21-nextcloud-player.apk) ([all releases](../../releases)) and open it on your phone
(Android will ask once to allow installs from your browser or file manager). Android 10 or newer.

**User guide:** [USERGUIDE.md](USERGUIDE.md) (also opened by ⋮ → Help… in the app).

## Features

- **Browse Nextcloud folders** over WebDAV; long file names are shown in full, wrapped over several lines.
  The start shared by every file in a folder (e.g. the book title) is shown once above the list, so each row
  shows only what differs ("21 - Chapter 20 …").
- **Search** the current folder instantly, and all sub-folders (Nextcloud server-side search) after 3 letters.
- **Local storage** too: browse and play audio on the phone's memory or SD card.
- **Resume** every track where you left off; **History** of tracks or folders played; last track highlighted after restart.
- **Save to phone**: playing a track downloads its whole folder (SD card by default, Wi-Fi only by default),
  so it plays offline. Storage screen to see usage and delete folders not played for 30 days.
- **Favourites** (long-press a track or folder), **Statistics** (time listened, top tracks/folders, play counts).
- **Sleep timer** (minutes with fade-out, or end of track) and **playback speed** 0.75×–2×.
- Background playback with lock-screen / notification controls and a **home-screen widget**
  (play/pause resumes the last track).
- **Help** and **About** (version, build date, check for and install updates from GitHub releases).
- A single tap only selects a track; play it from the long-press menu, so a stray touch never starts playback.
- All audio is sent to Android as 44.1 kHz stereo, which avoids silent Bluetooth playback on some phones
  when switching between e.g. mono 22 kHz audiobooks and stereo music.

## Setup

1. In Nextcloud (web): **Settings → Security → Create new app password**.
2. In the app: enter your server address (e.g. `https://cloud.example.com`), user name and the app password.
   The password is stored only on the phone.

## Building

Android SDK + JDK 17:

```
./gradlew assembleDebug        # → app/build/outputs/apk/debug/m21-nextcloud-player.apk
```

Kotlin, AndroidX Media3 (ExoPlayer + MediaSessionService), OkHttp for WebDAV.
