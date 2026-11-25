# Voice ASMR

Voice ASMR adds a local network library to Voice. It installs as a separate app with the ID
`de.ph1b.audiobook.asmr`.

1. Connect the phone and the ASMR2 server to the same network.
2. Open **Voice ASMR**. You can also open **Settings → Library → ASMR** within the app.
3. Enter the server address, including `http://` and the port, then tap **Connect**.
4. Open a work and select a track. Use the language buttons to switch between Chinese and Japanese subtitles.

The screen shows the subtitle for the current playback position. It uses each track's available languages.
Playback pauses when the app leaves the foreground.

The app reads the library from `/api/library`, streams tracks from `/media/{work}/{track}`, and reads WebVTT
from `/subs/{work}/{track}?lang=zh` or `?lang=ja`. The server address comes from the settings screen.

Build with `./gradlew :app:assembleFossDebug`.
