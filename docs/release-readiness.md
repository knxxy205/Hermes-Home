# Beta release readiness

Review date: 2026-11-03. This is a record of local checks and remaining work, not a security certification.

## Repository history

- Gitleaks with its default rules and full redaction scanned all 31 commits reachable from the local Git refs and reported no findings.
- A separate Gitleaks scan of the current `app/src/main` source also reported no findings with its default rules.
- A separate check of historical Kotlin source found 14 password/API key assignment occurrences across old versions of the Settings preset code. These are historical versions of preset values, not necessarily 14 distinct credentials. They are absent from the current preset.
- Before making the repository public, confirm those values were examples only. Rotate any that were ever used on a real server. Deleting a value from the latest commit does not remove it from Git history.
- The scan does not establish that every possible credential format has been detected. No Git history was rewritten and no server credentials were changed.

## Files verification

See [the Files beta test guide](files-beta-testing.md) for automated checks and device scenarios. Android's folder picker, long press gestures, and actual phone storage still need device testing.

Local verification completed with `:app:testDebugUnitTest :app:assembleDebug`: 23 tests passed, with zero failures, errors, or skipped tests, and the debug APK was built successfully. This includes the embedded SFTP integration test, search state regression test, document-provider tests, and existing screenshot test.

## Remaining distribution work

1. Run the device scenarios against a consistently signed APK; local build and automated verification are complete.
2. Set up a dedicated production signing key and preserve it securely for future updates. The existing GitHub workflow distributes debug APKs.
3. Prepare the signed beta APK, checksum, version, and release notes.
4. Finalize the privacy policy, security reporting channel, and the app's license alongside third-party notices.
5. Start a small beta group before wider promotion.

The repository visibility and GitHub releases have not been changed as part of these local checks.
