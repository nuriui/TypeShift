# Minecraft version verification

Target: **Minecraft 26.3**, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Loom 1.17.9, Gradle 9.6.1, Java bytecode 25 (tested using JDK 26).

The starting template specified Loom 1.19.3 with Gradle 9.6.1. Gradle reported that Loom 1.19.3 needs Gradle >=9.8.0. This implementation uses the compatible 1.17.9 toolchain and available 26.3 dependencies.

The request also mentioned Minecraft 1.21.5 Yarn signatures. These were verified against the official Fabric Javadocs, but **1.21.5 is not a supported runtime**:

- https://maven.fabricmc.net/docs/yarn-1.21.5+build.1/net/minecraft/client/gui/screen/ChatScreen.html
- https://maven.fabricmc.net/docs/yarn-1.21.5+build.1/net/minecraft/client/gui/widget/TextFieldWidget.html

| Role | Yarn 1.21.5 verified signature | Actual 26.3 signature |
| --- | --- | --- |
| Chat field | `ChatScreen.chatField: TextFieldWidget` | `ChatScreen.input: EditBox` |
| Text event | `TextFieldWidget.charTyped(char, int): boolean` | `EditBox.charTyped(CharacterEvent): boolean` |
| Key event | `keyPressed(int, int, int): boolean` | `keyPressed(KeyEvent): boolean` |
| Mouse | `ChatScreen.mouseClicked(double, double, int): boolean` | `mouseClicked(MouseButtonEvent, boolean): boolean` |
| Text write | `TextFieldWidget.write(String): void` | `EditBox.insertText(String): void` |
| Set text | `setText(String): void` | `setValue(String): void` |
| Cursor | `getCursor(): int` | `getCursorPosition(): int` |
| Selection | `setSelectionEnd(int): void` | `setHighlightPos(int): void` |
| IME preedit | No equivalent method in that widget | `preeditUpdated(PreeditEvent): boolean` |

26.3 signatures and bytecode were inspected directly with `javap -private` and `javap -c` against Fabric Loom's downloaded `minecraft-client.jar`. This version is unobfuscated; adding 1.21.5 Yarn mappings to this build would be incorrect. Current screen is `Minecraft.getInstance().gui.screen()`, not the older `Minecraft.screen` field.

Fabric's 26.3 migration note documents SDL and its text input focus integration: https://fabricmc.net/2026/09/15/263.html. Keyboard shortcuts use `InputConstants` and `KeyEvent` modifier helpers rather than GLFW constants. The original EditBox remains responsible for focus and IME overlay rendering.

Two client mixins are used: one accessor for ChatScreen.input and one EditBox event adapter. All adapter operations are gated on exact identity with the currently open ChatScreen's input. Other text widgets and gameplay input are untouched. Text changes use selected-range insertText rather than replacing the widget's whole value or its vanilla responder.

Creative search integration: inspected the 26.3 merged client bytecode with javap. CreativeModeInventoryScreen has private EditBox searchBox. Its charTyped(CharacterEvent) and keyPressed(KeyEvent) compare the widget value before/after accepted input and call refreshSearchResults(). The shared EditBox mixin now accepts only this exact focused, visible widget in addition to the chat input. A converted confirmation event returns true for the creative search widget so vanilla refreshes results on Enter. Player-name protection is populated only for chat. No inventory rendering, item filtering, or gameplay key mappings are replaced.
