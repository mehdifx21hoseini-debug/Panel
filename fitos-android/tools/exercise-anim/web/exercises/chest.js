// Chest exercises.
import { V3, X, Y, Z, deg, lerp, arm, sit, supine, stand, shoulders, barGrip, bothFists, medial, SX, ELBOWS_OUT, KNEES_UP, ARM_LEN, plank, liftFor, chestFrame, legs, footQ, torso, REST_ROOT, hips, pWorld, B, mid } from './common.js';
import { easeInOut } from '../figure.js';
import { barbell, dumbbell, flatBench, inclineBench, cableTower, dHandle, seat, lever, stack, step, floorMat, dipBars, band, pad, rod, MAT, part } from '../props.js';

const PRESS_POLES = [V3(0.8, -1, 0.35), V3(-0.8, -1, 0.35)];
const reach = () => ARM_LEN * 0.97;

/** A bar pressed from the chest: lying flat or on an incline. */
function barPress(p, incline) {
  if (incline) inclineBody(incline); else supine({ top: 4.4, hipZ: 1.2 });
  const sh = shoulders(), { up, fwd } = chestFrame();
  // From over the shoulders down to the lower chest: a slight J.
  const top = sh.clone().add(fwd.clone().multiplyScalar(reach() + 0.55)).add(up.clone().multiplyScalar(-0.2));
  const low = sh.clone().add(fwd.clone().multiplyScalar(2.25)).add(up.clone().multiplyScalar(incline ? -0.9 : -1.4));
  const bar = top.clone().lerp(low, p);
  bar.add(up.clone().multiplyScalar(-0.5 * Math.sin(Math.PI * p) * 0));
  barGrip(bar, 3.75, { dir: fwd, palm: up.clone().negate(), poles: PRESS_POLES });
  barbell(bar);
  if (incline) inclineBench(incline); else flatBench(4.4, -11.5, 3.0);
}
/** Lying back on an incline bench (seat at the origin). */
function inclineBody(incline) {
  torso({ root: V3(0, 5.25, -0.15), pitch: -(Math.PI / 2 - incline), flex: deg(-3), headPitch: deg(2) });
  legs({ ankles: [V3(2.6, 0.72, 5.0), V3(-2.6, 0.72, 5.0)], poles: [V3(0.3, 1, 0.3), V3(-0.3, 1, 0.3)], feet: [footQ(deg(18)), footQ(deg(-18))] });
}
/** Dumbbells pressed from beside the chest to over it. */
function dbPress(p, incline, floor = false) {
  if (incline) inclineBody(incline);
  else if (floor) floorBody();
  else supine({ top: 4.4, hipZ: 1.2 });
  const sh = shoulders(), { up, fwd, left } = chestFrame();
  const at = (s) => {
    const side = left.clone().multiplyScalar(SX[s]);
    const top = sh.clone().add(fwd.clone().multiplyScalar(reach() + 0.45)).add(side.clone().multiplyScalar(1.15)).add(up.clone().multiplyScalar(-0.3));
    const low = sh.clone().add(fwd.clone().multiplyScalar(floor ? 2.2 : 1.35)).add(side.clone().multiplyScalar(floor ? 3.0 : 3.3)).add(up.clone().multiplyScalar(-0.9));
    return top.lerp(low, p);
  };
  const pts = [at('L'), at('R')];
  bothFists(pts, { dirs: [fwd, fwd], palms: [up.clone().negate(), up.clone().negate()], poles: PRESS_POLES });
  pts.forEach((q) => dumbbell(q, left));
  if (incline) inclineBench(incline); else if (floor) floorMat(V3(0, 0, -3)); else flatBench(4.4, -11.5, 3.0);
}
function floorBody() {
  supine({ top: 0.1, hipZ: 1.2, feet: 'none', flex: 0 });
  legs({ ankles: [V3(1.7, 0.82, 4.9), V3(-1.7, 0.82, 4.9)], poles: [V3(0.2, 1, 0.3), V3(-0.2, 1, 0.3)], feet: [footQ(deg(8)), footQ(deg(-8))] });
}

/** Push-up on the floor or with the hands on a step `h` high. */
function pushUp(p, h = 0, close = false) {
  const wristY = h + 0.48;
  const top = liftFor(wristY + reach() + 0.05, {}, 'p' + h), low = liftFor(wristY + (h ? 1.9 : 1.75), {}, 'p' + h);
  const shTop = plank(top);
  plank(lerp(top, low, p));
  const x = close ? 1.25 : 2.6;
  ['L', 'R'].forEach((s, i) => arm(s, {
    wrist: V3(SX[s] * x, wristY, shTop.z + 0.25), pole: V3(SX[s] * (close ? 0.15 : 0.55), 0.5, -1),
    dir: V3(SX[s] * (close ? -0.1 : 0.18), 0, 1).normalize(), palm: V3(0, -1, 0), grip: 0.05,
  }));
  if (h) step(h, V3(0, 0, shTop.z + 0.6), 9, 3.2);
}

