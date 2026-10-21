// Renders one frame of an exercise for the headless driver (render.mjs).
import { THREE, V3, deg, easeInOut, easeOut, camera, aimLights, draw, rest, bounds, blink, LIFE, body, QUALITY } from './figure.js';
import { equipment, clearEquipment } from './props.js';
import chest from './exercises/chest.js';
import back from './exercises/back.js';
import shoulders from './exercises/shoulders.js';
import arms from './exercises/arms.js';
import legs from './exercises/legs.js';
import core from './exercises/core.js';
import cardio from './exercises/cardio.js';

const EXERCISES = { ...chest, ...back, ...shoulders, ...arms, ...legs, ...core, ...cardio };

/**
 * Timing. 'rep': a pause, the lowering part slow and even, a short pause, the lifting part
 * quicker (3 s). 'alt': one rep a side (6 s). 'hold': a held position (3 s). 'loop': a
 * continuous movement whose phase runs 0..1 `cycles` times over `seconds`.
 */
const TEMPO = { pause0: 0.45, ecc: 1.35, pause1: 0.15, con: 1.05 };
const REP = TEMPO.pause0 + TEMPO.ecc + TEMPO.pause1 + TEMPO.con;
export const seconds = (ex) => ex.seconds || (ex.mode === 'alt' ? 2 * REP : REP);
function repProgress(ex, t) {
  const { pause0, ecc, pause1, con } = TEMPO;
  t = ((t % REP) + REP) % REP;
  const out = ex.concentric === 'go';
  const goTime = out ? con : ecc, backTime = out ? ecc : con, goEase = out ? easeOut : easeInOut, backEase = out ? easeInOut : easeOut;
  if (t < pause0) return 0;
  if (t < pause0 + goTime) return goEase((t - pause0) / goTime);
  if (t < pause0 + goTime + pause1) return 1;
  return 1 - backEase((t - pause0 - goTime - pause1) / backTime);
}
function apply(ex, t, p) {
  clearEquipment(); rest();
  if (ex.mode === 'loop') { const c = ex.cycles || 1; ex.pose(p != null ? p : (((t / seconds(ex)) * c) % 1 + 1) % 1); return; }
  if (ex.mode === 'hold') { ex.pose(p != null ? p : 0); return; }
  if (ex.mode === 'alt') {
    const side = p != null ? 'L' : (t % (2 * REP)) < REP ? 'L' : 'R';
    ex.pose(p != null ? p : repProgress(ex, t), side); return;
  }
  ex.pose(p != null ? p : repProgress(ex, t));
}

// One camera per exercise, fitted to everything the movement reaches.
const fitCache = {};
function fit(key, ex) {
  if (fitCache[key]) return fitCache[key];
  const v = { az: -40, el: 10, zoom: 1, ...(ex.view || {}) };
  const box = new THREE.Box3();
  const samples = ex.mode === 'loop' ? [0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875] : [0, 0.25, 0.5, 0.75, 1];
  const sides = ex.mode === 'alt' ? ['L', 'R'] : [undefined];
  for (const s of sides) for (const p of samples) {
    clearEquipment(); rest();
    if (ex.mode === 'loop') ex.pose(p); else ex.pose(p, s);
    body.updateMatrixWorld(true);
    box.union(bounds(ex.frameEquipment === false ? null : equipment));
  }
  if (ex.clip) box.intersect(new THREE.Box3(V3(...ex.clip[0]), V3(...ex.clip[1])));
  const corners = []; for (let i = 0; i < 8; i++) corners.push(V3(i & 1 ? box.max.x : box.min.x, i & 2 ? box.max.y : box.min.y, i & 4 ? box.max.z : box.min.z));
  const dir = V3(Math.sin(deg(v.az)) * Math.cos(deg(v.el)), Math.sin(deg(v.el)), Math.cos(deg(v.az)) * Math.cos(deg(v.el)));
  let target = box.getCenter(V3());
  const extent = (d) => {
    camera.position.copy(target).add(dir.clone().multiplyScalar(d)); camera.lookAt(target); camera.updateMatrixWorld(true);
    let x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
    corners.forEach((c) => { const q = c.clone().project(camera); x0 = Math.min(x0, q.x); x1 = Math.max(x1, q.x); y0 = Math.min(y0, q.y); y1 = Math.max(y1, q.y); });
    return { x0, x1, y0, y1 };
  };
  let lo = 5, hi = 400;
  for (let k = 0; k < 2; k++) {
    for (let i = 0; i < 40; i++) { const m = (lo + hi) / 2; const e = extent(m); if (Math.max(-e.x0, e.x1, -e.y0, e.y1) > 0.86 / v.zoom) lo = m; else hi = m; }
    // Centre what is seen, then fit again.
    const e = extent(hi), right = V3().setFromMatrixColumn(camera.matrixWorld, 0), up = V3().setFromMatrixColumn(camera.matrixWorld, 1);
    const h = Math.tan(deg(camera.fov / 2)) * hi;
    target = target.clone().add(right.multiplyScalar(((e.x0 + e.x1) / 2) * h)).add(up.multiplyScalar(((e.y0 + e.y1) / 2) * h));
    lo = 5; hi = 400;
  }
  for (let i = 0; i < 40; i++) { const m = (lo + hi) / 2; const e = extent(m); if (Math.max(-e.x0, e.x1, -e.y0, e.y1) > 0.86 / v.zoom) lo = m; else hi = m; }
  return (fitCache[key] = { dir, target, dist: hi, az: deg(v.az) });
}

window.exerciseKeys = () => Object.keys(EXERCISES);
window.setQuality = (q) => Object.assign(QUALITY, q);
window.cycleSeconds = (key) => seconds(EXERCISES[key]);
/** Draws `key` at time t (s), or at progress p when given (stills: no life, no blink). */
window.renderFrame = (key, t, p, size) => {
  const ex = EXERCISES[key];
  const f = fit(key, ex);
  const alive = p == null;
  const ph = (2 * Math.PI * t) / seconds(ex);
  LIFE.sway.set(alive ? 0.035 * Math.sin(ph + 0.7) : 0, 0, alive ? 0.02 * Math.sin(2 * ph) : 0);
  LIFE.breath = alive ? deg(0.9) * Math.sin(ph + 0.4) : 0;
  LIFE.nod = alive && ex.mode !== 'loop' ? deg(1.1) * Math.sin(ph + 1.3) : 0;
  apply(ex, t, p);
  LIFE.sway.set(0, 0, 0); LIFE.breath = 0; LIFE.nod = 0;
  const bt = (t % seconds(ex)) - 0.3;
  blink(alive && bt > 0 && bt < 0.2 ? Math.sin((Math.PI * bt) / 0.2) : 0);
  camera.position.copy(f.target).add(f.dir.clone().multiplyScalar(f.dist)); camera.lookAt(f.target);
  aimLights(f.az);
  draw(size);
  return document.querySelector('canvas').toDataURL('image/png');
};
window.FIG_READY = true;
