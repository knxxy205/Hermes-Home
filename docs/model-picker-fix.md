# Model picker stability and navigation background

The device recording `Screen_Recording_20261108_162547.mp4` shows the entire picker moving vertically repeatedly: its title, search field, list, and close button move together while the list is at its lower edge. Disabling list overscroll in the previous APK did not resolve that behavior.

The previous implementation measured a `fillMaxHeight(0.92f)` content column inside a draggable `ModalBottomSheet`. Sheet anchor movement and content measurement could affect one another. The correction removes this geometry dependency altogether: the picker uses a full-height dialog with explicit safe-area and keyboard insets. Only the bounded lazy list scrolls. Close and Android Back dismiss it; it no longer has a sheet drag gesture.

The existing Hermes theme, typography, and 48 dp controls are retained (ENERGY 1 / RHYTHM 1 / MOTION 1 for this picker). A fixed header keeps search, reload, close, and the current model available. The selected model sorts first, model rows are at least 64 dp high, and the redundant `models/` prefix is removed from displayed names only. Original model IDs are preserved for selection/API requests. Search matches multiple words and has a clear action; loading, provider errors, empty catalogs, and no matches use the same bounded content area. Item keys remain the original distinct IDs. There are no scroll-offset reads that recompose the containing dialog.

The app previously reserved navigation height by shortening the entire page stack. This exposed a separate scaffold-colored band behind the floating capsule. Files and Settings now extend their background and lazy-list viewport behind the capsule, with bottom content padding to keep the final row/actions reachable. Terminal and chat retain their input clearance, but draw their own background across that space. Android's navigation-bar contrast scrim is disabled where supported. The capsule itself retains its existing appearance and four working tabs; no whole-screen blur is restored.

## Validation

Regression tests exercise the actual dialog with a mock catalog of 160 models in light and dark themes. They compare the window/header/search bounds after repeated gestures at both list limits, check selected-first ordering, search, clearing, empty search results, reload, selection using the original ID, and close. A pixel test checks that the navigation capsule's outside margin shows the underlying content color. Screenshots are saved under `app/build/outputs/model-picker-fix/screenshots/`.

The three new tests pass (`app/build/outputs/model-picker-fix/picker-host-fix.log`). Light and dark captures were inspected: a fixed header, a selected model with a contrasting background/radio state, readable rows, and a bounded scrolling region are present. The navigation pixel test confirms the outside margins match the underlying green test canvas. The bounds comparison permits one physical pixel for native window/inset rounding; the large vertical motion in the reported recording would fail it. The test host includes a full-size parent page, matching actual usage: a dialog-only zero-size host had stalled asynchronous updates in Robolectric and was corrected in the test setup.

Interaction evidence: repeated swipes at top/bottom preserve picker/header/search bounds; multi-word search selects the matching original ID; no-match feedback appears; clear restores the catalog; reload requests a fresh catalog; close removes the window. These checks run in both themes against a 160-model mock catalog. All **73 JVM tests pass**, with zero failures, errors, or skips (`full-tests.log`); Android-test APK assembly also passes. No Android device is connected; JVM gesture/layout tests do not establish physical-device frame timing.

## APK

`app/build/outputs/model-picker-fix/Hermes-Home-model-picker-fix.apk`, **15,614,898 bytes**, package `com.aistudio.homeserver.thns.preview`, version `1.3.1-smooth-preview` (code 5), minimum API 24. The APK is not debuggable; code and resource shrinking remain disabled. Final assembly succeeds (`build.log`). `apksigner verify` passes and the certificate matches the preceding preview, allowing installation as an update. All 99 captured source/build configuration hashes match the assembly inputs. DEX checks confirm the new picker controls, `ModelPickerContent`, and `/effort` are packaged. SHA-256: `5808322cb60351dc017c5ac623fe83b16594233f93351f3423e90b2690d8e14c`.

The exact results are in `validation-summary.json`, `apk-signature.txt`, `apk-badging.txt`, and `SHA256SUMS.txt` alongside the APK. Earlier APKs are preserved. Changes remain local and uncommitted; no push or release was performed.

## Delivery checks

- R-03/R-34: light and dark phone-size captures show bounded controls and readable model rows; the list retains bottom scroll space and fixed actions.
- R-25/R-39: the picker uses the existing tested theme foreground/background pairs and native text-field, icon-button, and selectable radio semantics. Search, reload, close, and clear have accessible names; rows are at least 64 dp tall.
- R-26/R-27: selection, search clear, reload, and close are exercised; loading, connection failure, empty catalog, and no-match branches retain real callbacks.
- R-19/R-31: the fixed dialog removes the moving sheet geometry. The app's existing palette and type are retained for continuity; selected-row color communicates state. No new assets or decorative animation are added.
- R-35/R-36 PASS within the local verification scope: 73 tests pass; light/dark gesture interaction and captures were inspected; final APK assembly and signature checks pass. Physical-device startup/flicker/FPS remain unverified here.
