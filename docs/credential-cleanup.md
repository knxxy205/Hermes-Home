# Credential cleanup verification

Review date: 4 November 2026. Repository visibility remains private. This record describes checks performed and unresolved dependencies; it is not a certification that historical credentials are invalid.

## Repository verification

An independent mirror was freshly cloned from GitHub at `main` commit `6e13340f8dd2f0c227d9fcffd35d837f96a87ca0`, including the three retained release tags.

- All 44 reachable commits passed Gitleaks with its default rules and redacted output. The current production source also passed a separate Gitleaks directory scan.
- After audit commit `9ecbb1059074cbdf2819fab4f2f4fb211e939926` was pushed, the independent mirror was fetched again. All 45 reachable commits passed Gitleaks and the mirror integrity check passed.
- Git object integrity checks passed. The fresh mirror contained 847 reachable objects and no unreachable objects.
- The known original commit was absent from both that mirror and the local shared Git object database. Local integrity checks reported no unreachable or dangling objects.
- GitHub listed zero forks, zero pull requests, and no pull-request refs. The reachable history contained no Git LFS pointer files.
- All five retained workflow runs were built from the sanitized history. Current build caches and artifacts are distinct from GitHub's cached Git objects; deleting an Actions cache does not purge a cached commit.
- All three retained release APKs were freshly downloaded and matched their recorded SHA-256 checksums. No archive member contained any of the three independently recovered old literals in UTF-8 or UTF-16LE form. This was a targeted literal check, not a guarantee against unknown secrets or encodings.
- The production credential-literal guard passed. It now rejects nonempty credential literals in remembered Compose state as well as preset assignments and embedded server-environment examples. All six guard tests passed.
- GitHub's secret-scanning API reported that scanning is disabled for this private repository. An unavailable scan was not counted as a clean scan.

The previous cleanup recorded removal of four distinct credential/example values. A separate review of the accessible old commit and its 28-commit ancestry independently recovered literals in three categories: SSH password, Hermes/API key, and a Tailscale auth-key example. The values are deliberately omitted from this document and tool output. Checks against known values supplement the general scanner; neither method proves that every possible secret format has been detected.

## GitHub cached history

Before Support's purge, the original commit responded through GitHub's Git commit API despite being absent from all advertised branches and tags. A metadata comparison matched the 28 old commits to rewritten commits by unique author timestamp and commit message, identifying the original root as the earliest rewritten commit. This reconstruction is not the original `git-filter-repo` report.

GitHub documents server garbage collection and cached-view removal as a Support operation. There is no repository-owner Git command or public purge API that completes that step. On 3 November 2026, the maintainer-approved private request was submitted through GitHub Support using the subject "Request review and purge of cached sensitive history — knxxy205/Hermes-Home" and issue type "General question or feature request". The report contains the original and rewritten commit identifiers, affected paths, verification results, and the explicitly unknown credential-revocation status; it omits credential values.

The portal displayed "Thanks for getting in touch with us! Your message has been successfully submitted." The maintainer subsequently supplied the matching Support ticket title and confirmed case **#4945452** in the Repositories category. On 4 November 2026, the maintainer shared Support's reply from Finch: "I've cleared out unreferenced commits, and the commits should now return a 404 error." Support also recommended changing leaked credentials. The ticket's current open/closed state has not been rechecked. Follow up on the existing case through the [private Support ticket portal](https://help.github.com/support/tickets) if needed.

Independent authenticated checks on 4 November 2026 confirmed the following:

- The Git commit API returned **404** for original commit `32c74b5e89ad5e1195d0d9012f6898e275ff2a01`, original root `cc94ffeadcd3aa661ff2ef0c25597f34164dc76a`, and the two previously inspected historical commits `a74621473025fdaceb3ab958b315b2b703d434d1` and `6159ac5dfe23c5c1beaa194e235606a58afd620f`.
- The REST commit endpoint returned **422**, with "No commit found for SHA", for each of those four identifiers.
- The Contents API returned **404** for `app/src/main/java/com/example/ui/settings/SettingsScreen.kt` at the original commit.
- The same authenticated Git commit API returned **200** for reachable `main` commit `98921068c204aea2fe7d340ae6f8fa2279f5091f`, confirming that the old-object failures were not caused by missing repository access.
- Repository visibility remained private. The advertised `main` and all three retained release tags still pointed to the expected sanitized commits.

Support has confirmed the purge, and these checks corroborate removal of the known cached commits. This verification covers the four known identifiers and one historical file; it does not enumerate every old blob or erase copies previously downloaded by others. Credential status is recorded separately below.

Reference: [Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository#fully-removing-the-data-from-github).

## Live credentials

This coding session has no verified server-administration connection and did not perform server changes directly. On 4 November 2026, the maintainer supplied these updates:

- **SSH:** the maintainer reports that the current password differs from the historical password following a server reinstall after disk failure. No further SSH password change was performed in this session. Password reuse on other accounts or systems was not checked.
- **Hermes gateway on port 8642:** the maintainer shared the homeserver agent's report of rotating `API_SERVER_KEY`. The agent reports that the pre-rotation key is rejected with **401**, the new key and the active `.env` key succeed with **200**, port 8642 remains active, the gateway service is active, and Telegram is connected in polling mode. These are server-agent-reported checks, not independent requests from this coding session. The report does not establish an exact comparison between the pre-rotation key and every historical literal.
- **Other services:** the agent reports no changes to 9Router on port 20128, the SSH password, or the Telegram token during gateway rotation. The gateway key is distinct in purpose from the upstream provider key; moving clients to 9Router would not revoke the gateway credential.
- **Phone follow-up:** any Hermes Home profile connecting to gateway port 8642 needs the new key entered privately, followed by a chat/model request to verify the updated profile. Completion of this phone check has not yet been reported.
- **Tailscale:** the server agent's earlier read-only audit found no auth-key plaintext in its scoped locations. Whether the historical auth-key example was real, reused, expired, or revoked remains unknown; check the corresponding auth key in the admin console if applicable. Do not log out or delete the registered device to rotate a registration key.

Any historical value still used elsewhere must be replaced or revoked at that source. Source-history rewriting and APK deletion do not invalidate credentials. The changed SSH password and gateway rotation above should not be described as verification of every historical credential or token.

Do not paste the old values into issues, Support drafts, screenshots, or public documentation. Supply identifiers and affected paths when possible.

## Release and device status

The maintainer reports that 1.3.1 now runs smoothly on a physical phone. The exact signed candidate also passed installation, launch, device-PIN authentication, and four-tab navigation on an Android 14 emulator. These observations are separate from the historical-credential and GitHub-cache questions.

The cached-history request has a Support purge confirmation and passed the checks above. SSH password replacement and gateway rotation are recorded from the maintainer's reports. Repository visibility remains private; the updated phone profile check and the historical Tailscale auth-key status remain unresolved.
