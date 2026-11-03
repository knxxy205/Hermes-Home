# Beta release readiness

Review date: 2026-11-03. This is a record of local checks and remaining work, not a security certification.

## Repository history

- Gitleaks with its default rules and full redaction scanned all 31 commits reachable from the local Git refs and reported no findings.
- A separate Gitleaks scan of the current `app/src/main` source also reported no findings with its default rules.
- A separate check found historical password/API key literals in Settings presets and sample server configuration. Four distinct credential/example values have been replaced across the reachable Git history, including branches and tags.
- Exact-value scanning of the rewritten Git objects found zero occurrences of those removed values. The latest application source tree was unchanged by the rewrite; the change removed values from older versions.
- Older GitHub APK releases, workflow runs/artifacts, and build caches are removed during this cleanup. The tested APK from Build #28 is retained as Hermes Home v1.2 Beta, with the same bytes and signing key. Its source tag and source links use the rewritten history.
- Rotate or revoke every removed value that was ever used on a real server. Git cleanup does not invalidate a password, API key, or token. No server credentials have been changed by this cleanup.
- Other checkouts, previously downloaded APKs, and GitHub's cached/unreachable objects can retain the old data. Keep the repository private until live credentials are rotated. GitHub Support may need to purge cached views of the old commits.
- Scanning does not establish that every possible credential format has been detected. Use the rewritten history for future work; do not push old branches or tags back into the repository.

## Files verification

See [the Files beta test guide](files-beta-testing.md) for automated checks and device scenarios. Android's folder picker, long press gestures, and actual phone storage still need device testing.

Local verification completed with `:app:testDebugUnitTest :app:assembleDebug`: 23 tests passed, with zero failures, errors, or skipped tests, and the debug APK was built successfully. This includes the embedded SFTP integration test, search state regression test, document-provider tests, and existing screenshot test.

## Remaining distribution work

1. Run the device scenarios against a consistently signed APK; local build and automated verification are complete.
2. Set up a dedicated production signing key and preserve it securely for future updates. The existing GitHub workflow distributes debug APKs.
3. Prepare the signed beta APK, checksum, version, and release notes.
4. Finalize the privacy policy, security reporting channel, and the app's license alongside third-party notices.
5. Start a small beta group before wider promotion.

Repository visibility remains private. The retained APK is still a testing build, not a production release.
