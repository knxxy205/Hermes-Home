# Terminal, application settings, and Hermes chat

This records the first terminal/chat preview. The later screenshot-driven chat revision, new-chat fix, and terminal session removal behavior are documented in [chat-v2-ui.md](chat-v2-ui.md).

This change improves everyday SSH use on a phone and makes application preferences affect the actual terminal and chat. The existing light application palette and dark terminal remain; the abandoned glass experiment is not included.

## Direction

ENERGY 1 / RHYTHM 1 / MOTION 1. Output is the main content. Toolbars stay compact, controls have at least a 48 dp target, and secondary actions live in menus or sheets.

| Choice | Reason |
| --- | --- |
| Dark terminal `#141729`, muted toolbar `#1B1E33` | Preserve the existing terminal identity and keep sustained reading comfortable. |
| Terminal output `#62AE91`, toolbar labels `#83CBB0` | Keep the established terminal palette with readable control labels. |
| White/slate application surfaces and blue actions `#1D4ED8` | Retain the default identity while giving new text actions sufficient contrast. |
| JetBrains Mono or the system monospace font | Keep terminal cells aligned; an existing bundled font avoids additional asset downloads. |
| Existing Jakarta typography for application UI | Preserve the application's existing reading style. |
| Compact chat header and on-demand slash commands | Reserve more screen space for answers while keeping model selection visible. |
| Explicit session names and connection labels | Make the target of typing and commands easier to identify. |

Calculated text contrast: terminal output 6.74:1, terminal toolbar 8.70:1, blue actions and user-message text 6.70:1, secondary chat text on white 4.76:1, and code text 13.24:1. These color-pair calculations cover the new material choices; they are not a full application accessibility certification.

## Working preferences

Settings has separate **Aplikasi** and **Koneksi & model** sections. Appearance preferences are stored locally in a dedicated SharedPreferences file, separate from the encrypted server vault. Changes apply immediately and survive reopening the app.

| Preference | Behavior |
| --- | --- |
| Terminal font size, 10 to 24 sp | Rebuilds the native renderer and resizes the PTY. Pinch gestures save the resulting size to the same preference. |
| JetBrains Mono / Mono sistem | Changes the actual terminal typeface and the Settings preview. |
| Extra keys | Shows or hides the terminal modifier/navigation row. |
| Keep screen on | Applies only while the Terminal tab is active. |
| Preview paste | Opens an editable review before pasting. Multiline paste always receives a review because shell handling can execute newlines. |
| Chat font size, 12 to 22 sp | Changes message bodies and the composer. |
| Message times | Shows actual stored message timestamps. |
| Enter sends | Controls hardware Enter and the IME send action; Shift+Enter retains a newline. |
| Concise / steps / detailed answers | Adds the selected style to the real request's system instruction. |
| Additional Hermes instructions | Persists up to 2,000 characters and includes them in subsequent requests. |

## Terminal interactions

- Long-press selects a word. Drag either endpoint to adjust the selection; the native action menu offers Copy, Paste, and Select all.
- Drag and fling browse scrollback. The Output terbaru action returns to the live viewport. Session switching retains each session's viewport.
- Pinch changes the font size within the same limits as Settings.
- Ctrl and Alt support one-shot input. Physical keyboard modifier bytes are sent correctly; Ctrl+Shift+C/V operate selection/clipboard.
- Search finds case-insensitive rendered text per terminal row in the current screen and scrollback, including wide Unicode cell coordinates. Previous/next move between matches. Refresh searches the latest buffer; results are limited to 1,000 matches.
- A command composer and saved-command search allow reviewing/editing text before insertion. Custom commands can be added, edited, and removed using the existing encrypted snippet storage.
- New sessions choose a saved host or an explicitly labelled local simulation. Up to five sessions can be selected, renamed, disconnected, or closed. Closing an earlier session preserves the currently selected session.

## Hermes chat and models

The header exposes the current model, history, and new conversation. Sending is disabled for empty drafts and while another operation is active. Clearing a conversation asks for confirmation. Reading older messages no longer forces a jump on every incoming answer; Pesan terbaru returns to the end.

`/model` opens a searchable model picker. `/model name` opens that picker with a filter, so selection uses the models actually returned by the configured endpoint. The picker has loading, error/retry, empty-catalog, no-search-match, and selected-model states. Selecting a model updates the encrypted active profile and the model used by subsequent requests without inserting a model directory into the conversation.

Answers render basic Markdown headings, emphasis, inline code, lists, tables, and fenced code. Code blocks retain indentation, scroll horizontally, and offer Copy, Run on server, and Open in terminal actions. Execution still requires confirmation, with the actual terminal target shown for an interactive command. Starting a connection from chat waits for connection success before sending the command.

The system instruction describes the configured target and the user's confirmation workflow. It asks Hermes to distinguish information supplied in the conversation from uncertain server state. Actual response quality still depends on the chosen model and endpoint.

## Preview and verification

The debug application is **Hermes Home Preview**, package `com.aistudio.homeserver.thns.preview`. Production identifiers and release signing are retained. No release publishing or source push is part of this change.

The Kotlin, Compose, Java, and runtime library versions remain at their original values. There is no new runtime dependency.

The final application sources built successfully with the original Java 17, Kotlin 2.2.10, and Compose BOM 2024.09.00 toolchain. All 40 JVM tests passed, with zero failures, errors, or skipped tests. These cover the existing SSH/file behavior plus rendered-text search, Markdown parsing, preference persistence, active-session preservation, physical Ctrl/Alt delivery through a loopback SSH server, model catalog handling, and response preferences in the actual HTTP request.

Debug lint completed with 85 warnings and zero errors. No `NewApi` or `InlinedApi` issues were found. Remaining warnings include dependency/resource suggestions and the custom View accessibility lint warning; terminal taps call `performClick()` through the gesture detector.

