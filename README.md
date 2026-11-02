# Homeserver Remote

> Kelola homeserver kamu dari mana aja — terminal, file, dan AI agent, semua dalam satu genggaman.

Aplikasi Android native berbasis Kotlin dan Jetpack Compose untuk mengelola homeserver pribadi melalui jaringan privat Tailscale, tanpa membuka port ke internet publik.

## Fitur
- 🖥️ **Terminal** — terminal SSH interaktif, emulator ANSI/VT xterm, input langsung dari keyboard, dan tombol shortcut untuk ponsel
- 📁 **Files** — file manager via SFTP: browse, upload, download, rename, delete
- 🤖 **Hermes Chat** — chat langsung ke AI agent sendiri (Hermes Agent, OpenAI-compatible API)
- 🔒 **Aman** — semua kredensial disimpan encrypted di Keystore device, tidak ada data yang dikirim ke pihak ketiga

Terminal emulator and renderer sources are based on Termux v0.118.0 and are
included under `app/src/main/java/com/termux`. See the notices and license texts
in `app/src/main/assets/licenses`. The Android Terminal Emulator components
carry the Apache-2.0 exception documented by upstream; other applicable Termux
components are GPL-3.0-only. Each APK release includes a link to its exact source
commit archive.
