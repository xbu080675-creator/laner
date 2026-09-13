#!/usr/bin/env python3
from __future__ import annotations

import base64
import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urljoin, urlparse, urlunparse

import requests
from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright

SENSITIVE_KEYS = {"token", "access_token", "apikey", "api_key", "key", "sign", "signature", "auth", "authorization", "session", "cookie"}
API_HINTS = ("api", "/v1/", "/v2/", "match", "event", "live", "detail", "score", "stat", "game", "socket", "ws")
JS_HINT_RE = re.compile(r"(?:https?:\\/\\/|wss?:\\/\\/|/v\\d+/)[^\\\"'`\\s]{4,220}|/(?:match|event|live|game|score|stat|detail)[A-Za-z0-9_?&=./:%-]{2,180}", re.I)


def redact_url(url: str) -> str:
    try:
        p = urlparse(url)
        pairs = []
        for k, v in parse_qsl(p.query, keep_blank_values=True):
            if k.lower() in SENSITIVE_KEYS or any(s in k.lower() for s in ("token", "secret", "auth", "sign", "key")):
                v = "REDACTED"
            pairs.append((k, v))
        return urlunparse((p.scheme, p.netloc, p.path, p.params, urlencode(pairs, doseq=True), p.fragment))
    except Exception:
        return url


def is_esports8(url: str) -> bool:
    try:
        host = (urlparse(url).hostname or "").lower()
        return host == "esports8.com" or host.endswith(".esports8.com")
    except Exception:
        return False


def looks_candidate(url: str) -> bool:
    low = url.lower()
    return is_esports8(url) and any(h in low for h in API_HINTS)


def save_public_response(out: Path, name: str, response: requests.Response) -> dict:
    content_type = response.headers.get("content-type", "")
    body = response.content
    suffix = ".json" if "json" in content_type.lower() else ".txt"
    path = out / f"rest_{name}{suffix}"
    if len(body) <= 4_000_000:
        path.write_bytes(body)
    meta = {
        "name": name,
        "status": response.status_code,
        "url": redact_url(response.url),
        "content_type": content_type,
        "bytes": len(body),
        "saved_as": path.name if path.exists() else "",
    }
    try:
        parsed = response.json()
        if isinstance(parsed, dict):
            meta["top_keys"] = list(parsed.keys())[:40]
    except Exception:
        pass
    return meta


def collect_box_numbers(value) -> list[int]:
    found: list[int] = []
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "box_num":
                try:
                    found.append(int(child))
                except Exception:
                    pass
            found.extend(collect_box_numbers(child))
    elif isinstance(value, list):
        for child in value:
            found.extend(collect_box_numbers(child))
    return found


