# Consistent app appearance and manual unlock

The later [performance revision](performance.md) keeps these theme and unlock behaviors while reducing navigation rendering, smoothing terminal scrolling/keyboard layout, and adding a smaller preview build.

The user requested a consistent dark mode, a lock screen that waits for an explicit button tap before biometric authentication, and a more compact language selector. The existing white/blue identity, type, screen layouts, terminal palette and liquid navigation gestures are retained. ENERGY 1 / RHYTHM 1 / MOTION 2; this change adds no decorative motion.

## Appearance

Settings → App preferences now groups two compact menus: **Tema** (Ikuti perangkat / Terang / Gelap) and **Bahasa** (Ikuti perangkat / Indonesia / English). Selected entries have a check mark and selected semantics. The choice is stored under `theme_mode` in the existing preferences store, defaults to `system`, and applies without recreating screen content. Invalid stored values normalize to `system`.

`Theme.kt` owns paired Material 3 color schemes. The shared color names in `Color.kt` resolve semantic roles from the active scheme instead of retaining light-only values. Fixed white surfaces and dark text were replaced in Files, Settings, transfer sheets, the model picker, chat input, user bubbles, Markdown tables, code blocks and actions. Code highlighting caches include the active appearance, so syntax colors refresh when the theme changes. Foreground/background and primary/on-primary pairs are kept together.

The terminal and image viewer retain their purpose-built dark canvases in both modes; output colors and images are not inverted. Their surrounding app dialogs and sheets follow the chosen appearance. The glass navigation uses the same appearance preference rather than consulting the system independently, while Terminal retains its dark navigation treatment. Status/navigation bar icon styles follow the visible surface. Android 7 receives a dark system-navigation scrim because it cannot display dark navigation icons.

The native Activity theme uses AppCompat DayNight. Its local night mode follows the preference; the activity handles `uiMode` configuration updates so a theme switch does not recreate the UI, discard a draft, or restart SSH ownership. No new runtime library was added.

## Unlock flow

The automatic biometric effect on app launch was removed. The screen remains locked until the user presses **Buka Hermes Home** (Open Hermes Home). That existing callback invokes the existing fingerprint/PIN helper. Authentication errors or cancellation leave the screen locked, and the user can press the button again. Existing relock timing and SSH session ownership are unchanged.

## Checkpoint and verification

The source and working liquid-navigation APK were preserved before this change in `/Users/fadjri/orca/backups/Hermes-Home/20261108-100806-before-dark-mode`; its source archive was checked against the workspace and has a SHA-256 manifest. The earlier pre-liquid-glass backup remains separate.

A byte comparison against that checkpoint confirms that all 24 SSH, SFTP, security, worker and transfer source files are unchanged by the appearance work. The comparison record is `preserved-services.json` in the output directory.

Build logs, screenshots and validation metadata are stored in `app/build/outputs/dark-mode/`. Appearance regression coverage includes persisted theme selection through the menu, switching back to Light and System, compact language selection, light/dark palette and code-token contrast, preserving a chat draft, Files appearance, transfer-sheet dismissal, and the renamed unlock button waiting for a tap. The test fixtures use an empty saved profile and initialize WorkManager explicitly; Robolectric does not supply a device's AndroidKeyStore.

Screenshots are JVM renders using Robolectric native graphics. A physical biometric dialog, hardware AGSL appearance, system bar appearance and physical-device frame timing are not claimed as measured. Lint is not reported as passing.

Final validation on 8 November 2026: debug APK assembly, Android-test APK assembly and all **63 JVM tests** passed, with zero failures, errors or skipped tests (`final-build.log`, successful in 1m 46s). This includes the five appearance regressions and four liquid-navigation regressions. Android instrumentation was compiled but not run for this change because no device or emulator is connected.

The deliverable is `Hermes-Home-dark-mode.apk` (23,909,713 bytes), package `com.aistudio.homeserver.thns.preview`, version `1.3.1-chat-preview`, minimum Android API 24. `apksigner verify` passes and the signing certificate matches the preceding liquid-navigation preview. SHA-256: `dbfc76c908816047a6b294c6643eb4761a067ceeb3979c69b1e4b3cf3e1a8a47`. `SHA256SUMS.txt`, `apk-signature.txt` and `validation-summary.json` identify the artifact. Changes remain local and uncommitted; no push or release was performed.

## Scoped delivery checks

These checks cover the appearance, preference menus and manual-unlock changes. Device-only behavior remains subject to the limits above.

- R-02 / R-17 / R-18 / R-23 / R-36 / R-38 PASS: new labels contain no em dashes, invented statistics or testimonials; existing product art and four navigation destinations are reused.
- R-03 PASS by render and source inspection: the inspected phone renders for Settings, chat, Files, transfer sheet and lock screen have no clipped labels or horizontal overflow. Preference rows are at least 64 dp, dropdown items use Material defaults, and the unlock button is at least 56 dp. Existing lock-screen scrolling and safe insets remain.
- R-24 / R-26 / R-35 PASS for exercised controls: Tema opens its menu; Gelap changes the rendered background and persists; Terang restores the light background; Ikuti perangkat stores system mode. Bahasa opens its menu; English changes the labels and persists. Transfer history opens and its close button dismisses it. Buka Hermes Home invokes the unlock callback once after a tap and never during the initial wait. These actions are asserted in `AppearanceTest`.
- R-25 PASS for tested color pairs: `themeResolutionAndSemanticContrasts` checks body, secondary, primary, primary-container and error text pairs at 4.5:1 or better in both schemes, plus highlighted code tokens on both code canvases. Navigation tint contrast is recorded in `liquid-glass-navigation.md`.
- R-27 / R-28 PASS by source inspection: Files empty/loading/error branches and chat error presentation use theme roles; no FAQ or fetched-data behavior was added.
- R-32 / R-39 PASS by source inspection for changed controls: native Compose buttons and menu items provide keyboard activation, focus and dismissal behavior. Text labels name the controls, selection has both a check mark and selected semantics, and decorative icons have no duplicate spoken label. Physical keyboard traversal is not claimed as tested.
- R-33 / R-37 / R-40 PASS: changes are written directly in Kotlin/resources using the existing Hermes Home design direction. No web media, scripts or invented performance measurements were added.
- R-34 PASS for exercised views: light/dark Settings, chat, Files and lock renders were inspected, together with the dark transfer sheet. Theme switching preserves the unsent chat draft in the regression test.
- Purpose checks PASS (R-01 / R-04 / R-06 / R-07 / R-08 / R-09 / R-10 / R-12 / R-13 / R-14 / R-19 / R-22): blue preserves the existing action identity; darker surfaces and brighter content preserve hierarchy at night; the existing Jakarta Sans and monospace code/terminal fonts preserve reading roles. The check and expansion icons explain selection and menu behavior. Glass remains confined to the existing navigation bar and indicator. No new illustration, glow, decorative pattern or animation was added.
- Liveliness PASS: ENERGY 1 / RHYTHM 1 / MOTION 2 retains the app's calm, predictable layout and existing navigation feedback. The unlock action is the lock screen's focal point; selected values lead the compact preference rows.
- Craftsmanship and quality locks PASS (C-1 through C-5, R-05 / R-11 / R-15 / R-16 / R-20 / R-21 / R-29 / R-30 / R-31): grouping Tema and Bahasa reduces vertical space while preserving a visible current value; existing card radii and spacing stay consistent; the explicit unlock label describes its action. Paired themes use the existing blue accent and neutral surfaces, with semantic status colors. No new page template or external product identity was introduced. Evidence and hardware limits are recorded separately.
