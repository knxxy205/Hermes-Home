# Hermes Home

Hermes Home is a native Android app for managing your own homeserver from your phone. It brings an SSH terminal, an SFTP file manager, and a chat interface for a self-hosted Hermes Agent together in one app.

The app is designed for private networks such as Tailscale. Keep SSH and Hermes services on your private network instead of exposing them directly to the public internet.

**Beta status:** `1.3.0-beta.1` has a reported startup crash on both updates and fresh installations. Do not install that candidate. A `1.3.1` replacement is being prepared with code/resource shrinking disabled and signed-APK emulator startup checks. See [the verification record](docs/release-readiness.md).

## Features

- **SSH terminal** with an interactive terminal emulator and host key confirmation.
- **SFTP file manager** to browse folders, create folders and files, upload and download files, preview supported files, rename, and delete.
- **Multi-select and recursive search** with file paths in results and a stop button for searches.
- **Downloads to a folder you choose** using Android's folder picker, with numbered filenames when a name is already in use.
- **File information** with full filenames, paths, extensions, sizes, modification times, and permissions. Extension badges stay visible beside long filenames.
- **Background uploads** that keep the destination folder with each queued batch.
- **Hermes chat** for an OpenAI-compatible, self-hosted Hermes Agent endpoint.
- **Connection profiles** for switching between server configurations.
- **Encrypted local credentials** using Android Keystore backed AES-GCM encryption.
- **Password protected profile backups** for moving settings between devices.
- **Biometric app lock** on supported devices.

## Requirements

- Android 7.0 (API 24) or later.
- An SSH server with SFTP enabled for terminal and file management.
- A private network connection to your server, such as Tailscale.
- An optional self-hosted Hermes Agent endpoint that implements an OpenAI-compatible API.

## Getting started

1. Install Hermes Home on your Android device.
2. Connect the device and homeserver to the same private network.
3. Open **Settings** and enter the server's SSH host, port, username, and password.
4. Confirm the SSH host key when prompted. A changed host key is rejected until you investigate and resolve the change.
5. Optionally configure the Hermes base URL, model, and API key. Use HTTPS for public hosts; HTTP is accepted for local or private network hosts.
6. Open **Terminal**, **Files**, or **Hermes** to use the configured services.

Hermes Home does not provide a hosted server or Hermes service. You operate and secure those services yourself.

## Beta limitations

- Download currently supports files; folders must be downloaded file by file. Only empty folders can be deleted.
- Downloads run while the app process is alive. They do not resume automatically after Android kills the app. Existing files in the destination folder are preserved; a failed transfer attempts to remove its partial file.
- Search covers the current remote folder and its subfolders. It does not follow symbolic links to directories. Searches stop at 5,000 results, 10,000 scanned folders, 64 levels, or a 20-second scan budget. A blocked network request can extend that time up to the connection timeout. The app labels partial results and folders it could not read.
- Cancelling search restores the last successfully loaded directory listing. Use Refresh to check for server changes.

See [the Files beta test guide](docs/files-beta-testing.md) before distributing a beta build.

## Build from source

The project uses Kotlin, Jetpack Compose, and Gradle. With Java 17, Gradle 9.3.1, and Android SDK 36.1 installed, build a debug APK with:

```sh
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs checks and stores a CI debug artifact for pushes and pull requests. This artifact uses a development signing key and is for testing; it is not published to Releases and should not be used to update an existing GitHub beta installation.

The distribution beta uses a non-debuggable release variant. Minification and resource shrinking are disabled in the 1.3.1 recovery candidate while the launch regression is investigated. See [release build and signing instructions](docs/release-build.md). The manual release workflow tests the signed APK on an emulator and prepares an artifact for review before optional publication.

## Security and privacy

- SSH passwords and Hermes API keys are encrypted locally with AES-GCM and a key held by Android Keystore.
- SSH host keys are checked and require confirmation before first use.
- Hermes HTTP requests are restricted to local or private-network hosts; use HTTPS for public hosts.
- Android cloud backup and device transfer exclude the app's shared preferences.
- Profile exports contain connection credentials. Protect them with a strong backup password and store the resulting backup securely.
- Connecting to a server and using its SSH or Hermes service sends data to that server. Review the server and network you configure.

Read the [privacy policy](PRIVACY.md) and [security reporting instructions](SECURITY.md). These documents are also available offline in Settings in the 1.3 beta. This version removes the unused Firebase/Gemini template SDKs; Hermes requests go to the endpoint you configure.

## Third-party notices

Terminal emulator and renderer sources are based on Termux v0.118.0 and are included under `app/src/main/java/com/termux`. See the notices and license texts in `app/src/main/assets/licenses`. The upstream terminal emulator code carries an Apache-2.0 exception; other applicable Termux components are GPL-3.0-only. Each APK release links to the source commit used for that build.

## License

Hermes Home's original code is licensed under **GNU GPL version 3 only (GPL-3.0-only)**. You may use, modify, and redistribute it under those terms, including providing corresponding source when distributing covered binaries. The app is provided without warranty. See [LICENSE](LICENSE) for the full terms and [the recorded license decision](docs/license-review.md).

Third-party components retain their own notices and applicable exceptions. See [third-party notices](THIRD_PARTY_NOTICES.md) and the full license texts in `app/src/main/assets/licenses`.