def frame_record(direction: str, ws_url: str, payload) -> dict:
    record = {
        "ts_epoch_ms": int(time.time() * 1000),
        "direction": direction,
        "ws_url": redact_url(ws_url),
    }
    if isinstance(payload, bytes):
        record["kind"] = "binary"
        record["length"] = len(payload)
        record["base64"] = base64.b64encode(payload).decode("ascii")
        record["hex_prefix"] = payload[:96].hex()
    else:
        text = str(payload)
        encoded = text.encode("utf-8", errors="replace")
        record["kind"] = "text"
        record["length"] = len(encoded)
        record["text"] = text[:200_000]
    return record


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: esports8_live_probe.py <target_url> <match_id> <output_dir>", file=sys.stderr)
        return 2

    target_url, match_id, out_arg = sys.argv[1:]
    out = Path(out_arg)
    out.mkdir(parents=True, exist_ok=True)
    origin = "https://m.esports8.com"
    headers = {
        "User-Agent": "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.7",
        "Referer": target_url,
        "Accept": "application/json, text/plain, */*",
    }

    print(f"TARGET={target_url}")
    print(f"MATCH_ID={match_id}")

    # Layer 1: public HTTP page + exact REST endpoints used by the current frontend bundle.
    session = requests.Session()
    session.headers.update(headers)
    scripts: list[str] = []
    try:
        r = session.get(target_url, timeout=20, allow_redirects=True)
        print(f"HTTP_PAGE_STATUS={r.status_code}")
        print(f"HTTP_PAGE_FINAL={redact_url(r.url)}")
        (out / "raw_page.html").write_text(r.text, encoding="utf-8", errors="replace")
        (out / "raw_page_headers.json").write_text(
            json.dumps({k: v for k, v in r.headers.items() if k.lower() not in {"set-cookie"}}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        soup = BeautifulSoup(r.text, "html.parser")
        scripts = [urljoin(r.url, s.get("src")) for s in soup.find_all("script") if s.get("src")]
        (out / "raw_script_urls.txt").write_text("\n".join(redact_url(u) for u in scripts), encoding="utf-8")
    except Exception as exc:
        print(f"HTTP_PAGE_ERROR={type(exc).__name__}:{exc}")

    rest_meta: list[dict] = []
    rest_json: dict[str, object] = {}
    initial_endpoints = {
        "detail_nav": f"{origin}/api/match/detail_nav?match_id={match_id}",
        "live_nav": f"{origin}/api/match/live_nav?match_id={match_id}",
        "event_nav": f"{origin}/api/match/event_nav?match_id={match_id}",
        "lineup_nav": f"{origin}/api/match/lineup_nav?match_id={match_id}",
        "players": f"{origin}/api/match/players?match_id={match_id}&match_count=0",
        "analysis": f"{origin}/api/match/data?match_id={match_id}",
    }
    for name, url in initial_endpoints.items():
        try:
            rr = session.get(url, timeout=20)
            meta = save_public_response(out, name, rr)
            rest_meta.append(meta)
            print(f"REST_{name.upper()}={rr.status_code} {meta['content_type']} {meta['bytes']}B")
            try:
                rest_json[name] = rr.json()
            except Exception:
                pass
        except Exception as exc:
            rest_meta.append({"name": name, "error": f"{type(exc).__name__}:{exc}"})
            print(f"REST_{name.upper()}_ERROR={type(exc).__name__}:{exc}")

    box_numbers: list[int] = []
    for key in ("live_nav", "event_nav", "detail_nav"):
        box_numbers.extend(collect_box_numbers(rest_json.get(key)))
    box_num = max(box_numbers) if box_numbers else 4
    print(f"RESOLVED_BOX_NUM={box_num}")

    detail_endpoints = {
        "live_data": f"{origin}/api/match/live_data?match_id={match_id}&box_num={box_num}",
        "event_data": f"{origin}/api/match/event_data?match_id={match_id}&box_num={box_num}",
        "text_live": f"{origin}/api/match/text_live?match_id={match_id}&index=0",
    }
    for name, url in detail_endpoints.items():
        try:
            rr = session.get(url, timeout=20)
            meta = save_public_response(out, name, rr)
            rest_meta.append(meta)
            print(f"REST_{name.upper()}={rr.status_code} {meta['content_type']} {meta['bytes']}B")
            try:
                rest_json[name] = rr.json()
            except Exception:
                pass
        except Exception as exc:
            rest_meta.append({"name": name, "error": f"{type(exc).__name__}:{exc}"})
            print(f"REST_{name.upper()}_ERROR={type(exc).__name__}:{exc}")

    (out / "rest_probe_meta.json").write_text(json.dumps(rest_meta, ensure_ascii=False, indent=2), encoding="utf-8")

    # Layer 2: real Chromium page. Capture the natural MQTT-over-WebSocket handshake/subscription/push frames.
    requests_seen: list[dict] = []
    responses_seen: list[dict] = []
    websocket_urls: list[str] = []
    websocket_frames: list[dict] = []
    json_index = 0

    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        context = browser.new_context(
            user_agent=headers["User-Agent"],
            locale="zh-CN",
            viewport={"width": 412, "height": 915},
        )
        page = context.new_page()

        def on_request(req):
            requests_seen.append({
                "method": req.method,
                "resource_type": req.resource_type,
                "url": redact_url(req.url),
            })

        def on_response(resp):
            nonlocal json_index
            url = resp.url
            safe_url = redact_url(url)
            ctype = (resp.headers.get("content-type") or "").lower()
            entry = {"status": resp.status, "url": safe_url, "content_type": ctype}
            responses_seen.append(entry)
            if is_esports8(url) and ("json" in ctype or looks_candidate(url)):
                try:
                    body = resp.body()
                    if len(body) <= 2_000_000:
                        suffix = ".json" if "json" in ctype else ".txt"
                        path = out / f"response_{json_index:03d}{suffix}"
                        path.write_bytes(body)
                        entry["saved_as"] = path.name
                        json_index += 1
                except Exception as exc:
                    entry["body_error"] = type(exc).__name__

        def on_websocket(ws):
            websocket_urls.append(redact_url(ws.url))
            ws.on("framesent", lambda payload, url=ws.url: websocket_frames.append(frame_record("sent", url, payload)))
            ws.on("framereceived", lambda payload, url=ws.url: websocket_frames.append(frame_record("received", url, payload)))

        page.on("request", on_request)
        page.on("response", on_response)
        page.on("websocket", on_websocket)

        try:
            resp = page.goto(target_url, wait_until="domcontentloaded", timeout=60_000)
            print(f"BROWSER_PAGE_STATUS={resp.status if resp else 'NO_RESPONSE'}")
            page.wait_for_timeout(35_000)
            (out / "rendered_page.html").write_text(page.content(), encoding="utf-8", errors="replace")
            page.screenshot(path=str(out / "rendered_page.png"), full_page=True)
        except Exception as exc:
            print(f"BROWSER_PAGE_ERROR={type(exc).__name__}:{exc}")
            try:
                (out / "rendered_page.html").write_text(page.content(), encoding="utf-8", errors="replace")
            except Exception:
                pass
        finally:
            context.close()
            browser.close()

    (out / "network_requests.json").write_text(json.dumps(requests_seen, ensure_ascii=False, indent=2), encoding="utf-8")
    (out / "network_responses.json").write_text(json.dumps(responses_seen, ensure_ascii=False, indent=2), encoding="utf-8")
    (out / "websockets.txt").write_text("\n".join(dict.fromkeys(websocket_urls)), encoding="utf-8")
    with (out / "websocket_frames.jsonl").open("w", encoding="utf-8") as fh:
        for record in websocket_frames:
            fh.write(json.dumps(record, ensure_ascii=False) + "\n")

    browser_scripts = [x["url"] for x in requests_seen if x.get("resource_type") == "script" and is_esports8(x.get("url", ""))]
    all_scripts = list(dict.fromkeys(scripts + browser_scripts))
    js_hits: list[str] = []
    for script_url in all_scripts[:120]:
        if not is_esports8(script_url):
            continue
        try:
            rr = session.get(script_url, timeout=20)
            if rr.status_code != 200 or len(rr.content) > 8_000_000:
                continue
            text = rr.text
            for m in JS_HINT_RE.finditer(text):
                hit = m.group(0).replace("\\/", "/")
                if match_id in hit or any(h in hit.lower() for h in API_HINTS):
                    js_hits.append(hit)
        except Exception:
            continue

    candidate_urls = []
    for item in requests_seen:
        url = item.get("url", "")
        if looks_candidate(url):
            candidate_urls.append(f"{item.get('method','GET')} {url}")
    for item in responses_seen:
        url = item.get("url", "")
        if looks_candidate(url):
            candidate_urls.append(f"HTTP {item.get('status')} {url}" + (f" -> {item['saved_as']}" if item.get("saved_as") else ""))

    candidates = list(dict.fromkeys(candidate_urls))
    (out / "candidate_endpoints.txt").write_text("\n".join(candidates), encoding="utf-8")
    (out / "javascript_endpoint_strings.txt").write_text("\n".join(dict.fromkeys(js_hits)), encoding="utf-8")

    sent = sum(1 for f in websocket_frames if f["direction"] == "sent")
    received = sum(1 for f in websocket_frames if f["direction"] == "received")
    print(f"REQUEST_COUNT={len(requests_seen)}")
    print(f"RESPONSE_COUNT={len(responses_seen)}")
    print(f"WEBSOCKET_COUNT={len(set(websocket_urls))}")
    print(f"WS_FRAME_SENT={sent}")
    print(f"WS_FRAME_RECEIVED={received}")
    print(f"CANDIDATE_ENDPOINT_COUNT={len(candidates)}")
    print(f"JS_ENDPOINT_STRING_COUNT={len(set(js_hits))}")
    if websocket_urls:
        print("--- WEBSOCKETS ---")
        for line in dict.fromkeys(websocket_urls):
            print(line)
    if websocket_frames:
        print("--- WS FRAME LENGTHS ---")
        for f in websocket_frames[:80]:
            print(f"{f['direction']} {f['kind']} {f['length']}B {f.get('hex_prefix','')[:96]}")
    print("--- REST META ---")
    for item in rest_meta:
        print(json.dumps(item, ensure_ascii=False))
    print("--- JS STRINGS ---")
    for line in list(dict.fromkeys(js_hits))[:80]:
        print(line)

    # Research probe: third-party availability is evidence, not a CI gate.
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
