# Security policy

## Reporting a vulnerability

Do not include passwords, API keys, private keys, backup files, personal file contents, or unredacted server logs in public issues.

When available, use [GitHub's private vulnerability reporting form](https://github.com/knxxy205/Hermes-Home/security/advisories/new). If that form is unavailable, open an issue titled "Request private security contact" with no vulnerability details or sensitive data. The maintainer can arrange a private channel before you share the report. Do not assume a public issue is a private reporting channel.

Include the app version, Android version, a minimal reproduction using test data, expected/observed behavior, and the effect you believe an attacker could achieve. There is no guaranteed response time or bug-bounty program.

## Supported versions

Security fixes target the latest beta/source version. Older APKs are not maintained; update before reporting. Beta status and automated tests do not imply a security certification.

## Operational guidance

- Use a dedicated server account with only the permissions you need. Avoid routine root access.
- Confirm the SSH host-key fingerprint using an independent, trusted connection.
- Use HTTPS for Hermes whenever possible. Private-network HTTP is available for self-hosted endpoints but is not TLS-encrypted.
- Review AI-generated commands before choosing Run on Server. The app can execute commands with your configured SSH account's privileges.
- Treat exported profiles and downloaded server files as sensitive, even when the export is password protected.
- An app lock does not revoke credentials or stop all scheduled background work.

## Historical credentials

The Git history was sanitized after credential/example literals were found in old presets. Removing them from source, releases, or caches does not revoke credentials on a real server. Any reused SSH password, API key, or token must be rotated by the server/service administrator. Other clones, downloaded APKs, backups, and GitHub's unreachable object caches can retain original data.
