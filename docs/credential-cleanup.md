# Credential cleanup verification

Review date: 3 November 2026. Repository visibility remains private. This record describes checks performed and unresolved dependencies; it is not a certification that historical credentials are invalid.

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

The original commit still responds through GitHub's Git commit API despite being absent from all advertised branches and tags. A metadata comparison matched the 28 old commits to rewritten commits by unique author timestamp and commit message, identifying the original root as the earliest rewritten commit. This reconstruction is not the original `git-filter-repo` report.

GitHub documents server garbage collection and cached-view removal as a Support operation. There is no repository-owner Git command or public purge API that completes that step. On 3 November 2026, the maintainer-approved private request was submitted through GitHub Support using the subject "Request review and purge of cached sensitive history — knxxy205/Hermes-Home" and issue type "General question or feature request". The report contains the original and rewritten commit identifiers, affected paths, verification results, and the explicitly unknown credential-revocation status; it omits credential values.

The portal displayed "Thanks for getting in touch with us! Your message has been successfully submitted." That confirmation was saved outside the repository. The maintainer subsequently supplied the matching Support ticket title and confirmed case **#4945452**, status **Open**, in the Repositories category. The case number is now recorded from that maintainer-provided confirmation; the stored report body has not been independently reread. An open case does not establish policy acceptance or server-side deletion. Follow up on this existing case through the [private Support ticket portal](https://help.github.com/support/tickets). No purge confirmation has been received.

After submission, the original commit `32c74b5e89ad5e1195d0d9012f6898e275ff2a01` still responded through the authenticated Git commit API. Repository visibility was rechecked as private.

GitHub also states that Support will only assist with sensitive-data removal where it determines the risk cannot be mitigated by rotating credentials. Support acceptance and eventual deletion must not be assumed.

Reference: [Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository#fully-removing-the-data-from-github).

## Live credentials

No SSH server, Hermes/API gateway, or Tailscale credential was revoked or changed by this audit. The maintainer could not confirm whether the historical values had been used or already replaced. There is no verified server-administration connection in this session from which to make those changes.

Before public distribution, the corresponding service administrator needs to establish whether those values were real and used. Any real, reused value must be replaced or revoked at its source, then the phone's connection profile updated as necessary. Source-history rewriting, APK deletion, successful app authentication, and a clean Git scan do not establish credential revocation.

Do not paste the old values into issues, Support drafts, screenshots, or public documentation. Supply identifiers and affected paths when possible.

## Release and device status

The maintainer reports that 1.3.1 now runs smoothly on a physical phone. The exact signed candidate also passed installation, launch, device-PIN authentication, and four-tab navigation on an Android 14 emulator. These observations are separate from the historical-credential and GitHub-cache questions.

Public visibility remains pending server-credential verification and resolution of the cached-history request. Do not describe either unresolved item as completed.
