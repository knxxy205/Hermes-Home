# Building a distribution beta

The release variant is non-debuggable. Code minification and resource shrinking remain disabled in 1.3.2 after startup regressions in shrunk builds. The application ID remains `com.aistudio.homeserver.thns` so that a consistently signed build can update the installed app. Version 1.3.2 uses version code 6, following 1.3.1's code 5.

## Local verification

Use Java 17, Gradle 9.3.1, and the configured Android SDK 36.1. JVM tests require a Java 21 toolchain, installed locally or provisioned by the configured toolchain resolver:

```sh
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Without release signing configuration, this produces `app/build/outputs/apk/release/app-release-unsigned.apk`. An unsigned APK is for verification and must not be distributed as an installable release.

To sign locally, supply these environment variables through a private secret store/session:

- `RELEASE_KEYSTORE_PATH`: an absolute path outside the repository.
- `RELEASE_STORE_PASSWORD` and `RELEASE_KEY_PASSWORD`.
- `RELEASE_KEY_ALIAS`: the alias inside that keystore (default: `upload`).

Never put these values in a tracked file or publish the keystore. There is no checked-in key or hard-coded release password. Back up the signing key and its recovery information securely before public distribution. Losing it can prevent future updates.

## GitHub beta workflow

The manual **Prepare signed release beta** workflow builds a signed release APK, validates its signature and manifest, and uploads a review artifact. A separate job on a fresh runner then attempts a fresh installation, device-credential authentication, and tab navigation on an Android 14 emulator. This preserves the signed APK if the emulator infrastructure fails and keeps emulator storage separate from Gradle build output. Artifact availability alone does not mean runtime checks passed; inspect the emulator job and retained startup logs/screenshots. Workflow publication requires the runtime job to succeed, is an explicit optional input, and defaults to off.

To retry Android runtime checks without rebuilding the app, run **Test signed beta on Android** with the preparation run ID. The signed artifact must still be available.

The separate **Verify Android app** workflow runs for pushes/pull requests and does not publish Releases or receive the beta signing secrets. Its debug artifact is a development build and cannot be assumed to update an existing beta installation.

Debug and compact builds use package `com.aistudio.homeserver.thns.preview` and a development certificate. They install beside the main app. Removing the suffix alone does not make a preview compatible with the original release: both the main package and original signing identity must match. See [update compatibility](update-compatibility.md).

For beta update continuity it maps the existing private GitHub `DEBUG_KEYSTORE_BASE64` and `DEBUG_KEYSTORE_PASSWORD` secrets to the release signing configuration with alias `homeserver`. This reuses the previous beta's signing identity; the generated app is still the non-debuggable release variant. It does not expose or replace those secrets.

The workflow requires certificate SHA-256 `1638687c08785bb0c5887c09638834b173f0f7645813940e648c74ebd1dec79d`, independently verified from the retained `v1.2-beta.28` APK. This also catches an accidental replacement of the signing secret.

Changing to a different signing key without a planned migration will prevent updating existing installations with the same application ID. Decide and securely back up the long-term signing identity before wide distribution. Play App Signing is a separate enrollment decision.

GPL-3.0-only is applied in the root `LICENSE`, packaged inside the app, and included in the review artifact. Each APK must have matching source and build instructions available to its recipients. Public distribution requires public access to that source.

Before enabling publication, review the privacy policy, release notes, known limitations, and live-credential rotation. Repository visibility and GitHub cached-commit cleanup are separate actions; this workflow does not make the repository public.
