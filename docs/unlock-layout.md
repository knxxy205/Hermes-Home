# Unlock screen layout

Direction: the user's supplied phone screenshot, with a light background, centered brand block, stored server address and a blue unlock action near the bottom. ENERGY 1 / RHYTHM 1 / MOTION 1. The user explicitly limited this change to layout.

`VaultLockScreen` reuses the existing logo drawable, palette and Material button. The logo sits above a single centered Hermes Home title. The subtitle and stored server address sit below it, while flexible space separates the main block from the unlock action and private-network caption. The rounded address container groups the configured destination; it does not claim the server is online. No network check or new asset was added.

The existing `onUnlockRequested` callback is retained. The displayed host still comes from the saved configuration, and the existing fallback appears when a server has not been configured. No example IP is hard-coded. Safe drawing insets keep the button clear of system controls, and vertical scrolling keeps content reachable in short windows and at larger font sizes. The layout uses no added animation or background task. The later request on 8 November changes the label to **Buka Hermes Home** and removes automatic authentication on launch: the fingerprint/PIN prompt now starts only after this button is pressed. See [dark-mode.md](dark-mode.md).

Source checks:

- PASS: the Material button invokes the existing unlock callback and retains `unlock_vault_button`.
- PASS: the host still comes from `config.sshHost`; no invented connection state or user data is shown.
- PASS: the layout has safe-area padding, a scroll fallback, wrapping labels and a minimum 56 dp action height.
- PASS: measured contrast is 4.51:1 for supporting text, 13.88:1 for the heading and 14.63:1 for the address. The large blue title measures 3.49:1; the large bold white button label measures 3.68:1.
- PASS: existing Material controls retain native click/focus semantics; the lock icon is decorative beside the button label. No new focus handling was introduced.
- PASS: changes were made directly in Kotlin source; no new assets, gradients, shadows, metrics or performance claims were introduced.

Runtime verification is limited by the current environment: no Android emulator is installed or connected. The fresh debug build now passes and this layout is included in `app/build/outputs/liquid-glass/Hermes-Home-liquid-glass.apk`; the earlier floating-navigation APK does not contain it. Physical keyboard operation, large-font rendering and unlock-screen captures have not been re-tested for this change.
