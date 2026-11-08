# Daily-use revision

The user asked for persistent SSH while the app remains in recents, matching headers across Hermes/Files/Settings, code-only copy controls, Indonesian/English language selection, actionable upload notifications with file-level history, and a review of Files usability. Keep the established white/blue application, dark terminal, and existing Fira Code/provider work. Deliver a local preview; no GitHub commit, push, or release.

## Direction

ENERGY 1 / RHYTHM 1 / MOTION 1. Shared header measurements align the screens the user compared: 16 dp horizontal padding, 12 dp vertical padding, 44 dp icon badge, 12 dp icon-to-title gap, 22 sp title, and 48 dp title area. Hermes retains its own icon and add/history/clear actions. Files uses a history action and a menu to leave room for the title; upload stays in the lower primary action.

Folder rows replace the two-column colored tiles so long names have usable width on phones. Breadcrumbs navigate to parents; the edit-path action allows a direct path. Sort and hidden-file preferences are saved. New controls use real operations and the existing palette; no new artwork, decorative animation, or runtime libraries are introduced.

The user-provided chat screenshot shows a single-line message crowded inside the bubble. User bubbles now have 20 dp horizontal padding and 16 dp vertical padding, with 12 dp between the sender/time line and message text. Message separation is 28 dp. This keeps the short message comfortably inset while retaining a readable conversation rhythm on a phone.

## Behavior

- SSH sessions move out of the composable scope into an application owner kept active by a user-visible foreground service. Locking the vault or recreating the activity does not destroy them. The service ends when sessions disconnect or the app task is removed.
- JSch keep-alive must retain its socket timeout: `setServerAliveInterval` sets the timeout internally. The old subsequent `timeout = 0` prevented the timer-driven keep-alive from operating. Retry applies to transport failures with bounded delays, not authentication/host-key failures, manual disconnect, or normal shell exit. Pending input from an old connection is not replayed.
- The SSH foreground service declares its interactive-terminal use under `specialUse`, independently of the existing `dataSync` upload worker. A bounded, renewed partial wake lock is held only while the service runs. Network, server, force-stop, and Android power restrictions can still interrupt transport.
- Upload history stores each source URI, display name, destination, status, byte count, and error in an encrypted journal. Successful entries are retained across retry; unfinished entries are retried as a new batch. Missing URI access offers source reselection. Picking files snapshots the server profile and destination and asks before replacing destination names.
- Notification taps carry a batch identifier directly to the activity. Navigation waits for normal app authentication, opens Files, and displays the correct batch. Opening its folder selects the original profile. Each batch has its own result notification; progress and results use separate notification channels.
- Language uses Android/AppCompat per-app locales with Indonesian, English, and system choices. New text uses translated resources; legacy UI labels are migrated through explicit bilingual text pairs. Filenames, code, server output, and existing conversation content are not translated.

## Verification

Implementation and verification are in progress. Record final JVM/native results, real SSH/SFTP fixture behavior, notification tap/cancel/retry behavior, language switching, mobile screenshots, lint, and the local APK checksum before delivery.
