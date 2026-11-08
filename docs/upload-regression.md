# Upload regression

The Android baseline test reproduced cross-batch input corruption on 7 November 2026. `APPEND_OR_REPLACE` made the next upload depend on the previous worker. WorkManager's input merger then supplied the previous worker's `batch_id` and result fields to the next worker. A 14-byte upload was marked successful with the first upload's batch ID and 786,432 completed bytes; its own journal stayed queued and the file was skipped.

Each upload now has its own independent WorkRequest. A process mutex keeps actual SFTP transfers sequential, and the worker resolves its identity from its unique tag and its own journal. Cancellation no longer propagates into a dependent upload. Existing requests with merged legacy inputs can recover their original identity from their tags.

Only the transfer observer posts completion notifications after WorkManager reaches a terminal state. Starting a transfer clears old transfer results; SSH notifications keep their separate channel. Completion uses a single notification ID. Progress updates are limited to twice per second, with immediate updates at file boundaries.

Files observes completion transitions for every batch rather than the currently selected work item. It refreshes the matching profile and folder without navigating. Notification permission is requested after the document picker returns, so two external activities are no longer launched together.

The detail panel uses the existing white/blue palette and platform typography. A content-sized sheet, a single destination button and plain file rows keep the filename, progress and action visible without repeating folder buttons or large cards. ENERGY 1 / RHYTHM 1 / MOTION 1.

Baseline reproduction: `app/build/outputs/upload-regression/baseline-instrumentation.log`. Final validation results are recorded in this directory after the regression suite runs.

The first fixed Android run passed both transfer regressions (`instrumentation.log`): two folders and byte comparison, failure followed by another upload and retry, cancellation from the notification action without cancelling the next batch, and a single completion notification after the queue finishes. The picker test uses an actual MediaStore JPEG and Android DocumentsUI; its final result is recorded separately in `photo-picker-ui.log`.

The expanded native run passed four data/notification tests: legacy merged inputs resolve to the batch's own tag and destination; notification cancellation preserves the following upload; consecutive uploads and retries retain their own byte counts and folders; clearing completed history preserves a source referenced by an active retry. The JVM run passed 54 tests, including a real SFTP upload with a Unicode/wildcard filename, an empty file, and selection of the actual uploading batch in the current folder.

Verification boundary: these results use an API 34 Android emulator and a disposable local SFTP server. They do not claim every device, Android release, or server permission configuration has been exercised. The previously reported lint toolchain crash has not been represented as a passing lint run.

Final result: all four transfer regressions and the document-picker UI scenario passed. The UI scenario chooses a real JPEG from Android DocumentsUI, confirms upload, changes the visible folder, checks the original destination, downloads and compares the bytes, scrolls to the uploaded file, opens its transfer detail and dismisses it. The resulting detail screenshot was inspected at 720 × 1280 and shows the compact white panel. `validation-summary.json` records the five passing cases and their individual source logs; earlier failed attempts are retained in the raw logs rather than presented as successful runs. The packaged APK is signature-verified and its checksum is stored in `SHA256SUMS.txt`.

## UI delivery checks

This review covers the edited transfer sheet and notifications. Other application screens retain their existing design.

- R-02 PASS: added UI strings use short sentences and no em dashes.
- R-03 PASS: the sheet wraps its content, caps its scrolling list, and ellipsizes long names; phone layout is captured in `upload-compact-details.png`.
- R-17 / R-18 / R-23 / R-38 PASS: counters come from the journal, filenames come from the provider, and no product assets, identities or claims were invented.
- R-24 / R-26 PASS: upload confirmation starts work, notification cancellation cancels only its request, history selects a batch, and close dismisses the sheet. Folder/retry callbacks were inspected and their underlying destination/retry operations exercised by the transfer tests.
- R-25 PASS: transfer text uses the established dark ink and muted ink on white; measured white-background contrast is 14.63:1 and 4.76:1 respectively. Blue actions measure 6.70:1 and error text 6.47:1.
- R-27 PASS: empty history, waiting/uploading, completion, cancellation, failed files and missing batches have explicit branches.
- R-28 PASS: no FAQ was added.
- R-32 / R-39 PASS by source inspection: Material buttons and clickable Compose rows supply focus and click semantics; the close icon has an accessible name. Physical-keyboard traversal was not measured.
- R-33 PASS: implementation changes were made directly in source with patches.
- R-34 PASS by source inspection: the existing fixed light theme and separate terminal theme were preserved; no theme toggle was introduced.
- R-35 PASS for the scoped native flow: Android document selection, upload confirmation, navigation while a transfer is queued, source/server byte comparison, history selection, detail rendering and dismissal are exercised by the picker test. Notification cancellation is exercised by the regression test. Other callbacks are explicitly identified as inspected above.
- R-36 PASS: performance and universal bug-free claims are not made; measured test results and their platform are recorded.
- R-37 PASS: direction is the user's request for a simple file-manager panel, with the existing white/blue palette and ENERGY 1 / RHYTHM 1 / MOTION 1.
- R-40 PASS for native scope: no web media or web performance claims were introduced; progress publication is throttled rather than running an idle animation.
- Purpose and quality checks PASS: repeated destination controls and nested cards were removed; the folder and close icons identify actions, plain rows make file outcomes easy to scan, and notifications have a single current owner. Existing type and spacing are reused without artwork, gradients, glow or decorative motion.
