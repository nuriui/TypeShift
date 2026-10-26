# TypeShift

A client-only Fabric mod for Minecraft that automatically converts English key sequences into Korean text in chat, as if you had typed them on a Korean 2-set (Dubeolsik) keyboard layout. No server installation is needed.

Inspired by [Catchtype](https://catchtype.com/), TypeShift is an independent implementation and is not affiliated with it.

## Features

- Converts as you type: `dkssud` → `안녕`.
- Works in chat and the Creative inventory search box. Item searches use Minecraft's selected display language.
- Uses word and character statistics to handle mixed English/Korean input.
- Protects recognized game terms, player names, URLs, file paths, and commands from conversion.
- Supports continued editing and undo with Ctrl+Z (Cmd+Z on macOS).
- Provides keep/convert word lists through Mod Menu. After you undo a word's conversion three times, it is added to the keep list automatically.
- Runs fully offline, with adjustable sensitivity.


## Requirements

- Java 25+
- Fabric Loader 0.19.5+
- Fabric API
- Mod Menu (optional, but needed for the dictionary settings screen)

Disable any other automatic language converter mods to avoid conflicts.

## License

- **Code and local game/technical word lists:** [MIT](LICENSE.txt), copyright © 2026.
- **Bundled [Catchtype public data](https://catchtype.com/data):** [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), published by Hebbian AI Co., Ltd.

The Catchtype data derives from Hermit Dave's FrequencyWords / OpenSubtitles data and is included unchanged. When redistributing, preserve the attribution and license notices. If you adapt the data, mark your changes and release the adapted data under the same CC BY-SA 4.0 license. See [NOTICE.md](NOTICE.md) for full credits and upstream changes.
