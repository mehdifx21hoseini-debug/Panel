// Back exercises.
import { V3, X, Y, Z, deg, lerp, arm, hang, sit, stand, hinge, shoulders, hips, barGrip, bothFists, medial, SX, ARM_LEN, placeShoulders, hangingLegs, chestFrame, legs, footQ, torso, supine, pWorld, B, KNEES_FWD, stance, REST_ROOT, balance } from './common.js';
import { barbell, dumbbell, flatBench, cableTower, handleBar, dHandle, seat, lever, stack, pullUpBar, pad, rod, roller, band, floorMat, MAT, box, group, part } from '../props.js';
import { THREE, easeInOut } from '../figure.js';

const R = () => ARM_LEN * 0.97;

/** Hanging from a bar; p 0 = arms long, 1 = chin over the bar. */
function pullUp(p, { half, under = false }) {
  const barY = 23.6;
  const sh = V3(0, lerp(barY - R() - 0.85, barY - 1.9, p), lerp(-0.1, -0.9, p));
  placeShoulders(sh, { pitch: deg(lerp(-4, -14, p)), flex: deg(lerp(0, -8, p)), headPitch: deg(lerp(0, 12, p)) });
  hangingLegs(0.5);
  ['L', 'R'].forEach((s) => arm(s, {
    fist: V3(SX[s] * half, barY, 0), pole: V3(SX[s] * (under ? 0.4 : 1), -0.6, under ? 0.5 : -0.3),
    dir: Y, palm: under ? V3(0, 0, -1) : Z,
  }));
  pullUpBar(barY, 0);
}

/** The lat pulldown station around a seated figure; `handle` is where the cable meets the attachment. */
function latStation(handle) {
  const knee = pWorld(B['lowerleg01.L']);
  seat(4.6, V3(0, 0, -0.6), { back: false });
  pad(5.4, 0.9, 1.2, V3(0, knee.y + 0.95, knee.z - 0.6));
  rod(V3(0, 0, knee.z - 0.6), V3(0, knee.y + 0.5, knee.z - 0.6), 0.25, MAT.frame());
  stack(-6.4, 0, 22.4);
  box(0.6, 0.6, 7.6, V3(0, 22.1, -2.8), MAT.frame());
  cableTower(V3(0, 21.5, handle.z), handle.clone().add(V3(0, 0.3, 0)), { column: false });
}
/** A lat bar: straight in the middle, the ends bent down. */
function latBar(at) {
  const g = group(at);
  part(new THREE.CylinderGeometry(0.15, 0.15, 7.6, 16), MAT.chrome(), V3(), { z: Math.PI / 2 }, g);
  [-1, 1].forEach((s) => part(new THREE.CylinderGeometry(0.15, 0.15, 3.4, 16), MAT.chrome(), V3(s * 5.25, -0.38, 0), { z: s * deg(77) }, g));
}
function pulldown(p, close) {
  sit({ seatY: 4.6, hipZ: -0.9, pitch: deg(lerp(-6, -16, p)), flex: deg(lerp(0, -4, p)), headPitch: deg(lerp(-4, 6, p)), footZ: 4.3, footX: 2.0, knees: [V3(0.25, 1, 1), V3(-0.25, 1, 1)] });
  const sh = shoulders();
  const bar = V3(0, lerp(sh.y + R() + 0.2, sh.y - 0.2, p), lerp(sh.z + 0.6, sh.z + 1.9, p) + 0.9 * Math.sin(Math.PI * p));
  if (close) {
    const pts = ['L', 'R'].map((s) => bar.clone().add(V3(SX[s] * 0.55, 0, 0)));
    bothFists(pts, { dirs: [Y, Y], palms: [medial('L'), medial('R')], poles: [V3(0.5, -1, -0.3), V3(-0.5, -1, -0.3)] });
    vHandle(bar);
  } else {
    barGrip(bar, 4.6, { dir: Y, palm: Z, poles: [V3(1, -0.5, -0.35), V3(-1, -0.5, -0.35)] });
    latBar(bar.clone().add(V3(0, 0.05, 0)));
  }
  latStation(bar);
}
/** A V handle: two short neutral grips joined in a V toward the cable. */
function vHandle(at, toward = Y) {
  const up = toward.clone().normalize();
  [-1, 1].forEach((s) => {
    rod(at.clone().add(V3(s * 0.55, -0.55, 0)), at.clone().add(V3(s * 0.55, 0.55, 0)), 0.17, MAT.rubber());
    rod(at.clone().add(V3(s * 0.55, 0, 0)), at.clone().add(up.clone().multiplyScalar(1.4)), 0.12, MAT.chrome());
  });
}

