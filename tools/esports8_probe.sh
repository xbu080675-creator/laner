#!/usr/bin/env bash
set -euo pipefail

MATCH_ID="${1:-er5s6s3t2r559f6}"
PAGE_URL="https://m.esports8.com/lol/match/${MATCH_ID}/"
UA='Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36'
mkdir -p probe

curl -L --compressed -A "$UA" -D probe/page.headers -o probe/page.html "$PAGE_URL"
printf 'PAGE_STATUS\n'
head -n 20 probe/page.headers || true
printf '\nPAGE_SIZE '; wc -c < probe/page.html

python3 - <<'PY'
from pathlib import Path
import re, html
s=Path('probe/page.html').read_text(errors='ignore')
urls=[]
for u in re.findall(r'''(?:src|href)=["']([^"']+)["']''', s, re.I):
    u=html.unescape(u)
    if '_nuxt/' in u and (u.endswith('.js') or '.js?' in u):
        if u.startswith('//'): u='https:'+u
        elif u.startswith('/'):
            u='https://m.esports8.com'+u
        elif not u.startswith('http'):
            u='https://m.esports8.com/'+u.lstrip('/')
        if u not in urls: urls.append(u)
Path('probe/assets.txt').write_text('\n'.join(urls))
print('NUXT_ASSETS', len(urls))
for u in urls: print(u)
PY

mkdir -p probe/js
while IFS= read -r u; do
  [ -n "$u" ] || continue
  name=$(printf '%s' "$u" | sha256sum | cut -c1-16)
  curl -L --compressed -A "$UA" --max-time 30 -sS "$u" -o "probe/js/$name.js" || true
done < probe/assets.txt

printf '\nENDPOINT_STRINGS\n'
grep -RhoE 'https?://[^"'\'' ]+|wss?://[^"'\'' ]+|/[A-Za-z0-9_./?-]*(match|game|live|event|detail|score|lol)[A-Za-z0-9_./?=&${}:-]*' probe/js probe/page.html 2>/dev/null \
  | sed 's/[),;]$//' \
  | sort -u \
  | grep -Ei 'api|match|game|live|event|detail|score|websocket|socket|lol' \
  | head -n 400 || true

printf '\nESPORTS8_API_REFERENCES\n'
grep -RniE 'api\.esports8|ddbaisuiyuan|detail_nav|event_nav|match_id|game_id|websocket|socket\.io|wss://' probe/js probe/page.html 2>/dev/null | head -n 300 || true

for path in \
  "https://api.esports8.com/v1/match/detail_nav?match_id=$MATCH_ID" \
  "https://api.esports8.com/v1/match/event_nav?match_id=$MATCH_ID" \
  "https://api.esports8.com/v1/match/list"; do
  printf '\nPROBE %s\n' "$path"
  curl -L --compressed -A "$UA" -H 'Accept: application/json,text/plain,*/*' -sS --max-time 20 -D - "$path" -o probe/api.tmp || true
  printf 'BODY_PREFIX\n'
  head -c 4000 probe/api.tmp 2>/dev/null || true
  printf '\n'
done
