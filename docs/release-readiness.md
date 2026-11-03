# Beta release readiness

Review date: 2026-11-03. This is a record of local checks and remaining work, not a security certification.

## Repository history

- Gitleaks with its default rules and full redaction scanned all 33 commits reachable from the local Git refs before the 1.3 preparation commit and reported no findings.
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

## Version 1.3 verification

The maintainer reported that the Files checks on the previous v1.2 beta worked on an Android device. The new 1.3 candidate still needs its own verification after build/signing.

The 1.3 preparation adds a non-debuggable R8 release variant, removes unused Firebase/Gemini template SDKs, validates every Hermes HTTP redirect and resolved address, and packages privacy/security notices offline in Settings. A manual workflow prepares a signed APK with the existing beta identity, and defaults to artifact review rather than publication.

- Android/JVM tests: 28 passed with zero failures, errors, or skipped tests, including five Hermes endpoint policy/integration tests.
- Production credential-literal guard: four Python tests passed, and the current production source scan passed. This narrow guard supplements secret scanning; it does not establish that all secrets have been detected.
- Optimized unsigned release build and release lint passed. Lint reports zero errors and 84 warnings, mainly dependency updates, Kotlin API suggestions, and unused resources. Remaining warnings also include intentional base cleartext support for dynamic private HTTP destinations, a terminal accessibility issue, and private API use in bundled terminal code.
- The final unsigned APK was inspected after the license/UI changes: application ID `com.aistudio.homeserver.thns`, version `1.3.0`, version code `4`, non-debuggable manifest, matching bundled legal documents, and no Firebase manifest entries or DEX class descriptors.
- The maintainer approved [GPL-3.0-only](license-review.md) for original app code on 3 November 2026. Root `LICENSE` includes the full GPL text; the app and release artifact include it alongside third-party notices.

## Signed candidate: 1.3.0-beta.1

**Device review failed:** the maintainer reported a force close on both update and fresh installation. Do not install or promote this candidate. Its automated checks below established package/signature properties, not successful Android startup. A 1.3.1 candidate with minification and resource shrinking disabled is being prepared with emulator startup/authentication checks; the root cause is not yet confirmed.

The first 1.3.1 preparation run completed its signed build and package verification, but Android runtime checks did not start: the runner had about 5.7 GB free while the emulator requested a 7.4 GB userdata partition. That run was canceled. The workflow now uploads the verified APK before attempting emulator startup, limits the emulator disk to 2 GB, and bounds boot time. An available artifact must not be mistaken for a passed startup test.

The [successful preparation run](https://github.com/knxxy205/Hermes-Home/actions/runs/41604258134) built and verified a signed candidate from source commit [`a5d671b12b791dde8be45c545047e3afdf419733`](https://github.com/knxxy205/Hermes-Home/commit/a5d671b12b791dde8be45c545047e3afdf419733). Workflow publication was disabled. The same verified APK was subsequently attached to [the private repository's 1.3.0 Beta 1 prerelease](https://github.com/knxxy205/Hermes-Home/releases/tag/v1.3.0-beta.1) for easier phone installation; its tag points to the matching source commit.

- [Review artifact](https://github.com/knxxy205/Hermes-Home/actions/runs/41604258134/artifacts/13216021219): `hermes-home-signed-release-beta`, retained for 14 days. Repository access and GitHub sign-in are required. Extract the ZIP to obtain `Hermes-Home-v1.3.0-beta.1.apk`.
- APK size: 2,295,417 bytes (approximately 2.3 MB).
- [Direct APK download](https://github.com/knxxy205/Hermes-Home/releases/download/v1.3.0-beta.1/Hermes-Home-v1.3.0-beta.1.apk) from the private prerelease requires repository access and GitHub sign-in. The release asset's GitHub SHA-256 digest matches the independently verified APK checksum below.
- APK SHA-256: `b5e50d7d04d78602f6bb14183cdae4e92b3e36ff7d66b730ff3328e8e07543be`.
- Signing certificate SHA-256: `1638687c08785bb0c5887c09638834b173f0f7645813940e648c74ebd1dec79d`, independently matched to the retained v1.2 beta APK.
- After downloading the artifact, local verification passed for the APK signature, checksum, application ID, version name/code, non-debuggable manifest, and exact bundled legal-document contents from the source commit. No Firebase manifest entries or DEX class descriptors were found.
- The artifact includes `SHA256SUMS.txt`, `signature.txt`, `LICENSE`, and the privacy/security/third-party documents. Test and lint reports are retained in a separate diagnostics artifact for seven days.

The app reports version `1.3.0`; the beta sequence is in the APK filename. Matching signing identity and a higher version code support an update from the retained 1.2 beta, but installation and runtime behavior still need device testing.

For the replacement's device review, keep a protected profile backup, then install over the existing beta without uninstalling or clearing app data. Check that saved profiles remain accessible, biometric/device unlock works, SSH/SFTP connections and file transfers work, private HTTP Hermes chat works, queued uploads still run, and the legal documents open offline from Settings.

## Remaining distribution work

### Recovery candidate: 1.3.1-beta.1

The [private 1.3.1 recovery prerelease](https://github.com/knxxy205/Hermes-Home/releases/tag/v1.3.1-beta.1) provides a [direct APK download](https://github.com/knxxy205/Hermes-Home/releases/download/v1.3.1-beta.1/Hermes-Home-v1.3.1-beta.1.apk) for phone testing. It was built from source commit `0d976789fbed90be077a55a3fcec6b0454436201`, with version code 5, minification/resource shrinking disabled, and the same beta signing certificate. It is non-debuggable.

- Size: 13,764,601 bytes (about 13.8 MB).
- SHA-256: `431b8a68b8a682ffaf0aa8860b8736c78295928212f7031a9239c5a428e4ac38`.
- Local inspection of the downloaded signed APK verified its signature/certificate, checksum, version and application ID, non-debuggable status, and legal assets against the exact source commit.
- Local automated checks passed: 28 tests, release build, and lint with zero errors and 84 warnings.
- [Preparation run](https://github.com/knxxy205/Hermes-Home/actions/runs/41611454866): signed build and package verification passed; emulator startup failed because its image still required more disk space than was available, despite the requested smaller userdata partition. No application startup test executed in that run.
- [Runtime checks on a separate fresh runner](https://github.com/knxxy205/Hermes-Home/actions/runs/41613642334) are independent of the build artifact. The recovery APK was made available for the requested phone test while that check was pending. The launch-crash cause remains unconfirmed.

### Before wider distribution

1. Test the signed candidate on a device, including updating the earlier beta without clearing app data, biometric unlock, SSH/SFTP, Hermes chat, and background uploads.
2. Confirm rotation/revocation of any historical credentials that were actually used, and resolve GitHub cached-commit removal before opening the repository. The old commit remained accessible through GitHub's API when checked on 3 November 2026.
3. Securely back up the signing identity and finalize the private reporting channel.
4. Review [the privacy policy](../PRIVACY.md), [security policy](../SECURITY.md), [third-party notices](../THIRD_PARTY_NOTICES.md), and [release instructions](release-build.md), then begin a small beta group. Public APK recipients must also have access to matching source and build instructions.

Repository visibility remains private. The retained 1.2 release and the new 1.3 review candidate are beta builds; public distribution is still pending.
