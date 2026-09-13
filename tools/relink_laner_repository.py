#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVE_ROOTS = [ROOT / "app", ROOT / "scripts", ROOT / "tools"]
OLD_REPO = "xbu080675-creator/" + "Rlftlab"
NEW_REPO = "xbu080675-creator/laner"
OLD_GITEE = "https://gitee.com/xiaobaiaaa1/" + "Rlftlab/raw/main/data/global/starting_rosters.json"

changed = []
replacement_count = 0
gitee_removed = 0

for base in ACTIVE_ROOTS:
    for path in sorted(p for p in base.rglob("*") if p.is_file()):
        if path.name in {"relink_laner_repository.py", "check_repository_links.py"}:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        original = text
        if OLD_GITEE in text:
            # There is no verified Laner Gitee mirror. Drop the stale endpoint instead of
            # inventing a URL that may silently serve old or nonexistent data.
            lines = text.splitlines(keepends=True)
            kept = []
            for line in lines:
                if OLD_GITEE in line:
                    gitee_removed += 1
                    continue
                kept.append(line)
            text = "".join(kept)

        count = text.count(OLD_REPO)
        if count:
            replacement_count += count
            text = text.replace(OLD_REPO, NEW_REPO)

        if text != original:
            path.write_text(text, encoding="utf-8")
            changed.append(path.relative_to(ROOT).as_posix())

# The guard intentionally contains legacy signatures, so it must exclude itself from its own scan.
guard = ROOT / "tools" / "check_repository_links.py"
guard_text = guard.read_text(encoding="utf-8")
needle = '    for path in sorted(p for p in base.rglob("*") if p.is_file()):\n'
replacement = needle + '        if path.resolve() == Path(__file__).resolve():\n            continue\n'
if replacement not in guard_text:
    if needle not in guard_text:
        raise SystemExit("repository link guard loop anchor missing")
    guard.write_text(guard_text.replace(needle, replacement, 1), encoding="utf-8")
    changed.append(guard.relative_to(ROOT).as_posix())

if replacement_count < 20:
    raise SystemExit(f"unexpectedly low repository replacement count: {replacement_count}")
if gitee_removed != 2:
    raise SystemExit(f"expected 2 stale Gitee endpoints, removed {gitee_removed}")

print(f"RELINK_OK repo_replacements={replacement_count} stale_gitee_removed={gitee_removed}")
for item in changed:
    print(item)
