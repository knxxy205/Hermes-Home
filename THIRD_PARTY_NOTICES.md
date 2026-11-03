# Third-party notices

Hermes Home's original code is licensed under GPL-3.0-only; see [LICENSE](LICENSE). The following components retain their own copyright notices, licenses, and applicable exceptions.

| Component | License / notice |
| --- | --- |
| Termux terminal emulator/renderer, v0.118.0 | GPL-3.0-only, with the upstream Apache-2.0 exceptions. See [the bundled notice](app/src/main/assets/licenses/termux-NOTICE.md) and [upstream declaration](app/src/main/assets/licenses/termux-upstream.md). |
| AndroidX and Jetpack Compose | Apache-2.0; retain component copyright notices. |
| Kotlin and kotlinx.coroutines | Apache-2.0. |
| OkHttp | Apache-2.0. |
| JSch, mwiede fork, 0.2.20 | BSD-3-Clause with JZlib/jBCrypt notices; see [the bundled notice](app/src/main/assets/licenses/jsch-NOTICE.txt). |
| Plus Jakarta Sans and JetBrains Mono | SIL Open Font License 1.1; see the font notices under `app/src/main/assets/licenses`. |

The repository includes full Apache-2.0 and GPL-3.0 license texts, font license notices, and Termux attribution under `app/src/main/assets/licenses`. Tests additionally use JUnit, Robolectric, Roborazzi, and Apache MINA SSHD; these test libraries are not shipped as application runtime dependencies.

The [license decision](docs/license-review.md) records the maintainer's approval. Every distributed APK must provide access to its matching source and build instructions.
