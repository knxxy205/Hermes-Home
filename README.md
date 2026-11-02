# TailHarness

TailHarness is a native Android app for managing your own homeserver from your phone. It brings an SSH terminal, an SFTP file manager, and a chat interface for a self-hosted Hermes Agent together in one app.

The app is designed for private networks such as Tailscale. Keep SSH and Hermes services on your private network instead of exposing them directly to the public internet.

## Features

- **SSH terminal** with an interactive terminal emulator and host key confirmation.
- **SFTP file manager** to browse folders, create folders and files, upload and download files, preview supported files, rename, and delete.
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

1. Install TailHarness on your Android device.
2. Connect the device and homeserver to the same private network.
3. Open **Settings** and enter the server's SSH host, port, username, and password.
4. Confirm the SSH host key when prompted. A changed host key is rejected until you investigate and resolve the change.
5. Optionally configure the Hermes base URL, model, and API key. Use HTTPS for public hosts; HTTP is accepted for local or private network hosts.
6. Open **Terminal**, **Files**, or **Hermes** to use the configured services.

TailHarness does not provide a hosted server or Hermes service. You operate and secure those services yourself.

## Build from source

The project uses Kotlin, Jetpack Compose, and Gradle. With Java 17 and Android SDK 36 installed, build a debug APK with:

```sh
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also builds and publishes a debug APK for pushes to `main`. Debug APKs are for testing and development, not Play Store releases.

## Security and privacy

- SSH passwords and Hermes API keys are encrypted locally with AES-GCM and a key held by Android Keystore.
- SSH host keys are checked and require confirmation before first use.
- Hermes HTTP requests are restricted to local or private-network hosts; use HTTPS for public hosts.
- Android cloud backup and device transfer exclude the app's shared preferences.
- Profile exports contain connection credentials. Protect them with a strong backup password and store the resulting backup securely.
- Connecting to a server and using its SSH or Hermes service sends data to that server. Review the server and network you configure.

## Third-party notices

Terminal emulator and renderer sources are based on Termux v0.118.0 and are included under `app/src/main/java/com/termux`. See the notices and license texts in `app/src/main/assets/licenses`. The upstream terminal emulator code carries an Apache-2.0 exception; other applicable Termux components are GPL-3.0-only. Each APK release links to the source commit used for that build.

## License

See the notices and license texts in `app/src/main/assets/licenses` for third-party components. Check the repository's license files for the terms applicable to TailHarness itself.
