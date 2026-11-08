# Liquid glass bottom navigation

The later [performance revision](performance.md) replaces the live Backdrop rendering with a lightweight translucent treatment and direct pointer tracking. The notes below describe the original glass implementation and its original validation.

The design follows the user's `Screen_Recording_20261108_090959.mp4`: a floating capsule, a separate glass selection capsule, direct horizontal dragging, and spring settling. ENERGY 1 / RHYTHM 1 / MOTION 2. Motion explains navigation and touch feedback; it does not run while idle. Only the bar and its indicator use glass effects.

## Backup

The verified snapshot before this implementation is `/Users/fadjri/orca/backups/Hermes-Home/20261108-092513-before-liquid-glass`. It includes a source archive with all tracked and untracked work, a Git bundle, the working-tree patch and index, a SHA-256 manifest, and the previous preview APKs. Generated directories are excluded. This includes the preceding unlock-screen layout work. Nothing has been committed or pushed. Extract into a separate directory to inspect before restoring any active files.

## Compatibility decision

Maven Central metadata on 8 November 2026 lists Backdrop **2.0.1** as the latest release. Its published POM uses Kotlin **2.4.10** and Compose Multiplatform **1.12.0**. That is beyond this project's Kotlin 2.2.10 compiler line.

Backdrop **1.0.2** is selected: its published POM uses Kotlin 2.2.21 metadata and AndroidX Compose 1.10.0. The next stable release, 1.0.3, moves to Kotlin 2.3.0. The existing Kotlin compiler and Compose plugin remain 2.2.10; the Compose BOM moves from 2024.09.00 to **2025.12.00**, which aligns UI/foundation at 1.10.0 and Material 3 at 1.4.0. Backdrop's library minimum is API 21, while the app minimum remains **24**. The library's public effect entry points are regular functions, and its JVM 21 bytecode is consumed by the existing AGP/D8 toolchain rather than changing the app's Java target.

