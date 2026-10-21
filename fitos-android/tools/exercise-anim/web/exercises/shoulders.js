// Shoulder exercises.
import { V3, X, Y, Z, deg, lerp, arm, hang, sit, stand, hinge, shoulders, hips, barGrip, bothFists, medial, SX, ARM_LEN, chestFrame, legs, footQ, torso, REST_ROOT, pWorld, B, placeShoulders, setWorld, qWorld, qAxis, mid } from './common.js';
import { THREE } from '../figure.js';
import { barbell, dumbbell, uprightBench, cableTower, dHandle, rope, seat, lever, stack, pad, rod, band, MAT, box, part, handleBar } from '../props.js';

const R = () => ARM_LEN * 0.97;
const SIDES = ['L', 'R'];
const ELBOWS_FWD_DOWN = [V3(0.4, -1, 0.5), V3(-0.4, -1, 0.5)];

/** Seated with the back on an upright bench. */
const seatUp = () => sit({ seatY: 4.6, hipZ: -0.7, pitch: deg(-6), flex: deg(-3), headPitch: deg(4), footZ: 4.4, footX: 2.3 });

/** Dumbbells from beside the ears to overhead; palms forward. */
function dbOverhead(p, { arnold = false } = {}) {
  const pts = SIDES.map((s) => {
    const sh = pWorld(B['upperarm01.' + s]);
    return arnold
      ? sh.clone().add(V3(SX[s] * lerp(0.2, 0.9, p), lerp(0.7, R() + 0.35, p), lerp(1.3, 0.3, p)))
      : sh.clone().add(V3(SX[s] * lerp(1.7, 0.75, p), lerp(0.5, R() + 0.35, p), lerp(0.6, 0.3, p)));
  });
  const palms = SIDES.map((s) => {
    if (!arnold) return Z.clone();
    const f = Math.PI * Math.min(1, p * 1.3);
    return V3(0, 0, -Math.cos(f)).add(medial(s).multiplyScalar(Math.sin(f)));
  });
  const poles = SIDES.map((s) => (arnold ? V3(SX[s] * lerp(0.1, 1, p), -0.6, lerp(1, -0.2, p)) : V3(SX[s], -0.6, -0.15)));
  bothFists(pts, { dirs: [Y, Y], palms, poles });
  pts.forEach((q) => dumbbell(q, X));
}

/** Arms rising out to the sides (or forward) through `a`: the fist and how the hand is turned. */
function raise(s, a, { forward = false, lean = V3(0, 0, 0.15) } = {}) {
  const sh = pWorld(B['upperarm01.' + s]), r = ARM_LEN * 0.95 + 0.4;
  if (forward) {
    const fist = sh.clone().add(V3(SX[s] * 0.6, -Math.cos(a) * r, Math.sin(a) * r));
    return { fist, dir: fist.clone().sub(sh).normalize(), palm: V3(0, -Math.sin(a), -Math.cos(a)) };
  }
  const fist = sh.clone().add(V3(SX[s] * (0.5 + Math.sin(a) * r), -Math.cos(a) * r, 0)).add(lean.clone().multiplyScalar(Math.sin(a) * 4));
  return { fist, dir: fist.clone().sub(sh).normalize(), palm: V3(-SX[s] * Math.cos(a), -Math.sin(a), 0) };
}

/** Pulling toward the face, elbows high and out: from arms forward to hands by the ears. */
function facePull(p) {
  stand({ drop: 0.4, pitch: deg(-4), headPitch: deg(2), width: 0.5 });
  const sh = shoulders();
  const pts = SIDES.map((s) => sh.clone().add(V3(SX[s] * lerp(0.6, 2.4, p), lerp(0.2, 1.6, p), lerp(R() + 0.2, 0.9, p))));
  const dirs = SIDES.map(() => V3(0, lerp(0, 1, p), lerp(1, 0.3, p)).normalize());
  const palms = SIDES.map((s) => medial(s).multiplyScalar(1 - p).add(V3(0, 0, p)).normalize());
  bothFists(pts, { dirs, palms, poles: [V3(1, 0.5, -0.4), V3(-1, 0.5, -0.4)] });
  return { sh, pts };
}