export default {
  barbell_bench_press: { view: { az: -38, el: 22 }, pose: (p) => barPress(p, 0) },
  incline_barbell_press: { view: { az: -58, el: 14 }, pose: (p) => barPress(p, deg(35)) },
  dumbbell_bench_press: { view: { az: -38, el: 22 }, pose: (p) => dbPress(p, 0) },
  incline_dumbbell_press: { view: { az: -58, el: 14 }, pose: (p) => dbPress(p, deg(35)) },
  db_floor_press: { view: { az: -38, el: 24 }, pose: (p) => dbPress(p, 0, true) },

  dumbbell_fly: {
    view: { az: -20, el: 30 },
    pose(p) {
      supine({ top: 4.4, hipZ: 1.2 });
      const sh = shoulders(), { up, fwd, left } = chestFrame();
      const a = deg(lerp(8, 72, p)), R = ARM_LEN * 0.9;
      const pts = ['L', 'R'].map((s) => sh.clone().add(left.clone().multiplyScalar(SX[s] * (1.0 + R * Math.sin(a)))).add(fwd.clone().multiplyScalar(R * Math.cos(a) + 0.35)).add(up.clone().multiplyScalar(-0.2)));
      bothFists(pts, {
        dirs: ['L', 'R'].map((s) => fwd.clone().multiplyScalar(Math.cos(a)).add(left.clone().multiplyScalar(SX[s] * Math.sin(a)))),
        palms: ['L', 'R'].map((s) => left.clone().multiplyScalar(-SX[s] * Math.cos(a)).add(fwd.clone().multiplyScalar(Math.sin(a)))),
        poles: [V3(0.2, -1, 0.5), V3(-0.2, -1, 0.5)],
      });
      pts.forEach((q) => dumbbell(q, up));
      flatBench(4.4, -11.5, 3.0);
    },
  },

  cable_fly: {
    view: { az: -24, el: 8, zoom: 0.95 },
    pose(p) {
      stand({ pitch: deg(14), flex: deg(4), headPitch: deg(-8), ankles: [V3(1.7, 0.72, 1.3), V3(-1.9, 0.72, -1.6)], knees: [V3(0.2, 0, 1), V3(-0.2, 0, 1)] });
      const sh = shoulders();
      const a = deg(lerp(78, 6, p));
      const pts = ['L', 'R'].map((s) => sh.clone().add(V3(SX[s] * (0.5 + 4.9 * Math.sin(a)), lerp(-2.6, 0.9, Math.sin(a)), 0.6 + 4.6 * Math.cos(a))));
      bothFists(pts, {
        dirs: ['L', 'R'].map((s) => V3(SX[s] * Math.sin(a), -0.35, Math.cos(a)).normalize()),
        palms: ['L', 'R'].map((s) => V3(-SX[s] * Math.cos(a), 0, Math.sin(a)).normalize()),
        poles: [V3(0.6, -0.8, -0.3), V3(-0.6, -0.8, -0.3)],
      });
      ['L', 'R'].forEach((s, i) => { const pul = V3(SX[s] * 8.6, 19.5, sh.z - 0.6); cableTower(pul, pts[i]); dHandle(pts[i], V3(0, 1, 0), pts[i].clone().sub(pul).normalize().negate()); });
    },
  },

  machine_chest_press: {
    view: { az: -55, el: 10 },
    concentric: 'go',
    pose(p) {
      sit({ seatY: 4.6, hipZ: -0.9, pitch: deg(-12), flex: deg(-4), headPitch: deg(8) });
      const sh = shoulders();
      const pts = ['L', 'R'].map((s) => sh.clone().add(V3(SX[s] * lerp(3.1, 2.4, p), -0.9, lerp(1.0, 4.6, p))));
      bothFists(pts, { dirs: [Z, Z], palms: [medial('L'), medial('R')], poles: [V3(1, -0.6, -0.4), V3(-1, -0.6, -0.4)] });
      seat(4.6, V3(0, 0, 0), { backZ: -2.2, lean: deg(12), backH: 8 });
      pts.forEach((q, i) => { const piv = V3(q.x, sh.y + 6.5, sh.z - 1.5); lever(piv, q.clone().add(V3(0, 0.75, 0))); rod(q.clone().add(V3(0, -0.8, 0)), q.clone().add(V3(0, 0.8, 0)), 0.18, MAT.rubber()); });
      stack(sh.z - 4.2, 0, 19);
    },
  },

  pec_deck: {
    view: { az: -22, el: 12 },
    concentric: 'go',
    pose(p) {
      sit({ seatY: 4.6, hipZ: -0.9, pitch: deg(-6), headPitch: deg(4) });
      const sh = shoulders();
      const a = deg(lerp(84, 12, p)), R = ARM_LEN * 0.88;
      const pts = ['L', 'R'].map((s) => shoulders().add(V3(SX[s] * (0.6 + R * Math.sin(a)), -0.25, 0.3 + R * Math.cos(a))));
      bothFists(pts, {
        dirs: ['L', 'R'].map((s) => V3(SX[s] * Math.sin(a), 0, Math.cos(a))),
        palms: ['L', 'R'].map((s) => V3(-SX[s] * Math.cos(a), 0, Math.sin(a))),
        poles: [V3(0.3, -1, -0.2), V3(-0.3, -1, -0.2)],
      });
      seat(4.6, V3(), { backZ: -2.0, lean: deg(6), backH: 8 });
      pts.forEach((q, i) => { const piv = V3(SX[i ? 'R' : 'L'] * 1.3, sh.y + 4.2, sh.z - 0.8); lever(piv, q.clone().add(V3(0, 1.0, 0))); rod(q.clone().add(V3(0, -0.9, 0)), q.clone().add(V3(0, 1.0, 0)), 0.18, MAT.rubber()); });
      stack(sh.z - 4.4, 0, 19);
    },
  },

  push_up: { view: { az: -62, el: 13 }, pose: (p) => pushUp(p, 0) },
  incline_push_up: { view: { az: -70, el: 10 }, pose: (p) => pushUp(p, 4.6) },

  chest_dip: {
    view: { az: -55, el: 6 },
    pose(p) {
      const barY = 12.6, half = 2.75;
      // Lean the chest forward and lower until the upper arms are about level.
      const want = barY + 0.35 + lerp(ARM_LEN * 0.95, 1.1, p);
      let dy = 0;
      for (let k = 0; k < 3; k++) {
        torso({ root: REST_ROOT().add(V3(0, 3.6 + dy, -0.4)), pitch: deg(lerp(14, 30, p)), flex: deg(6), headPitch: deg(-6) });
        dy += want - shoulders().y;
      }
      const h = hips(), { fwd } = chestFrame();
      legs({ ankles: [h.clone().add(V3(0.6, -6.6, -3.4)), h.clone().add(V3(-0.2, -6.5, -3.0))], poles: [V3(0.1, -0.2, 1), V3(-0.1, -0.2, 1)], feet: [footQ(deg(4), deg(30)), footQ(deg(-4), deg(30))] });
      const sh = shoulders();
      ['L', 'R'].forEach((s) => arm(s, { fist: V3(SX[s] * half, barY + 0.25, sh.z + 0.7), pole: V3(SX[s] * 0.3, 0.3, -1), dir: V3(-SX[s] * 0.4, 0, 1).normalize(), palm: V3(0, -1, 0), grip: 0.75 }));
      dipBars(barY, half, V3(0, 0, sh.z + 0.2));
    },
  },

  band_chest_press: {
    view: { az: -60, el: 8 },
    concentric: 'go',
    pose(p) {
      stand({ ankles: [V3(2.0, 0.72, 1.2), V3(-2.0, 0.72, -1.4)], pitch: deg(6), headPitch: deg(-4) });
      const sh = shoulders(), { up, fwd, left } = chestFrame();
      const pts = ['L', 'R'].map((s) => sh.clone().add(left.clone().multiplyScalar(SX[s] * lerp(2.9, 1.6, p))).add(up.clone().multiplyScalar(-0.7)).add(fwd.clone().multiplyScalar(lerp(1.4, 5.5, p))));
      bothFists(pts, { dirs: [fwd, fwd], palms: [up.clone().negate(), up.clone().negate()], poles: [V3(1, -0.5, -0.5), V3(-1, -0.5, -0.5)] });
      const backPts = ['L', 'R'].map((s) => sh.clone().add(left.clone().multiplyScalar(SX[s] * 2.4)).add(up.clone().multiplyScalar(-1.2)).add(fwd.clone().multiplyScalar(-1.3)));
      band([pts[0], backPts[0], sh.clone().add(up.clone().multiplyScalar(-1.0)).add(fwd.clone().multiplyScalar(-1.75)), backPts[1], pts[1]]);
    },
  },
};
