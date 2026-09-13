#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core" / "src"

FORBIDDEN_IMPORT_PREFIXES = (
    "import android.",
    "import androidx.",
    "import okhttp3.",
    "import coil.",
    "import com.google.mlkit.",
    "import com.google.ai.edge.",
    "import org.json.",
)

violations = []
for path in sorted(CORE.rglob("*.kt")):
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith(FORBIDDEN_IMPORT_PREFIXES):
            violations.append(f"{path.relative_to(ROOT)}:{number}: {stripped}")

if violations:
    print("CORE_BOUNDARY_VIOLATION")
    print("\n".join(violations))
    sys.exit(1)

print(f"CORE_BOUNDARY_OK files={len(list(CORE.rglob('*.kt')))}")
