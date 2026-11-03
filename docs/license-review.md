# License decision for public distribution

The project currently bundles Termux v0.118.0 terminal sources. Their upstream notice identifies GPL-3.0-only, with Apache-2.0 exceptions for applicable Android Terminal Emulator portions. Fonts have their own SIL Open Font License notices.

On 3 November 2026, the maintainer approved **GPL-3.0-only** for Hermes Home's original code and distribution of the combined app. The [root LICENSE](../LICENSE) applies that decision and includes the full GPL version 3 text. It does not grant an option to use a later GPL version.

Existing third-party copyright notices, Apache/SIL/BSD licenses, and applicable upstream exceptions remain intact. The app must not be described as MIT/Apache-only while GPL-covered components remain bundled.

Existing notices and full license texts are in `app/src/main/assets/licenses`. The release workflow must keep its matching source archive and build instructions available to APK recipients. Public APK downloads require equivalent source access; a private repository link alone is insufficient for public distribution. Signing keys, passwords, and server configuration must not be published.