export default {
  overhead_press: {
    view: { az: -48, el: 6 }, concentric: 'go',
    pose(p) {
      stand({ width: 0.55, headPitch: deg(8 * Math.sin(Math.PI * p)), pitch: deg(-2) });
      const sh = shoulders();
      const bar = V3(0, lerp(sh.y + 0.25, sh.y + R() + 0.5, p), lerp(sh.z + 1.4, sh.z + 0.15, p));
      barGrip(bar, 2.65, { dir: Y, palm: Z, poles: SIDES.map((s) => V3(SX[s] * lerp(0.4, 1, p), -1, lerp(0.7, -0.2, p))) });
      barbell(bar);
    },
  },

  seated_db_press: { view: { az: -36, el: 8 }, concentric: 'go', pose(p) { seatUp(); dbOverhead(p); uprightBench(); } },
  arnold_press: { view: { az: -30, el: 8 }, concentric: 'go', pose(p) { seatUp(); dbOverhead(p, { arnold: true }); uprightBench(); } },

  machine_shoulder_press: {
    view: { az: -50, el: 8 }, concentric: 'go',
    pose(p) {
      seatUp();
      const pts = SIDES.map((s) => pWorld(B['upperarm01.' + s]).add(V3(SX[s] * lerp(1.8, 1.0, p), lerp(0.5, R() + 0.2, p), 0.9)));
      bothFists(pts, { dirs: [Y, Y], palms: [medial('L'), medial('R')], poles: [V3(1, -0.6, 0), V3(-1, -0.6, 0)] });
      uprightBench();
      pts.forEach((q) => { rod(q.clone().add(V3(0, 0, -0.8)), q.clone().add(V3(0, 0, 0.8)), 0.18, MAT.rubber()); lever(V3(q.x * 1.3, 4.2, -4.6), q.clone().add(V3(0, 0, -0.8))); });
      stack(-6.4, 0, 18);
    },
  },

  lateral_raise: {
    view: { az: -20, el: 6 }, concentric: 'go',
    pose(p) {
      stand({ width: 0.3, pitch: deg(4), headPitch: deg(-2) });
      const a = deg(lerp(6, 86, p));
      SIDES.forEach((s) => { const r = raise(s, a); arm(s, { fist: r.fist, pole: V3(SX[s] * 0.2, 0.2, -1), dir: r.dir, palm: r.palm }); dumbbell(r.fist, Z); });
    },
  },

  cable_lateral_raise: {
    view: { az: -20, el: 6 }, concentric: 'go',
    pose(p) {
      // The right arm raises; the cable comes from a low pulley on the left.
      stand({ width: 0.4, side: deg(4), headPitch: deg(-2) });
      const a = deg(lerp(-14, 84, p));
      const sh = pWorld(B['upperarm01.R']), r = ARM_LEN * 0.95 + 0.4;
      const fist = sh.clone().add(V3(-(0.5 + Math.sin(a) * r), -Math.cos(a) * r, 0.5));
      arm('R', { fist, pole: V3(-0.2, 0.2, -1), dir: fist.clone().sub(sh).normalize(), palm: V3(Math.cos(a), -Math.sin(a), 0) });
      const shL = pWorld(B['upperarm01.L']);
      arm('L', { fist: V3(4.6, shL.y - 1.4, shL.z + 0.2), pole: V3(0.3, -1, -0.3), dir: V3(1, 0, 0), palm: V3(0, 0, -1), grip: 0.8 });
      const pulley = V3(4.9, 1.2, sh.z + 0.5);
      cableTower(pulley, fist, { column: false });
      box(1.2, 22, 1.2, V3(5.6, 11, sh.z + 0.5), MAT.frame());
      dHandle(fist, Z, pulley.clone().sub(fist).normalize());
    },
  },

  front_raise: {
    view: { az: -62, el: 6 }, concentric: 'go',
    pose(p) {
      stand({ width: 0.3, pitch: deg(-2) });
      const a = deg(lerp(6, 88, p));
      SIDES.forEach((s) => { const r = raise(s, a, { forward: true }); arm(s, { fist: r.fist, pole: V3(SX[s] * 0.6, -0.4, -0.6), dir: r.dir, palm: r.palm }); dumbbell(r.fist, X); });
    },
  },

  rear_delt_fly: {
    view: { az: -40, el: 16 }, concentric: 'go',
    pose(p) {
      hinge({ pitch: deg(70), drop: 1.0, flex: deg(4), headPitch: deg(-30), knees: [V3(0.2, 0, 1), V3(-0.2, 0, 1)], over: 1.0 });
      const a = deg(lerp(8, 82, p));
      SIDES.forEach((s) => { const r = raise(s, a, { lean: V3(0, 0, 0.05) }); arm(s, { fist: r.fist, pole: V3(SX[s] * 0.2, 0.6, -0.8), dir: r.dir, palm: r.palm }); dumbbell(r.fist, Z); });
    },
  },

  reverse_pec_deck: {
    view: { az: -150, el: 14 }, concentric: 'go',
    pose(p) {
      // Facing the machine, chest on the pad; arms open to the sides at shoulder height.
      sit({ seatY: 5.0, hipZ: -1.0, pitch: deg(8), headPitch: deg(-4), footZ: 3.6, footX: 2.0 });
      const sh = shoulders(), r = ARM_LEN * 0.92 + 0.4;
      const a = deg(lerp(12, 86, p));
      const pts = SIDES.map((s) => sh.clone().add(V3(SX[s] * (0.6 + r * Math.sin(a)), -0.3, 0.4 + r * Math.cos(a))));
      bothFists(pts, {
        dirs: SIDES.map((s) => V3(SX[s] * Math.sin(a), 0, Math.cos(a))),
        palms: SIDES.map((s) => V3(-SX[s] * Math.cos(a), 0, Math.sin(a)).multiplyScalar(-1).add(V3(0, -0.2, 0)).normalize()),
        poles: [V3(0.2, 0.6, -0.8), V3(-0.2, 0.6, -0.8)],
      });
      pad(3.0, 4.2, 0.9, sh.clone().add(V3(0, -2.4, 1.7))).rotation.x = deg(-8);
      seat(5.0, V3(0, 0, -0.6), { back: false });
      pts.forEach((q, i) => { lever(V3(SX[SIDES[i]] * 1.2, sh.y + 4.0, sh.z + 3.2), q.clone().add(V3(0, 0.9, 0))); rod(q.clone().add(V3(0, -0.9, 0)), q.clone().add(V3(0, 0.9, 0)), 0.18, MAT.rubber()); });
      box(1.2, 16, 1.2, V3(0, 8, sh.z + 6.0), MAT.frame());
    },
  },

  face_pull: {
    view: { az: -58, el: 8 }, concentric: 'go',
    pose(p) {
      const { sh, pts } = facePull(p);
      const anchor = V3(0, sh.y + 0.6, sh.z + 9.5);
      const top = mid(pts[0], pts[1]).lerp(anchor, 0.25);
      rope(top, pts);
      cableTower(anchor, top);
    },
  },

  upright_row: {
    view: { az: -40, el: 6 }, concentric: 'go',
    pose(p) {
      stand({ width: 0.35 });
      const sh = shoulders();
      const bar = V3(0, lerp(sh.y - R() - 0.55, sh.y - 0.7, p), sh.z + lerp(0.9, 1.3, p));
      barGrip(bar, 1.6, { dir: V3(0, -1, 0.1).normalize(), palm: V3(0, 0, -1), poles: [V3(1, lerp(-0.2, 1, p), -0.3), V3(-1, lerp(-0.2, 1, p), -0.3)] });
      barbell(bar, { light: true });
    },
  },

  pike_push_up: {
    view: { az: -72, el: 10 },
    pose(p) {
      // Hips high; the head drops toward the floor between the hands.
      const H = V3(0, 10.2, -3.6);
      torso({ root: H, pitch: deg(lerp(124, 146, p)), flex: deg(4), headPitch: deg(-10) });
      const shTop = (() => { const r = B.root.position.clone(); return null; })();
      SIDES.forEach((s) => {
        setWorld(B['foot.' + s], qWorld(B['lowerleg02.' + s]).multiply(qAxis(X, deg(-8))));
      });
      legs({ ankles: [V3(1.1, 0.9, -11.6), V3(-1.1, 0.9, -11.6)], poles: [V3(0, 1, 0), V3(0, 1, 0)], feet: [footQ(0, deg(60)), footQ(0, deg(60))], toes: ['flat', 'flat'] });
      SIDES.forEach((s) => arm(s, { wrist: V3(SX[s] * 2.5, 0.48, 1.3), pole: V3(SX[s] * 0.6, 0.3, -1), dir: V3(SX[s] * 0.15, 0, 1).normalize(), palm: V3(0, -1, 0), grip: 0.05 }));
    },
  },

  band_lateral_raise: {
    view: { az: -20, el: 6 }, concentric: 'go',
    pose(p) {
      stand({ width: 0.3, pitch: deg(4) });
      const a = deg(lerp(6, 84, p));
      SIDES.forEach((s) => {
        const r = raise(s, a); arm(s, { fist: r.fist, pole: V3(SX[s] * 0.2, 0.2, -1), dir: r.dir, palm: r.palm });
        const foot = pWorld(B['foot.' + s]).add(V3(-SX[s] * 0.2, -0.6, 0.5));
        band([foot, foot.clone().lerp(r.fist, 0.5).add(V3(SX[s] * 0.2, 0, 0)), r.fist]);
      });
    },
  },

  band_shoulder_press: {
    view: { az: -36, el: 6 }, concentric: 'go',
    pose(p) {
      stand({ width: 0.4, headPitch: deg(4) });
      const pts = SIDES.map((s) => pWorld(B['upperarm01.' + s]).add(V3(SX[s] * lerp(1.6, 0.7, p), lerp(0.4, R() + 0.3, p), lerp(0.6, 0.3, p))));
      bothFists(pts, { dirs: [Y, Y], palms: [Z, Z], poles: [V3(1, -0.6, -0.15), V3(-1, -0.6, -0.15)] });
      SIDES.forEach((s, i) => { const foot = pWorld(B['foot.' + s]).add(V3(0, -0.6, 0.6)); band([foot, foot.clone().lerp(pts[i], 0.5).add(V3(SX[s] * 0.6, 0, 0.3)), pts[i]]); });
    },
  },

  band_pull_apart: {
    view: { az: -35, el: 8 }, concentric: 'go',
    pose(p) {
      stand({ width: 0.35, pitch: deg(-2) });
      const sh = shoulders(), r = ARM_LEN * 0.95 + 0.4;
      const a = deg(lerp(12, 84, p));
      const pts = SIDES.map((s) => sh.clone().add(V3(SX[s] * (0.5 + r * Math.sin(a)), -0.35, 0.3 + r * Math.cos(a))));
      bothFists(pts, { dirs: SIDES.map((s) => V3(SX[s] * Math.sin(a), 0, Math.cos(a))), palms: [V3(0, -1, 0), V3(0, -1, 0)], poles: [V3(0.2, -1, -0.3), V3(-0.2, -1, -0.3)] });
      band([pts[0], mid(pts[0], pts[1]).add(V3(0, -0.05 * (1 - p), 0)), pts[1]]);
    },
  },

  band_face_pull: {
    view: { az: -58, el: 8 }, concentric: 'go',
    pose(p) {
      const { sh, pts } = facePull(p);
      const anchor = V3(0, sh.y + 0.6, sh.z + 10.5);
      pts.forEach((q) => band([q, q.clone().lerp(anchor, 0.5), anchor]));
      box(1.2, 20, 1.2, V3(0, 10, anchor.z + 0.6), MAT.frame());
    },
  },

  barbell_shrug: {
    view: { az: -45, el: 6 }, concentric: 'go',
    pose(p) {
      torso({ root: REST_ROOT().add(V3(0, 0, -0.2)), pitch: deg(3), shrug: deg(lerp(0, 14, p)), retract: deg(lerp(0, 4, p)), headPitch: deg(-2) });
      legs({ ankles: [V3(2.3, 0.72, 0.15), V3(-2.3, 0.72, 0.15)], poles: [V3(0.3, 0, 1), V3(-0.3, 0, 1)] });
      const sh = shoulders();
      const bar = V3(0, sh.y - R() - 0.6, sh.z + 1.1);
      barGrip(bar, 2.5, { dir: V3(0, -1, 0.05).normalize(), palm: V3(0, 0, -1), poles: [V3(0.4, 0, -1), V3(-0.4, 0, -1)] });
      barbell(bar);
    },
  },

  dumbbell_shrug: {
    view: { az: -40, el: 6 }, concentric: 'go',
    pose(p) {
      torso({ root: REST_ROOT().add(V3(0, 0, -0.2)), shrug: deg(lerp(0, 14, p)), retract: deg(lerp(0, 4, p)), headPitch: deg(-2) });
      legs({ ankles: [V3(2.1, 0.72, 0.15), V3(-2.1, 0.72, 0.15)], poles: [V3(0.3, 0, 1), V3(-0.3, 0, 1)] });
      SIDES.forEach((s) => {
        const sh = pWorld(B['upperarm01.' + s]);
        const fist = sh.clone().add(V3(SX[s] * 0.55, -R() - 0.55, 0.25));
        arm(s, { fist, pole: V3(SX[s] * 0.3, 0, -1), dir: V3(0, -1, 0), palm: medial(s) });
        dumbbell(fist, Z);
      });
    },
  },
};