Sources: [Maven version metadata](https://repo1.maven.org/maven2/io/github/kyant0/backdrop/maven-metadata.xml), [2.0.1 POM](https://repo1.maven.org/maven2/io/github/kyant0/backdrop/2.0.1/backdrop-2.0.1.pom), [1.0.2 POM](https://repo1.maven.org/maven2/io/github/kyant0/backdrop/1.0.2/backdrop-1.0.2.pom), [1.0.2 Android configuration](https://github.com/Kyant0/AndroidLiquidGlass/blob/1.0.2/backdrop/build.gradle.kts), [Compose BOM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.12.00/compose-bom-2025.12.00.pom).

JVM tests need a Java 21 launcher to load Backdrop classes. Test tasks declare that toolchain explicitly; the existing Foojay resolver can provision it when unavailable. Android assembly still works with the existing JDK 17 build environment and Java 11 app target. The first assembly succeeded, while the first JVM run correctly exposed the missing Java 21 test runtime; this is recorded in the raw build log.

## Implementation boundaries

- `LiquidGlassBottomNav.kt` owns pointer gestures, spring positioning, press feedback, icon crossfade/bounce, animated colors, tab semantics, keyboard focus and haptics.
- `NavigationMotion.kt` observes Android's animator-duration setting and provides page presentation and gesture hit testing. A zero duration scale makes transitions instant.
- `MainActivity.kt` keeps its existing `selectedTab`, callbacks, screen ownership, ViewModels, Back handling and transfer destinations. There is no NavHost in this app. A backdrop records the page content once; the navigation bar is outside that recording, avoiding feedback into its own blur.
- Pages stay composed through a 280 ms fade and a short directional slide. After the transition, hidden pages are not measured or placed. A hidden page contributes no accessibility actions, and the selected page is above the outgoing page.
- Existing measured bottom clearance and keyboard behavior are retained. The floating bar respects navigation-bar insets, keeps 16 dp side margins and 8 dp vertical margins, and caps its width on larger windows.

Full blur/refraction is used only on API 33+ with hardware acceleration. API 24–32 and software rendering use a translucent bar and solid indicator, with the same gestures and selection logic. The bar uses a dark palette over Terminal or when the selected app appearance is dark. The later [dark-mode change](dark-mode.md) adds a shared appearance preference for the rest of the app. The original four tabs and their localized labels remain.

Gesture targets are read by one animation collector. Frame-dependent position, velocity, scale, icon opacity and glass strength are read in graphics/effect blocks. Dragging does not update `selectedTab` or recompose the page tree. Selection commits on release to the nearest tab; cancelled gestures return to the selected tab. Haptics occur only for an explicit selection of a different tab.

The tint puts a lower bound on contrast over varied page content. Computed sRGB contrast at the darkest light-theme background is 5.17:1 for inactive text and 4.73:1 for active text; over the brightest dark-theme background it is 8.46:1 and 5.07:1 respectively. Tab icons are paired with labels and selection semantics. Keyboard focus has an explicit accent border. The decorative glass edge is not used as the control's sole affordance.

## Verification

Build and test evidence is written to `app/build/outputs/liquid-glass/`. New regression coverage exercises hit testing, bounds and RTL mapping, tap selection, committing a drag only on release, dragging in both directions, and the dark fallback with animations disabled. Existing JVM and Android-test compilation checks are retained. Hardware shader appearance and device frame timing require an Android device; no FPS, battery or universal smoothness claim is made.

Final result: `final-build.log` records successful debug APK assembly, Android-test APK assembly and all **58 JVM tests**, with zero failures, errors or skipped tests. This includes all four navigation regressions. `light-fallback.png` and `dark-fallback.png` were rendered with Robolectric native graphics and inspected. The API 24 drag regression runs with legacy graphics. These screenshots show the compatibility treatment, not an AGSL rendering from a device. A test-only classloader rule keeps MINA's filesystem provider and security registrars in the same loader when native font rendering initializes Java's filesystem providers.

`Hermes-Home-liquid-glass.apk` is signature-verified, and its certificate matches the previous preview APK saved in the backup. `SHA256SUMS.txt` and `validation-summary.json` identify the exact artifact. The existing preview APK paths were not overwritten with this experimental navigation build.

## Scoped delivery checks

- R-02 / R-17 / R-18 / R-23 / R-36 / R-38 PASS: labels use the existing localized tab names; no new product assets, identities, statistics or performance measurements were invented. The 58-test count comes from the result XML.
- R-03 PASS for the component: 320 dp and phone-width layouts are exercised, labels fit the inspected screenshots, the bar has system navigation insets, and existing measured content clearance is preserved. Minimum tab height is 56 dp; larger font scale adds bar height.
- R-24 / R-26 / R-35 PASS for tested navigation: Files tap changes selection; a held drag moves the indicator without changing pages; release selects Settings; reverse drag selects Terminal; cancellation retains Terminal; the same sliding selection works on API 24. Builds and native graphics captures completed successfully.
- R-25 PASS: foreground contrast is computed against the composited tint limits as recorded above. The active pill and filled icon identify selection in addition to color.
- R-27 / R-28 / R-34 PASS by scoped inspection: navigation adds no fetched data or FAQ; existing screen states are preserved. Light and dark bar treatments are separately exercised without adding an app-wide theme toggle.
- R-32 / R-39 PASS by source inspection: Compose selectable tab roles, full text labels, selected state and keyboard focus handling are retained. A visible focus border is included. Physical keyboard traversal and physical haptics were not measured.
- R-33 / R-37 / R-40 PASS: implementation lives directly in Kotlin source, follows the supplied video, uses no new web media or scripts, and reads frame-dependent gesture state in graphics/effect blocks.
- Purpose, liveliness and quality checks PASS: glass is limited to the two surfaces requested by the user; the shadow separates the bar from page content; the indicator, crossfade and bounce explain selection and touch. The existing four destinations, palette and type remain, and all movement stops when idle or when system animations are disabled.

A byte comparison against the pre-change archive confirms that existing SSH, transfer, ViewModel and individual screen implementation files are unchanged. No physical device or emulator is connected in this environment, so AGSL rendering, physical haptics and device frame timing remain for on-device testing. No lint pass is claimed.