Initial native runs were obstructed by an Android System UI unresponsive-process dialog. The app crash log was empty. Those screenshots are retained under `app/build/outputs/usability-verification/native/` for diagnosis and are not accepted as a clean visual result. System UI traces showed a stall during theme initialization alongside Google-service timeouts. Unneeded Google packages were disabled only on the disposable emulator. A later `com.google.android.configupdater` crash was also isolated to that emulator package, which was then disabled.

The test guard rejects unresponsive-process overlays. Native keyboard dismissal hides the IME on the application's activity and dialog windows and waits for the insets to settle. It does not send Back, which could navigate away after the keyboard had already closed. Long presses use Android's real event timing for the native terminal gesture detector.

The named preview APK passed fresh installation, launch, device-credential PIN authentication, and navigation through Settings, Files, Hermes, and Terminal. Screenshots for all four tabs were inspected at 720 × 1280 pixels, density 320. Startup crash output is empty and the captured log contains no fatal exception or ANR. Evidence: `app/build/outputs/usability-verification/startup/` and `startup-result.txt`.

Native screenshots confirm the terminal word selection and system Copy/Paste/Select all toolbar, searchable model sheet, and formatted reply with code actions. The chat code-copy clipboard assertion passed after IME handling was corrected. The fixture request must be parsed as JSON when checking the selected model because Android's serializer escapes `/` in the raw body.

Delivered preview: `app/build/outputs/apk/debug/Hermes-Home-Terminal-Chat-preview.apk`, 20,595,921 bytes. SHA-256: `1b72e89f6b83036cb1dfc7893d3f61c19f7485f80abf79c459eb186537a83eda`. It matches `app-debug.apk`; the debug signer was verified. Source changes are uncommitted and nothing was published or pushed.

Final native result: **OK (4 tests)** in 53.319 seconds, with an empty crash log. Evidence: `native-system.txt`, `native-system-crash.txt`, and `native-system/` under the verification output folder. Terminal copy was exercised through the system toolbar and verified against the clipboard, a second simulation session was opened and listed, and a custom saved command was created. The model/chat test verified the parsed JSON model ID, response instructions, rendered Markdown, and copied `df -h` code. The final lint run again completed with zero errors and 85 warnings. `summary.json` records the results and artifact checksum.

Tests use a loopback SSH server and local HTTP fixtures; fixture model names and answers are test data, not claims about a real homeserver. Physical-device performance and live-model response quality have not been measured.

### Interaction evidence and scope

| Interaction | Evidence |
| --- | --- |
| Terminal font increase | Native test changes 12 to 13 sp and inspects the actual `TerminalView` preference. |
| Keep screen on | Native test enables it in Settings, verifies the terminal View, changes tabs, and verifies it is cleared. |
| Saved preferences | JVM test reopens the store and verifies another reader receives updates. |
| Command composer | Native test inserts `echo comfort-check`, sends Enter, and checks the terminal buffer. |
| Terminal selection and copy | Native long press selects `select`; the system toolbar action copies exactly that text to the clipboard. |
| Session sheet and saved commands | Native test opens a second simulation, checks the two-session sheet, saves a custom command, and verifies encrypted storage. |
| Output search | Native test enters `comfort-check`; JVM tests verify occurrences, scrollback, case folding, and Unicode cell coordinates. |
| Model picker | Native test opens it with `/model`, searches for beta, selects the returned fixture model, and verifies encrypted storage and Settings. |
| Model error, retry, and empty catalog | Native fixture changes from HTTP failure to an empty list to a populated list; the test exercises retry and reload. |
| Model and response instructions | JVM test inspects the real request JSON; the native chat test also checks the selected model and default response style in its fixture request. |
| Reply formatting and code copy | Native test checks the Markdown heading, clicks Salin kode, and verifies exactly `df -h` in the clipboard. |
| Physical Ctrl/Alt | Loopback SSH test inspects the bytes received by the shell. |
| Closing sessions | JVM test closes an earlier session, checks the retained active ID, and checks the last-session guard. |

Font-family chips, other preference switches, reset, snippet edit/delete, session rename/close confirmations, search navigation, pinch/fling handlers, conversation history, and command execution confirmations have also been inspected in source. Source inspection is separate from native interaction evidence; this preview does not claim exhaustive physical-device or accessibility testing.

### Scoped antislop delivery review

- **PASS, content and identity:** the existing application direction and colors are retained, with reasons recorded above. No generated visual assets, testimonials, marketing statistics, or performance claims were added. The new interface copy contains no em dash.
- **PASS, purpose and composition:** monospace serves terminal cell alignment, the dark canvas serves sustained terminal reading, and sheets keep model/session choices available without crowding the output. Dials remain ENERGY 1 / RHYTHM 1 / MOTION 1.
- **PASS, controls and data states:** changed controls have actual handlers; native tests exercise Settings changes, command insertion, search, selection/copy, sessions, saved commands, model choice, retry/empty states, and chat code copy. Additional handlers were inspected as distinguished in the interaction evidence above.
- **PASS, mobile reading and semantics within the reviewed scope:** the 360 dp phone viewport and keyboard transitions were inspected. Long code and model lists scroll within their containers. New controls use Compose buttons, switches, radio semantics, field labels, and named icons; text contrast calculations are recorded above. Physical keyboard Ctrl/Alt delivery is covered by the SSH test. Comprehensive assistive-technology testing remains outside the measured evidence.
- **PASS, build and runtime evidence:** 40 JVM tests, four native tests, fresh-install/PIN/four-tab smoke, zero lint errors, and empty final crash logs. This review covers the preview changes; it does not certify every preexisting application screen or unmeasured device configuration.