/** Bent over with a barbell: the bar hangs under the shoulders and is rowed to the lower chest. */
function barbellRow(p) {
  hinge({ pitch: deg(48), drop: 0.9, flex: deg(4), headPitch: deg(-22), knees: [V3(0.2, 0, 1), V3(-0.2, 0, 1)], over: 0.95 });
  const sh = shoulders(), { up, fwd } = chestFrame();
  const hangAt = sh.clone().add(V3(0, -R() - 0.6, 0.35));
  const pulled = sh.clone().add(up.clone().multiplyScalar(-2.9)).add(fwd.clone().multiplyScalar(2.1));
  const bar = hangAt.lerp(pulled, p);
  barGrip(bar, 2.8, { dir: V3(0, -1, 0.1).normalize(), palm: V3(0, 0, -1), poles: [V3(0.5, 0.3, -1), V3(-0.5, 0.3, -1)] });
  barbell(bar);
}

export default {
  pull_up: { view: { az: -150, el: 8 }, concentric: 'go', pose: (p) => pullUp(p, { half: 3.7 }) },
  chin_up: { view: { az: -35, el: 6 }, concentric: 'go', pose: (p) => pullUp(p, { half: 1.6, under: true }) },
  lat_pulldown: { view: { az: -142, el: 9 }, concentric: 'go', pose: (p) => pulldown(p, false) },
  close_grip_pulldown: { view: { az: -62, el: 9 }, concentric: 'go', pose: (p) => pulldown(p, true) },

  seated_cable_row: {
    view: { az: -62, el: 10 }, concentric: 'go',
    pose(p) {
      sit({ seatY: 3.6, hipZ: -2.2, pitch: deg(lerp(16, -4, p)), flex: deg(lerp(6, -2, p)), headPitch: deg(lerp(-8, 2, p)), footZ: 5.6, footX: 1.7, knees: [V3(0.2, 1, 0.3), V3(-0.2, 1, 0.3)] });
      // The feet push on a platform rather than the floor.
      const h = hips();
      legs({ ankles: [V3(1.7, 3.1, 5.4), V3(-1.7, 3.1, 5.4)], poles: [V3(0.2, 1, 0.3), V3(-0.2, 1, 0.3)], feet: [footQ(deg(6), deg(-62)), footQ(deg(-6), deg(-62))] });
      const sh = shoulders();
      const handle = V3(0, lerp(sh.y - 2.3, sh.y - 3.4, p), lerp(sh.z + R() + 0.7, sh.z + 1.5, p));
      const pts = ['L', 'R'].map((s) => handle.clone().add(V3(SX[s] * 0.55, 0, 0)));
      bothFists(pts, { dirs: [Z, Z], palms: [medial('L'), medial('R')], poles: [V3(0.4, -0.3, -1), V3(-0.4, -0.3, -1)] });
      ['L', 'R'].forEach((s) => rod(handle.clone().add(V3(SX[s] * 0.55, -0.55, 0)), handle.clone().add(V3(SX[s] * 0.55, 0.55, 0)), 0.17, MAT.rubber()));
      box(1.2, 3.6, 16, V3(0, 1.8, -1.2), MAT.frame());
      pad(2.8, 0.7, 3.4, V3(0, 3.25, -2.4));
      box(4.6, 0.4, 2.0, V3(0, 2.6, 5.8), MAT.dark()).rotation.x = deg(-28);
      box(3.0, 18, 1.2, V3(0, 9, 7.6), MAT.frame());
      cableTower(V3(0, 2.9, 7.0), handle, { column: false });
    },
  },

  barbell_row: { view: { az: -70, el: 6 }, concentric: 'go', pose: barbellRow },

  one_arm_db_row: {
    view: { az: -80, el: 12 }, concentric: 'go',
    pose(p) {
      // Left knee and hand on the bench, back level; the right arm rows the dumbbell to the hip.
      torso({ root: V3(0, 8.9, -2.2), pitch: deg(80), flex: deg(-4), headPitch: deg(-30), twist: deg(lerp(0, -12, p)) });
      const hL = pWorld(B['upperleg01.L']);
      legs({
        ankles: [V3(hL.x + 0.2, 5.1, hL.z - 4.0), V3(-2.4, 0.72, hL.z + 0.6)],
        poles: [V3(0, -1, 0.1), V3(-0.3, 0, 1)], feet: [footQ(0, deg(82)), footQ(deg(-12))],
      });
      const shL = pWorld(B['upperarm01.L']);
      arm('L', { wrist: V3(shL.x + 0.1, 4.95, shL.z + 0.3), pole: V3(0.3, 0, -1), dir: V3(0, 0, 1), palm: V3(0, -1, 0), grip: 0.1 });
      const shR = pWorld(B['upperarm01.R']);
      const db = lerpTo(shR.clone().add(V3(-0.1, -R() - 0.55, 0.4)), shR.clone().add(V3(-0.5, -2.2, -2.6)), p);
      arm('R', { fist: db, pole: V3(-0.2, 0.6, -1), dir: V3(0, -1, 0.15).normalize(), palm: V3(1, 0, 0), grip: 1 });
      dumbbell(db, Z);
      flatBench(4.4, hL.z - 5.5, shL.z + 1.8, V3(hL.x + 0.1, 0, 0));
    },
  },

  t_bar_row: {
    view: { az: -72, el: 8 }, concentric: 'go',
    pose(p) {
      hinge({ pitch: deg(42), drop: 1.2, flex: deg(4), headPitch: deg(-18), width: 1.0, knees: [V3(0.4, 0, 1), V3(-0.4, 0, 1)], over: 1.0 });
      const sh = shoulders(), { up, fwd } = chestFrame();
      const handle = sh.clone().add(V3(0, -R() - 0.5, 0.7)).lerp(sh.clone().add(up.clone().multiplyScalar(-2.6)).add(fwd.clone().multiplyScalar(2.0)), p);
      const pts = ['L', 'R'].map((s) => handle.clone().add(V3(SX[s] * 0.6, 0, 0)));
      bothFists(pts, { dirs: [V3(0, -1, 0.1), V3(0, -1, 0.1)], palms: [medial('L'), medial('R')], poles: [V3(0.4, 0.2, -1), V3(-0.4, 0.2, -1)] });
      // One end of the bar sits in a floor pivot behind; the loaded end rises with the handle.
      const pivot = V3(0, 0.4, -9.5), end = handle.clone().add(V3(0, 0.6, 1.2));
      rod(pivot, end.clone().add(end.clone().sub(pivot).normalize().multiplyScalar(4)), 0.2, MAT.chrome());
      box(1.6, 0.8, 1.6, pivot, MAT.dark());
      const plateAt = end.clone().add(end.clone().sub(pivot).normalize().multiplyScalar(2.4));
      const pl = part(new THREE.CylinderGeometry(2.25, 2.25, 0.55, 48), MAT.plate(), plateAt);
      pl.quaternion.setFromUnitVectors(Y, end.clone().sub(pivot).normalize());
      ['L', 'R'].forEach((s) => rod(pts[s === 'L' ? 0 : 1].clone().add(V3(0, 0, -0.6)), pts[s === 'L' ? 0 : 1].clone().add(V3(0, 0, 0.6)), 0.17, MAT.rubber()));
      rod(handle, end, 0.14, MAT.chrome());
    },
  },

  machine_row: {
    view: { az: -60, el: 10 }, concentric: 'go',
    pose(p) {
      sit({ seatY: 4.4, hipZ: -1.6, pitch: deg(10), flex: deg(2), headPitch: deg(-6), footZ: 3.8, footX: 2.0 });
      const sh = shoulders();
      const pts = ['L', 'R'].map((s) => sh.clone().add(V3(SX[s] * 2.2, -0.9, lerp(R() + 0.4, 1.0, p))));
      bothFists(pts, { dirs: [Z, Z], palms: [medial('L'), medial('R')], poles: [V3(0.5, -0.3, -1), V3(-0.5, -0.3, -1)] });
      const chestPad = sh.clone().add(V3(0, -2.2, 1.9));
      pad(3.0, 3.4, 0.9, chestPad).rotation.x = deg(-10);
      seat(4.4, V3(0, 0, -1.4), { back: false });
      pts.forEach((q, i) => { lever(V3(q.x * 1.25, sh.y + 4.5, sh.z + 6.0), q.clone().add(V3(0, 0.85, 0))); rod(q.clone().add(V3(0, -0.85, 0)), q.clone().add(V3(0, 0.85, 0)), 0.18, MAT.rubber()); });
      box(1.2, 14, 1.2, V3(0, 7, sh.z + 6.6), MAT.frame());
    },
  },

  straight_arm_pulldown: {
    view: { az: -70, el: 8 }, concentric: 'go',
    pose(p) {
      hinge({ pitch: deg(24), drop: 0.5, flex: deg(4), headPitch: deg(-10), over: 0.6 });
      const sh = shoulders();
      const a = deg(lerp(150, 12, p)), r = ARM_LEN * 0.98 + 0.5;
      const bar = sh.clone().add(V3(0, -Math.cos(a) * r, Math.sin(a) * r * 0.95 + 0.3));
      const d = bar.clone().sub(sh).normalize();
      // Overhand: with the arms forward the palms face the floor.
      barGrip(bar, 2.4, { dir: d, palm: V3(0, -d.z, d.y), poles: [V3(0.2, -1, -0.2), V3(-0.2, -1, -0.2)] });
      handleBar(bar, X, 5.2);
      cableTower(V3(0, 20.5, sh.z + 6.5), bar);
    },
  },

  db_pullover: {
    view: { az: -60, el: 20 },
    pose(p) {
      supine({ top: 4.4, hipZ: 1.2 });
      const sh = shoulders(), { up, fwd } = chestFrame();
      const a = deg(lerp(5, 105, p)), r = ARM_LEN * 0.92 + 0.4;
      const db = sh.clone().add(fwd.clone().multiplyScalar(Math.cos(a) * r)).add(up.clone().multiplyScalar(Math.sin(a) * r));
      const d = db.clone().sub(sh).normalize();
      const pts = ['L', 'R'].map((s) => db.clone().add(V3(SX[s] * 0.45, 0, 0)));
      bothFists(pts, { dirs: [d, d], palms: [medial('L'), medial('R')], poles: [V3(0.6, 0, 0.4), V3(-0.6, 0, 0.4)], grip: 0.6 });
      dumbbell(db.clone().add(d.clone().multiplyScalar(0.5)), d, 1.05);
      flatBench(4.4, -11.5, 3.0);
    },
  },

  deadlift: {
    view: { az: -68, el: 6 }, concentric: 'go',
    pose(p) {
      // p 0: bar on the floor; 1: standing tall.
      const t = 1 - p;
      const barY = lerp(2.25, 0, 0);
      hinge({ pitch: deg(lerp(4, 58, t)), drop: lerp(0, 3.0, t), flex: deg(lerp(0, 6, t)), headPitch: deg(lerp(0, -20, t)), knees: [V3(0.3, 0, 1), V3(-0.3, 0, 1)], over: 0.7 });
      const sh = shoulders();
      const reachY = sh.y - R() - 0.6;
      const bar = V3(0, Math.max(reachY, 2.25), Math.min(sh.z + 0.25, 1.4));
      barGrip(bar, 2.6, { dir: V3(0, -1, 0.05).normalize(), palm: V3(0, 0, -1), poles: [V3(0.4, 0, -1), V3(-0.4, 0, -1)] });
      barbell(bar);
    },
  },

  back_extension: {
    view: { az: -75, el: 8 },
    pose(p) {
      // A 45° bench: hips on the pad, heels under the rollers; the trunk bends down and back up.
      const legDir = V3(0, -Math.sin(deg(45)), -Math.cos(deg(45)));
      const hipAt = V3(0, 9.0, 0.4);
      torso({ root: hipAt, pitch: deg(45) + deg(lerp(0, 62, p)), flex: deg(lerp(0, 10, p)), headPitch: deg(lerp(-10, -5, p)) });
      const h = hips();
      legs({ ankles: ['L', 'R'].map((s, i) => pWorld(B['upperleg01.' + s]).add(legDir.clone().multiplyScalar(8.4)).add(V3(0, 0, 0))), poles: [V3(0, 0.7, 0.7), V3(0, 0.7, 0.7)], feet: [footQ(0, deg(-45) + deg(10)), footQ(0, deg(-45) + deg(10))] });
      const { fwd, up } = chestFrame();
      ['L', 'R'].forEach((s) => arm(s, { wrist: shoulders().add(up.clone().multiplyScalar(-1.2)).add(fwd.clone().multiplyScalar(1.6)).add(V3(SX[s] * -0.6, 0, 0)), pole: V3(SX[s], 0, 0).add(up.clone().multiplyScalar(-0.5)), dir: V3(-SX[s], 0, 0), palm: fwd.clone().negate(), grip: 0.2 }));
      const padAt = h.clone().add(V3(0, -0.9, 1.0));
      pad(3.2, 0.9, 3.2, padAt).rotation.x = deg(-45);
      const ank = mid(pWorld(B['foot.L']), pWorld(B['foot.R']));
      roller(ank.clone().add(V3(0, 0.6, 0.45)), 3.4);
      box(1.0, 0.6, 12, V3(0, 3.9, -2.3), MAT.frame()).rotation.x = deg(-38);
      rod(V3(0, 0, -6.4), V3(0, 2.6, -6.4), 0.3, MAT.frame()); rod(V3(0, 0, 1.6), V3(0, 7.2, 1.6), 0.3, MAT.frame());
      box(4.0, 0.3, 1.0, V3(0, 0.15, 1.6), MAT.frame()); box(4.0, 0.3, 1.0, V3(0, 0.15, -6.4), MAT.frame());
    },
  },

  inverted_row: {
    view: { az: -62, el: 10 }, concentric: 'go',
    pose(p) {
      // Under a low bar, heels on the floor, body straight from heels to head; pull the chest up.
      const heel = V3(0, 0.62, 8.2);
      const D = REST_ROOT().y > 0 ? 13.6 : 13.6;
      const at = (beta) => heel.clone().add(V3(0, D * Math.sin(beta), -D * Math.cos(beta)));
      const top = deg(34), bar = at(top).add(V3(0, 1.85, 0.35));
      let lo = deg(5), hi = top;
      for (let i = 0; i < 30; i++) { const m = (lo + hi) / 2; if (at(m).distanceTo(bar) > R() + 0.75) lo = m; else hi = m; }
      const beta = lerp(lo, top, p);
      placeShoulders(at(beta), { pitch: -(Math.PI / 2 - beta), flex: deg(-2), headPitch: deg(10) });
      legs({ ankles: [heel.clone().add(V3(0.9, 0, 0)), heel.clone().add(V3(-0.9, 0, 0))], poles: [Y, Y], feet: [footQ(0, deg(-70)), footQ(0, deg(-70))] });
      barGrip(bar, 2.9, { dir: V3(0, 1, -0.2).normalize(), palm: Z, poles: [V3(0.7, -0.6, 0.3), V3(-0.7, -0.6, 0.3)] });
      part(new THREE.CylinderGeometry(0.17, 0.17, 12, 16), MAT.chrome(), bar, { z: Math.PI / 2 });
      [-6, 6].forEach((x) => { box(0.6, bar.y + 0.5, 0.6, V3(x, (bar.y + 0.5) / 2, bar.z), MAT.frame()); box(0.8, 0.3, 4, V3(x, 0.15, bar.z), MAT.frame()); });
    },
  },

  band_row: {
    view: { az: -65, el: 8 }, concentric: 'go',
    pose(p) {
      stand({ drop: 0.5, pitch: deg(8), headPitch: deg(-4), width: 0.5 });
      const sh = shoulders();
      const pts = ['L', 'R'].map((s) => sh.clone().add(V3(SX[s] * lerp(1.6, 2.3, p), -lerp(1.0, 2.3, p), lerp(R() + 0.3, 0.6, p))));
      bothFists(pts, { dirs: [Z, Z], palms: [medial('L'), medial('R')], poles: [V3(0.4, -0.4, -1), V3(-0.4, -0.4, -1)] });
      const anchor = V3(0, sh.y - 1.6, sh.z + 11);
      pts.forEach((q) => band([q, q.clone().lerp(anchor, 0.5), anchor]));
      box(1.2, 18, 1.2, V3(0, 9, anchor.z + 0.6), MAT.frame());
    },
  },

  superman: {
    view: { az: -70, el: 14 },
    pose(p) {
      // Face down on a mat, arms forward; lift the chest, arms and legs together.
      torso({ root: V3(0, 1.05 + 0.25 * p, -2.5), pitch: deg(90), flex: deg(lerp(0, -22, p)), headPitch: deg(lerp(-6, -16, p)) });
      const h = hips();
      legs({ ankles: ['L', 'R'].map((s) => h.clone().add(V3(SX[s] * 0.7, lerp(-1.0, 0.2, p), -LEG_LEN_() * 0.99))), poles: [V3(0, -1, 0), V3(0, -1, 0)], feet: [footQ(0, deg(80)), footQ(0, deg(80))] });
      const sh = shoulders(), { up } = chestFrame();
      ['L', 'R'].forEach((s) => arm(s, { wrist: sh.clone().add(up.clone().multiplyScalar(R())).add(V3(SX[s] * 1.4, lerp(-0.3, 0.6, p), 0)), pole: V3(SX[s] * 0.3, 1, 0), dir: up, palm: V3(0, -1, 0), grip: 0.1 }));
      floorMat(V3(0, 0, -2.5), 6.5, 21);
    },
  },
};

function lerpTo(a, b, t) { return a.clone().lerp(b, t); }
function mid(a, b) { return a.clone().add(b).multiplyScalar(0.5); }
function LEG_LEN_() { return pWorld(B['upperleg01.L']).distanceTo(pWorld(B['lowerleg01.L'])) + pWorld(B['lowerleg01.L']).distanceTo(pWorld(B['foot.L'])); }
