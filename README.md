<div align="center">

# Lyrix

**Live, synced lyrics of your Spotify song, right inside Minecraft.**

Fabric · Minecraft 1.21.11 · Client-side

[Documentation](https://docs.st3ix.net/docs/plugins/lyrix) · [Discord](https://discord.gg/6NQe7SaZtC) · [st3ix.net](https://st3ix.net)

</div>

---

Lyrix connects to your Spotify account and shows the lyrics of the song you are
listening to, in sync, while you play. Words can pop up in the world where you
look (like a TikTok edit), or sit in a clean HUD at the top, left or right.

## Features

- **3D lyrics in the world.** Every word appears a few blocks in front of you and stays where it spawned.
  Shader-safe by default, so Iris shaders don't darken the text.
- **HUD mode.** Top, left or right, with a karaoke fill that follows the singing.
- **Effects.** Pop, fade, typewriter, slide, with adjustable speed and fade out.
- **Now Playing banner.** Cover, title, progress and an equalizer. Tinted in the cover color, any corner, any size.
- **Own menu (press `K`).** Player controls, full synced lyrics view, search, queue, devices and likes.
- **Fully configurable.** Colors, font, size, distance, timing, sync offset and more.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.11 |
| Mod loader | [Fabric Loader](https://fabricmc.net) 0.20.1+ |
| Dependency | [Fabric API](https://modrinth.com/mod/fabric-api) |
| Java | 21+ |
| Spotify | Any account. Controlling playback needs Premium |

## Quick start

1. Put `lyrix-<version>.jar` and Fabric API in your `mods` folder.
2. Start the game and press **K**.
3. Open the **Account** tab and follow the four steps. You create a free Spotify developer app,
   add the redirect URI `http://127.0.0.1:25588/callback` and paste its Client ID.
4. Play a song on Spotify. The lyrics show up on their own.

The full guide with step by step instructions, settings reference and FAQ is on
**[docs.st3ix.net](https://docs.st3ix.net/docs/plugins/lyrix)**.

## Building

```bash
./gradlew build
```

The jar ends up in `build/libs/`. Gradle needs **Java 25** to run (it finds an installed JDK 25 on its own).
The mod itself is compiled for Java 21.

## Privacy

- Your Spotify login stays on your PC (`config/lyrix/tokens.json`). Lyrix talks to Spotify directly and uses no
  server of its own.
- To find lyrics, the song title, artist, album and length are sent to [LRCLIB](https://lrclib.net).

## License

Lyrix is licensed under the **GNU GPL v3.0** with additional terms. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
You may fork and modify it, but you must keep the credit to St3ix, keep your fork open source and give it its own name.

Pull requests are currently not accepted. See [CONTRIBUTING.md](CONTRIBUTING.md).

Lyrix is not affiliated with Spotify. Lyrics are provided by [LRCLIB](https://lrclib.net) and belong to their rights holders.
