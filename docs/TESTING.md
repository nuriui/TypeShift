# Verification and remaining manual coverage

26.3.14: 142 JUnit cases pass. User dictionary coverage includes persistence, conflicting rules, deletion, three-undo learning across restarts, case-sensitive keys, immediate updates, word-boundary forcing, protected tokens, disabled mode, paste/IME exclusion, and recovery from failed saves. The real client smoke test opens the actual Mod Menu configuration factory, adds/searches/deletes a rule, verifies persisted content, checks widget bounds at 320×240, and renders a screenshot at `build/smoke-run/user-dictionary-screen.png`. The client also passes with `-PwithoutModMenu`, opening the screen directly to verify the optional dependency. Smoke runs reset only their own dictionary fixture and result file under `build/smoke-run`.

Run JUnit and package the production JAR:

```sh
./gradlew test build
```

Tests use the real bundled public data. They cover required examples and exact first conversion keystrokes, the full intermediate `안녕하세요` trace, all 11,172 precomposed Hangul syllables round-tripping through keys, compound vowels/finals, unknown words scored without dictionary entries, game terms, online-player prefixes, message-only command conversion, URL/path rollback, undo suppression, later-input preservation, middle insertion/deletion, directed selections, mixed language input, rapid input, external updates, IME preedit/commit, empty preedit initialization, length bounds, recursion guard cleanup, malformed data, configuration persistence, sensitivity, isolated experimental reverse conversion, reported forum sentences, component word evidence, Korean technical terms, punctuation boundaries and Enter finalization, persistent language state, dictionary-absent names, immediate composition, English restoration with case preserved, transitions in both directions, protection without mode changes, cursor/IME state resets, undo after temporary composition, numeric date/time suffixes with and without prior Korean context, numeric suffix undo/backspace, and numeric URL/path/player/paste protection, attached English-stem particles, literal-stem preservation on backspace, mixed-token undo, the reported TypeShift sentence, typo deletion/retyping, native deletion reconciliation with provenance checks, logical-key dispatch, selected suffix deletion, quoted English followed by Korean, dotted numeric version suffixes, Codex particles, version undo/deletion recovery retained filename/URL protection, numeric suffix continuation beyond the ending list, general Korean version suffixes, and their raw-key deletion/undo.

JUnit HTML report: `build/reports/tests/test/index.html`.

## Real client smoke test

```sh
./gradlew -PsmokeTest runSmokeClient
```

This launches the actual 26.3 client with a separate development-only mod, waits for resource loading, creates a test ChatScreen subclass containing the actual EditBox, and dispatches CharacterEvent, KeyEvent, and PreeditEvent through the mixed-in widget. It checks forward conversion, continued composition, raw backspace, undo, command and URL protection, middle cursor insertion, IME protection, external insertText, and exclusion of unrelated EditBoxes. It then returns to the title screen and shuts down. Result: `build/smoke-run/smoke-result.txt`; logs: `build/smoke-run/logs/latest.log`.

The test ChatScreen overrides initialization to avoid creating a world/server connection or sending messages. Therefore this test exercises real widget/mixin behavior, but does not prove Brigadier suggestions or OS-generated native IME event order in a multiplayer session. The smoke mod's source/resources are excluded from the production JAR.

## Manual acceptance matrix

The following require an interactive world/server and native keyboard/IME. They remain manual coverage, not a claim of automated validation:

1. Open vanilla chat in a local world; enter each sample character by character, including Shift+P, without spaces. Confirm the table in README and send the displayed message.
2. On Windows, macOS and Linux, compose Korean with a real OS IME, move its candidate selection, commit, cancel, then type English. Confirm no preedit changes, duplicated commits or swallowed keys. Reverse auto-conversion remains disconnected pending these checks.
3. Use Tab suggestions, cycle choices, Shift+Tab, chat history Up/Down, then edit a message. Confirm the vanilla suggestion responder and input history continue working.
4. Paste text, cut a selection, drag-select in both directions, move by arrows/Home/End, and undo a conversion. The default conservative behavior preserves edited tokens until a new whitespace boundary.
5. Test actual server player names and `/msg <player>`, including names resembling Korean key sequences. Unknown server-specific commands remain protected.
6. Fill a chat close to the 256-character limit, continue a converted token, and undo. No truncation of the original token should occur.
7. Close chat, use movement/gameplay keys, and use unrelated search/sign widgets. The mod must not transform those inputs.

No OS-specific end-to-end IME stability or multiplayer interoperability guarantee is inferred from synthetic input tests.

The direct-typing regression for `9시에 typeshift모드를 26.3.7버전을 테스트하고있어` also dispatches physical KeyEvent press/release around each CharacterEvent, including digits, periods and shifted Korean keys. It still uses the isolated test ChatScreen widget, not a multiplayer session or native OS keyboard pipeline. A passing result is not proof that a user-reported discrepancy in that full pipeline has been reproduced.

26.3.9: 120 JUnit cases pass. Added exact fifth-key conversion traces for `ahemr`, `ahems`, `ahemf`, boundary/Enter decisions for `ahem`, continued composition, middle insertion, backspace, undo, quoted English and protected-token checks. The real widget smoke test also dispatches key press/character/release events for the four observed sequences. Thresholds use the default sensitivity; these examples do not establish general language-detection accuracy.

26.3.10: 128 JUnit cases pass. Unknown-word boundary tests cover 단타, 단타가, 단타를, 광클 and 데코니나 without dictionary additions, plus cursor restoration, undo, sensitivity, disabled mode, player/command/URL/numeric protection and English controls. The client smoke test checks that eksxk stays literal until a space, then becomes 단타. This limited regression set is not a corpus-wide accuracy measurement.

26.3.11: 131 JUnit cases pass. New regressions cover first-token Eksrpa remaining literal until space/Enter, preserved uppercase raw text on undo, middle insertion and continuation, player/command/URL/numeric protection, lowercase 단겜 distinction, disabled mode and conservative sensitivity. The actual widget smoke test includes physical Shift-modified E followed by ks rpa and space. No native keyboard/multiplayer coverage is implied.

26.3.12: Added controlled numeric fixtures proving equal raw-key normalization and exclusion/inclusion of end markers, plus first-token dkak at exactly the fourth key, cursor/selection, boundary and undo. The sensitivity regression now uses an ambiguous unknown word (단타); strong known 안녕 can pass even at conservative sensitivity with the new score scale. A separate before/after direct boundary-decision comparison over all 46,330 English dictionary entries found zero newly converted and zero no-longer-converted entries relative to 26.3.11. This is a regression comparison, not proof that every prior decision is correct, and does not test every English prefix in the UI.

26.3.13: 135 JUnit cases pass. The new context matrix checks typeshift/TypeShift/catchtype/CatchType with five particles after empty, Korean, and English prefixes. It also checks backspace, raw undo, URLs, paths, commands and player protection. The client smoke matrix dispatches key/character/release events for these four names plus 를 in all three contexts. The reported intermittent literal typeshiftfmf was not reproduced; these tests do not establish its cause or fix. The reproducible catchtypefmf failure was corrected by constrained compound-English stem recognition.

Mixed-token follow-up: added direct-typing matrices for catchtype, TypeShift, PixelForge, ChatBridge, CPU, and GPU with single and combined particles, in initial/Korean/English contexts. Assertions check results before spaces as well as after boundaries. Further checks cover raw undo, backspace/retyping, URLs, paths, commands, ordinary English controls, and rejection of split candidates for every complete English dictionary entry. The latter is a whole-word protection check, not a measurement of all incremental English prefixes. Local-only client smoke tests also dispatch physical key/character events for the expanded name matrix and 에서도.
