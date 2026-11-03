# Hermes Home privacy policy

Effective for the 1.3 beta release. Last updated: 3 November 2026.

Hermes Home is an Android client for servers you configure. It is maintained by [knxxy205](https://github.com/knxxy205). You do not need a Hermes Home account, and the project does not operate a hosted SSH or AI service.

## Information used by the app

| Information | Purpose and destination |
| --- | --- |
| SSH host, port, username, and password | Connect directly to your chosen SSH/SFTP server. |
| Hermes endpoint, model, and API key | Send requests to the AI endpoint you configure. |
| Chat messages, selected file contents, and command results you send to Hermes | Provide conversation context to that endpoint. Recent conversation messages and SSH host/username context may accompany requests. |
| Remote filenames, paths, permissions, sizes, and modification times | Browse, search, preview, transfer, and manage your server files. |
| Files selected through Android's picker | Upload those files to the selected server folder. Downloads are written to the destination provider you choose. |
| Saved connection profiles, chat sessions, terminal snippets, and transfer metadata | Restore settings, conversations, and queued work on your device. |
| Network availability and Wi-Fi/VPN status | Schedule transfers and check whether the configured server can be reached. |

The app does not include advertising, analytics, a crash-reporting backend, or the Firebase/Gemini SDKs in this release. It does not upload usage statistics to the maintainer. Your Android system, network/DNS provider, GitHub, and any server or storage provider you choose have their own practices.

## Local storage and device authentication

Connection credentials, chat session contents, and saved terminal snippets are encrypted with AES-GCM using an Android Keystore key. Hardware protection depends on the device. Some metadata, such as profile labels, ports, session identifiers, queued file URIs and paths, trusted SSH public keys, and health-check counters, is stored in the app's private storage without separate application encryption.

Android handles biometric or device-credential authentication. Hermes Home receives the authentication result; it does not receive your fingerprint, face template, or device PIN. App lock controls access to the interface. Scheduled background work can continue while the interface is locked.

Temporary file previews may use private cache storage. App credentials and preferences are excluded from the app's configured Android backup/transfer rules.

## Connections and background work

SSH and SFTP traffic use SSH encryption and server host-key checks. Hermes uses the HTTP(S) endpoint you configure. Public endpoints require HTTPS. Local/private HTTP remains available for self-hosted services and sends data without TLS on that network; use a trusted private network or HTTPS. The app checks HTTP destinations again after redirects.

Release builds trust Android's system certificate authorities. User-installed certificates are accepted only by development/debug builds. Use a server certificate trusted by Android for HTTPS in a release build.

Background uploads contact the selected server. An hourly task may also attempt a TCP connection to the active SSH server to check reachability. Android may delay these tasks. Notification previews can show server addresses, filenames, and transfer status, depending on your device's notification settings.

Your SSH server, Hermes endpoint, or any upstream AI service configured behind that endpoint may log or retain the data it receives. You are responsible for reviewing those services. Hermes Home cannot delete their records.

## Exports, sharing, and downloads

Profile exports contain connection details and credentials inside a password-protected backup. You choose whether to save or share it through Android. Keep its password and copies secure. Legacy unencrypted backup formats can still be imported; do not share those files publicly.

Files saved to a selected local, removable, or cloud storage provider leave the app's private storage. That provider controls access and retention. Exported backups and downloaded files remain in their chosen locations when you uninstall the app.

## Retention and removal

Saved app data remains on the device until you remove it through the app where supported, clear app storage in Android Settings, or uninstall Hermes Home. Clearing app storage removes the app's local settings and saved sessions; keep a secure export if you need to restore them. It does not remove files on your server, exported copies, downloaded files, or data already sent to another service.

Do not include credentials, server addresses, personal file contents, or unredacted logs when reporting a problem. The app does not automatically send diagnostic reports.

## Contact and changes

For a general privacy question, use the [repository issue tracker](https://github.com/knxxy205/Hermes-Home/issues) without personal information. For a vulnerability or private-data concern, follow [SECURITY.md](SECURITY.md). Access to the repository depends on its visibility and your GitHub permissions.

Changes to data handling will be described in this policy and the changelog. Revisit this policy when updating the app.
