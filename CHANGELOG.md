# Changelog

## Unreleased — beta preparation

- Prepare 1.3.1 with version code 5 and minification/resource shrinking disabled after a launch crash was reported for 1.3.0-beta.1 on both update and fresh install. The root cause is not yet confirmed.
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
