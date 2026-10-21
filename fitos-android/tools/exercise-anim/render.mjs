// Renders exercise frames with headless Chromium.
//   node render.mjs                 every exercise, 30 fps, into build/frames/<key>/
//   node render.mjs squat curl      only these
//   node render.mjs --sheet ...     stills at the start, middle and end into build/sheets/
// CHROMIUM=/path/to/chrome uses a given browser instead of Playwright's own.
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname);
const args = process.argv.slice(2);
const flag = (name, def) => { const i = args.indexOf(name); if (i < 0) return def; const v = args[i + 1]; args.splice(i, 2); return v; };
const sheet = args.includes('--sheet'); if (sheet) args.splice(args.indexOf('--sheet'), 1);
const size = Number(flag('--size', sheet ? 360 : 480)), fps = Number(flag('--fps', 30)), ao = Number(flag('--ao', 16)), limit = Number(flag('--limit', 0));

const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM || undefined,
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
});
const page = await browser.newPage({ viewport: { width: size, height: size } });
page.on('pageerror', (e) => console.error('[page]', e.message));
page.on('console', (m) => { if (m.type() === 'error') console.error('[console]', m.text()); });
const TYPES = { '.js': 'text/javascript', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.html': 'text/html' };
await page.route('http://anim.local/**', (route) => {
  const f = path.join(here, decodeURIComponent(new URL(route.request().url()).pathname));
  if (!fs.existsSync(f)) return route.fulfill({ status: 404, body: 'missing ' + f });
  route.fulfill({ body: fs.readFileSync(f), contentType: TYPES[path.extname(f)] || 'application/octet-stream' });
});
await page.goto('http://anim.local/web/index.html');
await page.waitForFunction(() => window.FIG_READY === true, null, { timeout: 180000 });
await page.evaluate((ao) => window.setQuality({ ao }), ao);

const keys = args.length ? args : await page.evaluate(() => window.exerciseKeys());
const shot = async (key, t, p) => Buffer.from((await page.evaluate(([k, t, p, s]) => window.renderFrame(k, t, p, s), [key, t, p, size])).split(',')[1], 'base64');
for (const key of keys) {
  if (sheet) {
    const dir = path.join(here, 'build/sheets'); fs.mkdirSync(dir, { recursive: true });
    for (const p of [0, 0.5, 1]) fs.writeFileSync(path.join(dir, `${key}_${p}.png`), await shot(key, 0, p));
    console.log('sheet', key);
    continue;
  }
  const dir = path.join(here, 'build/frames', key);
  const secs = await page.evaluate((k) => window.cycleSeconds(k), key);
  const n = limit || Math.round(secs * fps);
  if (process.env.SKIP_DONE && fs.existsSync(dir) && fs.readdirSync(dir).length === n) { console.log(key, 'already rendered'); continue; }
  fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true });
  for (let i = 0; i < n; i++) fs.writeFileSync(path.join(dir, `f${String(i).padStart(4, '0')}.png`), await shot(key, (i / n) * secs, null));
  console.log(key, n, 'frames');
}
await browser.close();
