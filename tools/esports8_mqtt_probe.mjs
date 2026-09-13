import fs from 'node:fs';
import mqtt from 'mqtt';

const matchId = process.argv[2] || 'er5s6s3t2r559f6';
const durationMs = Number(process.argv[3] || 35000);
const outDir = `probe-mqtt/${matchId}`;
fs.mkdirSync(outDir, { recursive: true });
const out = fs.createWriteStream(`${outDir}/frames.jsonl`, { flags: 'a' });
const topics = [
  `match_live_jisu_zh/1:${matchId}`,
  `match_event_jisu_zh/1:${matchId}`,
];
const clientId = `mqttjs_${Math.random().toString(16).slice(2, 10)}`;
const client = mqtt.connect('wss://push.esports8.com:8099/', {
  protocolVersion: 4,
  clean: true,
  keepalive: 60,
  reconnectPeriod: 0,
  connectTimeout: 15000,
  clientId,
});

const write = obj => out.write(JSON.stringify({ ts: new Date().toISOString(), ...obj }) + '\n');
write({ type: 'start', matchId, clientId, topics });

client.on('connect', packet => {
  write({ type: 'connect', sessionPresent: !!packet?.sessionPresent });
  client.subscribe(topics, { qos: 0 }, (err, granted) => {
    if (err) write({ type: 'subscribe-error', error: String(err) });
    else write({ type: 'subscribed', granted });
  });
});
client.on('message', (topic, payload, packet) => {
  write({
    type: 'message',
    topic,
    qos: packet?.qos ?? 0,
    retain: !!packet?.retain,
    dup: !!packet?.dup,
    bytes: payload.length,
    payloadBase64: payload.toString('base64'),
  });
});
client.on('error', err => write({ type: 'error', error: String(err) }));
client.on('close', () => write({ type: 'close' }));

setTimeout(() => {
  write({ type: 'finish' });
  client.end(true, {}, () => out.end());
  setTimeout(() => process.exit(0), 1000);
}, durationMs);
