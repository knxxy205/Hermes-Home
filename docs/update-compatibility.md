# Updating an existing GitHub beta

The GitHub beta and local preview are separate Android apps. Installing a preview cannot replace the GitHub beta or access its private data directory.

| Property | Existing GitHub beta | Local test preview | Next release candidate |
| --- | --- | --- | --- |
| Application ID | `com.aistudio.homeserver.thns` | `com.aistudio.homeserver.thns.preview` | `com.aistudio.homeserver.thns` |
| Version code | 5 (1.3.1) | 5 in the delivered picker-fix preview | 6 (1.3.2) |
| Certificate SHA-256 | `1638687c08785bb0c5887c09638834b173f0f7645813940e648c74ebd1dec79d` | `e49d9e2e9573414fbc1a30bfabf7aa1006b75af4032d58efcf0afe6b1fd1b478` | Must match the GitHub beta certificate |

The old certificate was checked against both the published `signature.txt` asset and the downloaded APK on `v1.3.1-beta.1`. `apksigner verify` passes on that APK and its manifest confirms the main package, version 1.3.1, code 5. The previous release readiness record also independently matched that signing identity to the retained 1.2 beta. Its APK has SHA-256 `431b8a68b8a682ffaf0aa8860b8736c78295928212f7031a9239c5a428e4ac38`. The original download is retained under `app/build/outputs/update-compatibility/previous-release/`.

The release build already uses the main application ID. Its version is now 1.3.2 with version code 6. Minification and resource shrinking remain disabled, preserving the packaging configuration used by the working preview. Do not change the debug/compact preview identity to the main package or sign a release with the local debug key: a mismatched certificate would make Android reject an update.

## Signing availability

No local copy of the release keystore or release-signing environment variables was found. GitHub lists `DEBUG_KEYSTORE_BASE64` and `DEBUG_KEYSTORE_PASSWORD`; their values were not accessed. The existing `release-beta.yml` workflow maps those secrets to release signing with alias `homeserver` and rejects any certificate that differs from the existing GitHub beta. GitHub secret values cannot be downloaded through the CLI.

The local release output is therefore unsigned until the original signing key is securely available locally, or the prepared source is built through the existing GitHub signing workflow. An unsigned APK is not an installable update. Building an APK with a different key is not a substitute.

## Prepared local candidate

`assembleRelease` succeeds in 3m 13s, including vital release lint. The retained verification artifact is `app/build/outputs/update-compatibility/Hermes-Home-v1.3.2-unsigned.apk` (15,610,770 bytes). Its manifest confirms application ID `com.aistudio.homeserver.thns`, version 1.3.2, code 6, label Hermes Home, and a non-debuggable build. SHA-256: `ab9b7f44fa4e553b7af00c1be55dfe70a08f932318c6fbd35555d1cb48d12b24`. `release-build.log`, `validation-summary.json`, `apk-badging.txt`, and `SHA256SUMS.txt` record the result. This APK must not be installed or advertised as a signed update.

The tested runtime changes previously passed all 73 JVM tests; version preparation changes only version metadata. The production credential-literal guard and its six Python tests also pass. A retained-data device update has not been tested. Signing on GitHub requires making the prepared source available on a branch and dispatching the existing signing workflow with publication disabled. The maintainer subsequently authorized pushing the application changes and updated README/screenshots, along with the proposed signed-candidate build. Release publication remains disabled.

## Device check

Keep the installed GitHub beta and its data. Install the correctly signed candidate over that beta without uninstalling or clearing data. Check that saved profiles, credentials, chat history, manual unlock, and SSH/SFTP/chat still work. Preview-only data belongs to the separate preview app and does not move into the main app automatically. Use the existing password-protected profile export/import if profile migration is needed; it is not a chat-history migration.

The local preview should only be removed after confirming the main app has the data the user needs. Push/signing progress is recorded with the candidate's GitHub commit and workflow run; public release publication is a separate step.
