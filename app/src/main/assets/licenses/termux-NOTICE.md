Terminal emulator and canvas renderer
====================================

Source: https://github.com/termux/termux-app
Version: v0.118.0, commit 6e2689f55295fa444be8ac8592c527c2c5ef3253

The Java files under com/termux/terminal and com/termux/view are unmodified
upstream sources. They provide terminal emulation and rendering. The local
process/JNI session is not instantiated; SSH transport is supplied by this app.

Copyright the Termux contributors and the Android Terminal Emulator contributors.
Upstream license declaration is included in termux-upstream.md. The repository
is GPL-3.0-only, with Apache-2.0 exceptions for Android Terminal Emulator code.
Full license texts are included alongside this notice.

Corresponding app source and build instructions:
https://github.com/knxxy205/TailHarness
Each APK release links to its matching source commit/archive.
