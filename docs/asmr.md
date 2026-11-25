# Voice ASMR

Voice ASMR uses the Voice bookshelf and player for an ASMR2 server. Its app ID is
`de.ph1b.audiobook.asmr`.

1. Connect the phone and the ASMR2 server to the same network.
2. Open **Voice ASMR**. The bookshelf loads from the default server at `http://192.168.5.103:8899`.
3. Select a work on the bookshelf to open the Voice player. Use its chapter controls to select an audio track.
4. To use another server, open **Settings → Library → ASMR**. Enter its address and sync the bookshelf.

The player shows Chinese subtitles when the track has them. Voice manages playback, bookmarks, and listening progress.

The ASMR2 server provides the catalog, audio, covers, and Chinese WebVTT under `/api/voice/`.
Its catalog reads track durations with PyAV.

Build with `./gradlew :app:assembleFossDebug --no-scan`.
