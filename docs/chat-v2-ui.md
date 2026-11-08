# Hermes chat preview, reference revision

The user asked to retain the Hermes header from their first reference, make the conversation and code blocks read like the supplied ChatGPT screenshot, suppress execution actions on server results, fix repeated new-chat actions, and allow deleting the initial `bash 1` terminal session. No GitHub publishing is authorized.

## Visual direction

ENERGY 1 / RHYTHM 1 / MOTION 1. The chat body carries the content; the header and composer stay compact. White and blue remain the application identity. The retained header has the blue Forum icon, Hermes title, conversation/model subtitle, and add/history/clear actions. The subtitle opens the model picker.

Assistant replies sit directly on white, while user messages have a small light blue bubble. Platform sans-serif improves reading density in answers; Jakarta Sans remains in the header and application controls. Code uses monospace, a light grey surface, a language caption, and a copy icon at the top. Shell suggestions retain execution actions below the block and wrap on narrow screens; HTML and other non-shell code only offer copy. Navigation is a flat, compact bar with the existing four destinations.

## Behavior fixes

- Server execution messages carry a persisted `isServerOutput` marker. Their blocks never receive Run or Open in terminal callbacks, even when the block says `bash`.
- Previously saved execution replies are recognized by the existing anchored result heading, so old history also loses the inappropriate actions. Ordinary assistant suggestions still offer actions for shell blocks.
- Repeated + presses while already in a fresh conversation reuse that conversation. Input, pending command, and slash-menu state reset when a new conversation is created. Initial session loading disables new-chat actions until the active conversation is known.
- Deleting the active conversation no longer saves it again before creating a replacement.
- Terminal menus expose Manage sessions and Close session. The initial `bash 1` has the same close action as other sessions. Closing the final session leaves an empty session list and an explicit empty screen; a new session is created only when the user opens one or sends an interactive command to Terminal.

## Contrast

Computed text contrast on the light code background `#F3F3F3`: primary code text 13.23:1, comments/captions 5.50:1, strings 5.98:1, markup attributes 5.88:1, numbers 6.08:1, and keywords/tags 7.22:1. Main text on the user bubble is 13.13:1. The composer retains `#F7F9FC`, where secondary text is 4.51:1.

Provider and model action labels use `#1D4ED8` on white (6.70:1) to retain the requested blue identity with readable small controls. The list icon denotes the model catalog that the action opens.

## Terminal font

The user requested Fira Code in place of Mono sistem. Settings preview and the native terminal renderer now load the bundled static Fira Code Regular 6.2 TTF. Existing `system` preferences normalize to `fira`; JetBrains Mono remains the default and the other available option. The static font supports the existing minimum Android version without a variable-font dependency. Its unmodified SIL OFL notice is packaged in `assets/licenses/fira-code.txt`.

## AI providers

The user requested a small provider dropdown, including Google and OpenRouter, then explicitly added Claude. The dropdown contains five choices:

| Provider | API base | Chat protocol |
| --- | --- | --- |
| 9router / server custom | User supplied | OpenAI compatible; local model discovery fallbacks remain available |
| Google Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | Google's official OpenAI compatibility endpoint |
| OpenAI | `https://api.openai.com/v1` | Chat Completions |
| Claude (Anthropic) | `https://api.anthropic.com/v1` | Native Messages API, `x-api-key`, version `2023-06-01` |
| OpenRouter | `https://openrouter.ai/api/v1` | Chat Completions |

The compact list follows the user's preference; no unsupported popularity ranking is shown. Each provider remembers its own URL, key, and model within the active server profile. Switching to a new provider starts with an empty key/model; switching back restores the draft. Save persists all provider credentials in the existing encrypted vault. Password-protected profile exports include these settings, and older profiles without provider fields load as custom without changing their endpoint or key.

Settings and chat share the searchable model sheet, with loading, error/retry, empty, and no-search-match states. Model IDs come from the provider instead of a hardcoded catalog; manual entry remains available. Cloud preset URLs are read-only; use custom for proxies or different API bases. The connection check sends a short request to the selected model, checks that a chat reply is actually returned, and explains that it consumes provider quota. Authentication, balance, quota, and server errors have actionable messages. Claude's top-level system prompt, required output limit, native authentication, and text content blocks are handled separately from OpenAI's format.

