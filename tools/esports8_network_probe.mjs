import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const matchId = process.argv[2];
if (!matchId) throw new Error('match id required');
const outDir = path.join('probe', matchId);
fs.mkdirSync(outDir, { recursive: true });
const logPath = path.join(outDir, 'network.jsonl');
const write = (obj) => fs.appendFileSync(logPath, JSON.stringify({ ts: new Date().toISOString(), ...obj }) + '\n');
const truncate = (s, n = 12000) => String(s ?? '').slice(0, n);
const interesting = (url) => /esports8|api|match|game|live|event|score|lol|socket/i.test(url);

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  userAgent: 'Mozilla/5.0 (Linux; Android 16; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36',
  viewport: { width: 412, height: 915 },
  locale: 'zh-CN',
  recordHar: { path: path.join(outDir, 'network.har'), content: 'embed', mode: 'full' },
});
const page = await context.newPage();

page.on('request', req => {
  if (!interesting(req.url())) return;
  write({ type: 'request', resourceType: req.resourceType(), method: req.method(), url: req.url(), postData: truncate(req.postData(), 4000) });
});
page.on('response', async res => {
  const req = res.request();
  const url = res.url();
  const ct = res.headers()['content-type'] || '';
  if (!interesting(url) && !['xhr', 'fetch', 'eventsource'].includes(req.resourceType())) return;
  const base = { type: 'response', resourceType: req.resourceType(), method: req.method(), status: res.status(), url, contentType: ct };
  if (/json|text|javascript|event-stream/i.test(ct) || ['xhr', 'fetch', 'eventsource'].includes(req.resourceType())) {
    try { write({ ...base, body: truncate(await res.text()) }); }
    catch (e) { write({ ...base, bodyError: String(e) }); }
  } else write(base);
});
page.on('websocket', ws => {
  write({ type: 'websocket-open', url: ws.url() });
  ws.on('framesent', ev => write({ type: 'websocket-send', url: ws.url(), payload: truncate(ev.payload) }));
  ws.on('framereceived', ev => write({ type: 'websocket-recv', url: ws.url(), payload: truncate(ev.payload) }));
  ws.on('close', () => write({ type: 'websocket-close', url: ws.url() }));
  ws.on('socketerror', err => write({ type: 'websocket-error', url: ws.url(), error: String(err) }));
});
page.on('console', msg => write({ type: 'console', level: msg.type(), text: truncate(msg.text(), 3000) }));
page.on('pageerror', err => write({ type: 'pageerror', error: truncate(err.stack || err.message, 5000) }));

const url = `https://m.esports8.com/lol/match/${matchId}/`;
write({ type: 'probe-start', url });
try {
  const response = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  write({ type: 'navigation', status: response?.status() ?? null, finalUrl: page.url() });
  await page.waitForTimeout(65000);
  fs.writeFileSync(path.join(outDir, 'final-url.txt'), page.url());
  fs.writeFileSync(path.join(outDir, 'title.txt'), await page.title());
  await page.screenshot({ path: path.join(outDir, 'page.png'), fullPage: true });
} catch (e) {
  write({ type: 'probe-error', error: truncate(e.stack || e.message, 8000) });
} finally {
  await context.close();
  await browser.close();
}

const lines = fs.readFileSync(logPath, 'utf8').trim().split('\n').filter(Boolean).map(x => JSON.parse(x));
const candidates = lines.filter(x => ['request','response','websocket-open'].includes(x.type) && interesting(x.url || ''));
console.log(`MATCH=${matchId} EVENTS=${lines.length} CANDIDATES=${candidates.length}`);
for (const x of candidates.slice(0, 120)) console.log(JSON.stringify({ type:x.type, resourceType:x.resourceType, method:x.method, status:x.status, url:x.url, contentType:x.contentType }));
