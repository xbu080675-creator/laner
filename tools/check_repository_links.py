#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SCAN_ROOTS = [
    ROOT / "app",
    ROOT / "scripts",
    ROOT / "tools",
    ROOT / ".github" / "workflows",
]
LEGACY_PATTERNS = (
    "github.com/xbu080675-creator/Rlftlab",
    "raw.githubusercontent.com/xbu080675-creator/Rlftlab",
    "cdn.jsdelivr.net/gh/xbu080675-creator/Rlftlab",
    "gitee.com/xiaobaiaaa1/Rlftlab",
)

violations = []
for base in SCAN_ROOTS:
    if not base.exists():
        continue
    for path in sorted(p for p in base.rglob("*") if p.is_file()):
        if path.resolve() == Path(__file__).resolve():
            continue
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue
        for number, line in enumerate(lines, start=1):
            if any(pattern in line for pattern in LEGACY_PATTERNS):
                violations.append(f"{path.relative_to(ROOT)}:{number}: {line.strip()}")

if violations:
    print("LEGACY_REPOSITORY_LINKS_FOUND")
    print("\n".join(violations))
    sys.exit(1)

print("REPOSITORY_LINKS_OK owner=xbu080675-creator repo=laner")
