# Changelog

## 1.3.2 (unreleased beta candidate)

- Refresh Terminal, Files, Hermes, Settings, and manual unlock, with light/dark/system themes and Indonesian/English/system language choices.
- Add floating four-tab navigation with direct drag tracking, reduced redraw work, and page backgrounds behind its outside margins. Request the highest supported refresh rate at the current display resolution; actual FPS is not measured.
- Keep SSH sessions under an application-owned foreground service, retain session viewports across tab changes, and add bounded transport reconnect behavior. Keep host-key and authentication failures explicit.
- Add terminal pixel scrolling, visible-row rendering, keyboard resize handling, selection/copy, output search, command editing/snippets, and configurable fonts/extra keys.
- Use full-width Files rows, breadcrumbs/direct paths, saved sort/hidden-file preferences, upload source retention, encrypted file-level transfer history, notification navigation, cancellation, and retry of unfinished files.
- Support saved provider-specific settings for custom OpenAI-compatible servers, Gemini, OpenAI, Claude Messages, and OpenRouter.
- Replace the bouncing draggable model sheet with a fixed searchable picker, keep the current model first, and add a model button in the chat header.
- Add saved `/effort low|medium|high` and `/effort default`, provider-specific request fields for recognized model families, and omission for custom endpoints/unknown models.
- Add saved chat history, Markdown/code rendering with code-copy and confirmed command actions, answer-style preferences, timestamps, and configurable send behavior.
- Prepare the main release package as version 1.3.2, code 6, preserving the existing signing workflow and disabled shrinking. Debug/compact previews remain separate applications.
- Update README feature documentation, setup/update instructions, testing scope, and screenshots from `image/`.
- Validate the latest runtime source with 73 passing JVM tests and Android-test APK assembly. Unsigned 1.3.2 release assembly passes; a signed retained-data device update remains to be tested.

## 1.3.1 beta preparation (historical)

- Prepare 1.3.1 with version code 5 and minification/resource shrinking disabled after a launch crash was reported for 1.3.0-beta.1 on both update and fresh install. Emulator logs reproduced the old crash: WorkManager initialization could not construct `androidx.work.impl.WorkDatabase_Impl` because the optimized APK lacked its no-argument constructor.
- Confirm the signed 1.3.1 APK passes fresh installation, launch, device-PIN authentication, and navigation through Terminal, Files, Hermes, and Settings on an Android 14 emulator. Its crash log was empty; physical-phone testing remains outstanding.
- Add signed-APK emulator checks for startup, device-credential unlock, and tab navigation before distributing review artifacts.
- License Hermes Home's original code under GPL-3.0-only with maintainer approval; retain third-party notices and include the app license offline in Settings.
- Prepare version 1.3.0 with a non-debuggable release variant, code/resource shrinking, and a manual signed-beta workflow that defaults to artifact review.
- Remove unused Firebase/Gemini, Google Services, Room/Moshi/Retrofit template dependencies and secret-to-BuildConfig generation.
- Validate Hermes private HTTP URLs at every redirect and verify their resolved network addresses.
- Limit release HTTPS trust to system certificate authorities; additional user-installed roots remain a debug-only override.
- Add offline privacy, security, and third-party notices in Settings.

- Show recursive search results progressively, with a stop button and notices for incomplete searches.
- Restore the previous folder listing when search is cleared, and prevent an older search from replacing a newer result or showing a stale connection error.
- Save single and multiple downloads to a folder chosen through Android's folder picker.
- Keep existing destination files and give same-named downloads numbered filenames. Attempt to remove incomplete files when a transfer fails.
- Show file extension badges beside long filenames and add file/folder information to the three-dot menu.
- Show selected-item checkboxes, exact paths in delete confirmations, and separate success/failure counts for batch deletion. Nonempty folders are retained.
- Treat wildcard characters in selected SFTP paths literally for browsing, download, and deletion.

Downloads do not resume automatically after the app process is killed. Folder downloads are not supported. See [beta limitations](README.md#beta-limitations) and [device checks](docs/files-beta-testing.md).