Official API references checked during implementation: [Gemini compatibility](https://ai.google.dev/gemini-api/docs/openai), [OpenRouter](https://openrouter.ai/docs/api-reference/overview), [OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create), and [Claude Messages](https://platform.claude.com/docs/en/api/messages/create). No runtime dependencies were added. Provider transport is verified with local HTTP fixtures; live account access depends on the user's API key, model permissions, and quota.

## Verification

The application, Android test APK, and lint build passed. All 48 JVM tests passed with no failures or errors. Lint reports 85 warnings and zero errors. The APK signature matches the first preview, so the package can be installed as an update.

All seven native emulator tests passed in 113.164 seconds. They cover output provenance, old server history, lossless code highlighting/copy, repeated new-chat actions, active-chat deletion, removing `bash 1`, closing the final terminal session, reopening after the empty state, Fira Code persistence, provider switching, encrypted provider credentials, password-protected backup restoration, model search, and a connection check against a local HTTP fixture. The fresh-install smoke passed device-credential/PIN authentication and all four application tabs. Final native and startup crash logs contain no application crash.

Evidence is under `app/build/outputs/chat-v2-verification`: `release-check-build.log`, `native-tests.txt`, `native/usability/`, `startup-result.txt`, `startup/`, `signer.txt`, and `summary.json`. Native screenshots were inspected for the font setting, Google provider selection, HTML/legacy code, new server output, and empty terminal. Android's transient clipboard/toast overlays appear in some fixture screenshots.

## Control verification

| Control | Observed behavior |
| --- | --- |
| Fira Code and font size | Native test selects Fira Code and 13 sp, checks terminal preferences and keep-screen-on behavior, then reopens the preferences store. Legacy `system` migrates in the JVM test. |
| Provider dropdown | Native test switches Google, OpenRouter, Claude, and custom, checks URLs, restores Google's own key, and preserves the custom endpoint. OpenAI URL and authorization are additionally exercised by the JVM transport fixture. |
| Provider key visibility and storage | Show/hide uses the password transformation; provider switches hide the next key. Native tests verify that provider keys do not occur as plaintext in SharedPreferences. |
| Model selection/search | Settings and chat open the shared sheet; searching `beta` and choosing it updates the actual request. Loading/error, retry, and empty catalog states are exercised. |
| Cek koneksi AI | Native test clicks the visible button and confirms a POST and actual model reply. JVM fixtures verify native Claude and OpenAI-compatible request shapes, missing-key rejection, and quota errors. |
| Save and backup | Native test saves each provider draft, exports a password-protected backup, imports it, and checks that provider keys/models survive. |
| Chat + and history delete | Repeated + presses reuse the fresh conversation; deleting the active history entry does not restore it. |
| Code copy and execution | Copy puts exactly `df -h` on the clipboard. A shell suggestion opens confirmation, then yields a persisted server result with copy and no execution actions. HTML and legacy server output also omit execution actions. |
| Terminal selection, search, and snippets | Long press selects a word; the native Copy action copies it. Search finds output, and a custom snippet saves. |
| Terminal sessions | Close `bash 1`, close the final session, observe the empty state, then open a fresh simulation. |
| Startup and navigation | Install the signed preview, authenticate through the device PIN, and visit Settings, Files, Hermes, and Terminal without an application crash. |

## Delivery gate

This gate covers the requested changes. Unrelated server/file-management controls retain their previous implementation. Native controls use Compose/Android semantics; browser-only cursor, HTML, and media rules do not apply.

| Gate | Status and evidence |
| --- | --- |
| Copy and factual content (R-02, R-17, R-18, R-36, R-38) | PASS: added copy contains no em dash, invented usage ranking, testimonials, or performance/security claims. Provider API details come from official documentation; fixtures are explicitly tests. |
| Mobile layout (R-03) | PASS: native screenshots at 360 dp show contained code panels, readable controls, and an explicit empty terminal. Code content scrolls horizontally inside its panel. |
| Assets and direction (R-23, R-37) | PASS: the retained header and palette follow the user's references; Fira Code is explicitly requested and bundled with its OFL notice. No new logo/avatar was generated. |
| Contrast (R-25) | PASS: the measured message/code colors exceed 4.5:1; added provider/model control labels are 6.70:1 on white. |
| Navigation and behavior (R-24, R-26, R-35) | PASS: the control table records real native interactions, fixture requests, and the four-tab startup smoke. |
| Data states (R-27) | PASS: model loading, error/retry, empty catalog and search results are present; final terminal closure has a tested empty state. |
| FAQ (R-28) | PASS: none introduced. |
| Input and accessibility (R-32, R-39) | PASS by native tests and code inspection: standard Compose buttons, fields, dropdowns, radio selection, named icons, and Android terminal input are used. A separate exhaustive hardware-keyboard traversal was not run. |
| Source edits and themes (R-33, R-34) | PASS: implementation is in Kotlin/resources, with no source-rewriting helper; the requested white/blue app and dark terminal remain the shipped direction. |
| Media/performance claims (R-40) | PASS: no new content media, third-party scripts, or unmeasured performance claims. |
| Purpose gates (R-01, R-04, R-06 to R-10, R-12 to R-14, R-19, R-22) | PASS: blue indicates actions, List denotes the model catalog, requested monospace preserves terminal columns, and the compact controls reserve space for conversation content. No glass, glow, decorative animation, or illustrations were added. |
| Liveliness and quality locks (R-05, R-11, R-15, R-16, R-20, R-21, R-29 to R-31; C-1 to C-5) | PASS: ENERGY 1 / RHYTHM 1 / MOTION 1, with the retained Hermes identity and content-driven layouts documented above. The fixed terminal theme serves terminal reading; empty/error/loading behavior and test limits are recorded. |

## Local artifact

- File: `app/build/outputs/apk/debug/Hermes-Home-Chat-v2-preview.apk`
- Package: `com.aistudio.homeserver.thns.preview`, version `1.3.1-chat-preview`.
- Size: 21,269,779 bytes.
- SHA-256: `2e92f7ef458c7dd499d3668a87d9d611160b705a08cd8f56c410ab1fbdebc819`.
- Signer SHA-256: `e49d9e2e9573414fbc1a30bfabf7aa1006b75af4032d58efcf0afe6b1fd1b478`, matching the first preview.
- Local and uncommitted; no GitHub commit, push, or release performed.
