# Nextcloud Player – User Guide

Nextcloud Player plays music and audiobooks from your Nextcloud server and from the phone's own storage.
It is designed to make long file names easy to read and to pick up every track where you left off.

- [Getting started](#getting-started)
- [The main screen](#the-main-screen)
- [Playing tracks](#playing-tracks)
- [Searching](#searching)
- [Resume and History](#resume-and-history)
- [Favourites](#favourites)
- [Saving folders to the phone (offline)](#saving-folders-to-the-phone-offline)
- [Phone storage and SD card](#phone-storage-and-sd-card)
- [Sleep timer and speed](#sleep-timer-and-speed)
- [Home-screen widget](#home-screen-widget)
- [Statistics](#statistics)
- [Updates](#updates)
- [Troubleshooting](#troubleshooting)

---

## Getting started

1. **Install**: download `m21-nextcloud-player.apk` from the
   [latest release](https://github.com/harrowmd/Android-Nextcloud-Player/releases/latest) and open it on the phone.
   Android asks once to allow installs from your browser or file manager.
2. **Create an app password** in Nextcloud (web browser): **Settings → Security → Create new app password**.
   Use this, not your main Nextcloud password. You can revoke it at any time from the same page.
3. **Log in**: when the app first opens, enter
   - the server address, e.g. `https://cloud.example.com`
   - your Nextcloud user name
   - the app password

   The password is stored only on the phone. To change it later: **⋮ → Nextcloud account…**

## The main screen

| Part | What it does |
|---|---|
| **Up** | Go to the parent folder (the phone's Back button does the same). |
| Path and folder name | The small line is the path; the big text is the folder you are in. |
| **⋮** | The menu (see below). |
| Info line | "Folder has 64 audio tracks, all start with: …" – the shared start of the file names is shown once here, and each row shows only what differs. |
| **Search** | Filter this folder; also searches sub-folders (see [Searching](#searching)). |
| List | Folders first, then tracks. |
| Player bar | Appears once something is loaded: title, position, controls, speed and sleep timer. |

Each track row shows its name in full, then a details line, for example:

`♥  ⏱ 38:04 · ▶ 12:30 of 38:04 · on phone · mp3 · 17.9 MB`

- **♥** – a favourite
- **⏱** – length of the track (known once played or saved on the phone)
- **▶ 12:30 of 38:04** – where you stopped; **✓ played** – finished
- **on phone** – a copy is saved on the phone and plays without the internet

### The ⋮ menu

- **Go to History / Go to Favourites / Go to Nextcloud / Go to local storage**
- **Statistics**, **Refresh**
- **Sort: newest first** (otherwise A–Z; names starting with `_` always come first)
- **Show only audio files** (on by default)
- **Offline storage…**, **Nextcloud account…**
- **Help…** (this guide), **About…** (version, check for updates)

## Playing tracks

- **Tap a folder** to open it.
- **Tap a track** to *select* it (highlighted). A single tap never starts playback, so a stray touch can't
  interrupt what you are listening to.
- **Long-press a track → ▶ Play** to play it. The other tracks in the folder become the queue, so the next
  chapter follows automatically.
- Long-press also offers **♥ Add to favourites**, **⬇ Save to phone** (folders) and **ℹ Details** (the exact
  original file name, size and date).

### Player bar

| Control | Action |
|---|---|
| ⏮ / ⏭ | Previous / next track in the queue |
| −15 / +15 | Jump back / forward 15 seconds |
| ▶ / ⏸ | Play / pause |
| Slider | Drag to any position |
| Title | Tap to go to the playing track's folder |
| **Speed** | Playback speed (see below) |
| **Sleep timer** | Stop after a while (see below) |

Playback continues with the screen off. Use the lock screen or notification controls, or Bluetooth / headset
buttons. Playback pauses automatically when headphones are unplugged.

## Searching

Type in **Search**:

- Matches in the **current folder** appear instantly. Every word must appear, in any order – `alto peace`
  finds "Peace White Dove Full Alto".
- After **3 or more letters** the app also searches **all sub-folders** below the current one
  ("Searching sub-folders…"). Results from deeper folders are added below, with their folder name.
  Search from a top folder to search your whole library.
- Tap ✕ to clear the search.

## Resume and History

- Every track's position is saved while it plays and whenever you pause, skip or close the app.
- Playing a track you have started resumes **3 seconds before** where you stopped. Tracks played to the end
  (or within 15 s of it) start again from the beginning.
- After a restart, the app reopens the last folder with the last track highlighted.

**⋮ → Go to History** shows everything you have played, newest first. The switch at the top changes between:

- **Tracks** – one row per track. Long-press → Play opens its folder and resumes it.
- **Folders** – one row per folder, e.g. "5 played (3 finished) · 2 h 10 m". Tap to open the folder at the
  track you last played there.

History keeps the latest 1,000 tracks.

## Favourites

- Long-press any track or folder → **♥ Add to favourites** (or **💔 Remove from favourites**).
- **⋮ → Go to Favourites** lists them. Tap a folder to open it; long-press a track → Play to resume it.

## Saving folders to the phone (offline)

When you play a track from Nextcloud, the app saves **the whole folder** to the phone in the background
(a spinner shows "Saving to phone: … 12 of 64"). Saved tracks show **on phone** and play from the phone,
also without internet. If Nextcloud can't be reached, folders show what is saved.

To save a folder without playing it: long-press it → **⬇ Save to phone**.

**⋮ → Offline storage…** shows where files are saved and how much space is used, and lets you:

- turn automatic saving on or off
- allow downloads on Wi-Fi only (on by default, so no mobile data is used)
- **Change location…** – phone storage or SD card (SD card is the default when present)
- **Saved folders…** – list with size and last played date; tap one to delete its saved copy, or
  **Delete not played for 30 days**. Deleting only removes the copy on the phone; Nextcloud keeps the original.

Note: saved files live in the app's own folder (`Android/data/com.manytwo.ncplayer`) and are **removed if the
app is uninstalled**. Updating the app keeps them.

## Phone storage and SD card

**⋮ → Go to local storage** lists the phone's storage (internal and SD card). Android asks once for
permission to read music and audio files. Browse and play exactly as with Nextcloud – resume, history,
favourites, search and statistics all work for local files too.

## Sleep timer and speed

- **Sleep timer** (player bar): 5–90 minutes, **End of this track**, or **Off**. The button shows the time left.
  A minutes timer fades out over the last 20 seconds, then pauses; your place is saved.
- **Speed** (player bar): 0.75× to 2.0×, voice pitch unchanged. The choice is remembered.

## Home-screen widget

Long-press an empty spot on the home screen → **Widgets** → **Nextcloud Player**, and drag it into place.
It shows the current track with ⏮ ▶/⏸ ⏭ buttons; tap the title to open the app. Pressing ▶ when nothing is
loaded (e.g. after a reboot) continues the last track from where you left off.

## Statistics

**⋮ → Statistics** shows total time listened (today, last 7 and 30 days, best day), number of plays
(a play counts after 30 seconds), tracks finished, and top 10 folders and tracks.
**Clear stats** resets the statistics (history, resume points and favourites are kept).

## Updates

**⋮ → About…** shows the version and build date. **Check for new version** looks for a newer release on GitHub;
if there is one, **Install update** downloads it and opens Android's installer. The first time, Android asks
you to allow Nextcloud Player to install apps ("Allow from this source") – turn it on, come back and tap
**Install update** again. Your settings, history, favourites and saved folders are kept.

## Troubleshooting

| Problem | Try |
|---|---|
| "Login failed" | Check the user name and create a new app password (⋮ → Nextcloud account…). |
| No sound over Bluetooth | Pause and play again; if it persists, turn Bluetooth off and on. |
| Download "waiting for Wi-Fi" | Connect to Wi-Fi, or allow mobile data in ⋮ → Offline storage…. |
| Download stopped: storage full | Delete saved folders in ⋮ → Offline storage… → Saved folders…. |
| A track has no length (⏱) | Lengths appear once the track has been played or saved on the phone. |
| Local storage is empty | Allow "Music and audio" access for Nextcloud Player in Android Settings → Apps. |
