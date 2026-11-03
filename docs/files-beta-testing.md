# Files beta testing

Use a disposable server folder and sample files for these checks. Record the APK commit, Android version, device, document provider (local storage, SD card, or cloud), and SSH server version with any report. Do not include passwords, API keys, or personal file contents.

## Automated coverage

Run `gradle :app:testDebugUnitTest :app:assembleDebug` with Gradle 9.3.1, Java 17, and the configured Android SDK.

- `DownloadDestinationTest` uses an Android document provider to check name collisions, partial-file cleanup after failure/cancellation, and inaccessible providers.
- `SftpFilesIntegrationTest` runs an embedded SFTP server to check recursive search scope, actual downloaded content, names containing wildcards, transfer failure, and deletion of the exact selected path.
- `FilesViewModelTest` checks rapid query changes, refresh during search, stopping search, and clearing search to restore browsing.

## Checks on an Android device

These checks still need a physical device or emulator. Passing unit tests is not a substitute for checking Android's picker and gestures.

Use an APK signed with the same key as the installed build when testing updates. Local debug builds use Android's default debug key unless `DEBUG_KEYSTORE_PATH` is configured; GitHub builds use the repository's persistent testing key. Different signatures cannot update one another. Use a separate test device or a consistently signed build rather than uninstalling an existing installation that contains important settings.

1. Open a server folder, search for a nested file, replace the query several times, then clear it. The original directory must return without restarting the app or showing a credential error.
2. Start searching a large directory. Results should appear progressively; tap Berhenti and then clear the query. Browsing must remain usable.
3. Long press an item and select several others. Verify checkboxes, Back to cancel selection, and the exact paths in the delete confirmation. Cancel the confirmation and verify nothing was deleted.
4. Download a single file and then several files. Choose a writable folder in Android's picker; check the actual bytes in the phone's file manager. Cancel the picker and confirm no download starts.
5. Select identically named files from different search results. Pre-create a file with that name in the phone's destination folder. Verify all versions survive with numbered names and the existing file is unchanged.
6. Disconnect the server mid-download, or revoke write access to the destination. The app must report failures, retain completed files, and attempt to remove partial files. Reconnect and retry.
7. Select a file and a nonempty directory for deletion. The file can succeed while the nonempty directory fails; check that the app reports both outcomes and retains the directory's contents.
8. Open the folder picker for a batch and rotate the screen before choosing the destination. Verify that the original files and original server configuration remain associated with the batch.
9. Check a second connection profile. A completed operation on the first server must not replace the second server's file listing.
10. Open a file with a long name. Its extension badge should remain visible. Open Informasi file from the three-dot menu and verify the full name, original server path, byte count, modification time, and permissions. Check Informasi folder as well.

## Before public distribution

This work improves the Files beta. A production signing key, release APK, repository history scan for credentials, privacy documentation, and license review remain separate release requirements. Current GitHub automation publishes debug builds.
