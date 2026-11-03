"""Catch the credential-literal pattern previously found in app presets; never print values."""
from pathlib import Path
import re
import sys

ASSIGNMENT = re.compile(
    r'\b(?:password|sshPassword|hermesKey|hermesApiKey|apiKey)'
    r'(?:\s*:\s*String\??)?\s*=\s*"([^"\r\n]*)"', re.IGNORECASE
)
SERVER_ENV = re.compile(r'\b(?:HERMES_API_KEY|TAILSCALE_AUTH_KEY)=([^\s\\";]+)')


def findings(text):
    lines = set()
    for pattern in (ASSIGNMENT, SERVER_ENV):
        for match in pattern.finditer(text):
            value = match.group(1)
            if not value or (value.startswith('<') and value.endswith('>')):
                continue
            if re.fullmatch(r'\$\{[\w.]+\}|\$\w+', value):
                continue
            lines.add(text.count('\n', 0, match.start()) + 1)
    return sorted(lines)


def main():
    root = Path(__file__).resolve().parents[1] / 'app/src/main'
    failures = []
    for path in root.rglob('*'):
        if path.suffix not in {'.kt', '.java', '.json', '.properties'}:
            continue
        for line in findings(path.read_text(encoding='utf-8')):
            failures.append(f'{path.relative_to(root)}:{line}: embedded credential literal (value redacted)')
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        print('Use user-entered configuration or private environment variables.', file=sys.stderr)
        return 1
    print('No embedded credential literals found in app production source.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
