// Leg and hip exercises.
import { V3, X, Y, Z, deg, lerp, arm, hang, sit, stand, hinge, balance, shoulders, hips, barGrip, bothFists, medial, SX, ARM_LEN, chestFrame, legs, footQ, torso, REST_ROOT, pWorld, B, mid, placeShoulders, KNEES_FWD, stance } from './common.js';
import { THREE, smooth, easeInOut, LEG_LEN } from '../figure.js';
import { barbell, dumbbell, kettlebell, flatBench, step, wall, floorMat, pad, rod, roller, box, lever, MAT, part, group } from '../props.js';

const SIDES = ['L', 'R'];
const R = () => ARM_LEN * 0.97;
const MIDFOOT = 0.75;
const THIGH = () => pWorld(B['upperleg01.L']).distanceTo(pWorld(B['lowerleg01.L']));
const SHIN = () => pWorld(B['lowerleg01.L']).distanceTo(pWorld(B['foot.L']));

/** A squat: hips and lean go together so `measure()` stays over the middle of the foot. */
function squat(p, { lean = 30, depth = 3.75, measure, width = 0.35, flex = 14 }) {
  const drop = depth * p, pitch = deg(lerp(2, lean, p));
  hinge({ pitch, drop, flex: deg(flex * p), headPitch: -pitch * 0.7 - deg(flex * p) * 0.5, width, knees: [V3(0.45, 0, 1), V3(-0.45, 0, 1)], over: MIDFOOT, measure, feet: [footQ(deg(14)), footQ(deg(-14))] });
}
const backBar = () => { const { q } = chestFrame(); return pWorld(B.neck01).add(V3(0, -0.35, -0.95).applyQuaternion(q)); };
const frontBar = () => { const { up, fwd } = chestFrame(); return shoulders().add(fwd.clone().multiplyScalar(1.25)).add(up.clone().multiplyScalar(0.15)); };

/** Romanian deadlift: hips back, knees soft, the weight hanging under the shoulders. */
function rdl(p) {
  hinge({ pitch: deg(lerp(2, 70, p)), drop: lerp(0, 0.75, p), flex: deg(lerp(0, 6, p)), headPitch: -deg(lerp(2, 70, p)) * 0.3, width: 0.1, knees: [V3(0.15, 0, 1), V3(-0.15, 0, 1)], over: MIDFOOT + 0.4 });
  return shoulders();
}

/** Split stance: front foot (side `front`) forward, back knee dropping toward the floor. */
function split(p, front, { stride = 6.2, rearOnBench = false, lean = 8 } = {}) {
  const f = front === 'L' ? 0 : 1, r = 1 - f;
  const ankles = [];
  ankles[f] = V3(SX[front] * 1.0, 0.72, stride * 0.55);
  ankles[r] = rearOnBench ? V3(-SX[front] * 1.0, 5.0, -5.6) : V3(-SX[front] * 1.0, 0.72, -stride * 0.45);
  const drop = lerp(rearOnBench ? 0.6 : 0.4, rearOnBench ? 4.0 : 3.9, p);
  torso({ root: REST_ROOT().add(V3(0, -drop, rearOnBench ? -0.4 : 0.2)), pitch: deg(lean * (0.4 + p)), headPitch: -deg(lean * (0.4 + p)) });
  const feet = [];
  feet[f] = footQ(SX[front] * deg(6));
  feet[r] = rearOnBench ? footQ(0, deg(75)) : footQ(-SX[front] * deg(6), deg(lerp(18, 40, p)));
  const toes = []; toes[f] = null; toes[r] = rearOnBench ? null : 'flat';
  legs({ ankles, poles: [V3(0.2, 0, 1), V3(-0.2, 0, 1)], feet, toes });
}
const handsOnHips = () => SIDES.forEach((s) => {
  const h = pWorld(B['upperleg01.' + s]), { up, fwd, left } = chestFrame();
  arm(s, { wrist: h.clone().add(left.clone().multiplyScalar(SX[s] * 1.3)).add(up.clone().multiplyScalar(1.6)).add(fwd.clone().multiplyScalar(-0.2)), pole: left.clone().multiplyScalar(SX[s]).add(fwd.clone().multiplyScalar(-0.5)), dir: up.clone().negate().add(fwd.clone().multiplyScalar(0.6)).normalize(), palm: left.clone().multiplyScalar(-SX[s]), grip: 0.2 });
});
const dbsHanging = () => SIDES.forEach((s) => { const o = hang(s, { forward: 0.15, out: 0.35, bend: 0.99 }); o && dumbbell(o.fist ?? pWorld(B['wrist.' + s]), Z); });

/** Supine bridge: shoulders resting at C, hips rising; `beta` is the slope from hips to shoulders. */
function bridge(C, beta, footZ, { onBench = false } = {}) {
  const D = 4.6;
  const pitch = -(Math.PI / 2 - beta);
  placeShoulders(C, { pitch, flex: deg(-2), headPitch: deg(onBench ? 20 : 4) });
  legs({ ankles: [V3(1.9, 0.72, footZ), V3(-1.9, 0.72, footZ)], poles: [V3(0.25, 1, 0.4), V3(-0.25, 1, 0.4)], feet: [footQ(deg(10)), footQ(deg(-10))] });
}

/** Calf raise: the balls of the feet stay on `ballY`; the heels rise by angle a. */
function calfFeet(a, ballY, { knees = KNEES_FWD, rootLift = true } = {}) {
  const toBall = 1.35;
  const ankleY = ballY + 0.62 + toBall * Math.sin(a);
  return { ankles: [V3(1.9, ankleY, 0.15 - toBall * (1 - Math.cos(a))), V3(-1.9, ankleY, 0.15 - toBall * (1 - Math.cos(a)))], feet: [footQ(deg(6), a), footQ(deg(-6), a)], toes: ['flat', 'flat'], poles: knees };
}

export default {
  back_squat: {
    view: { az: -52, el: 8 },
    pose(p) {
      squat(p, { lean: 30, measure: backBar });
      const bar = backBar(), { q } = chestFrame();
      ['L', 'R'].forEach((s, i) => arm(s, { fist: bar.clone().add(V3(SX[s] * 3.35, 0, 0)), pole: V3(SX[s] * 0.3, -1, -0.7), dir: V3(0, 1, 0.15).applyQuaternion(q).normalize(), palm: Z.clone().applyQuaternion(q) }));
      barbell(bar);
    },
  },
  front_squat: {
    view: { az: -56, el: 8 },
    pose(p) {
      squat(p, { lean: 16, measure: frontBar, flex: 4 });
      const bar = frontBar(), { up, fwd } = chestFrame();
      // The bar on the front of the shoulders, elbows high, fingertips under it.
      ['L', 'R'].forEach((s) => arm(s, { fist: bar.clone().add(V3(SX[s] * 2.5, 0.05, -0.2)), pole: fwd.clone().add(V3(SX[s] * 0.3, -0.2, 0)), dir: up.clone().negate().add(fwd.clone().multiplyScalar(-0.9)).normalize().negate(), palm: up.clone(), grip: 0.5 }));
      barbell(bar);
    },
  },
  goblet_squat: {
    view: { az: -50, el: 8 },
    pose(p) {
      const bell = () => { const { up, fwd } = chestFrame(); return shoulders().add(fwd.clone().multiplyScalar(1.9)).add(up.clone().multiplyScalar(-1.7)); };
      squat(p, { lean: 18, measure: bell, flex: 4, width: 0.8 });
      const db = bell(), { up } = chestFrame();
      bothFists(SIDES.map((s) => db.clone().add(V3(SX[s] * 0.42, -0.1, 0))), { dirs: [up, up], palms: [medial('L'), medial('R')], poles: [V3(0.3, -1, 0.2), V3(-0.3, -1, 0.2)], grip: 0.7 });
      dumbbell(db.clone().add(up.clone().multiplyScalar(0.2)), up, 1.05);
    },
  },
  bodyweight_squat: {
    view: { az: -52, el: 8 },
    pose(p) {
      squat(p, { lean: 34, measure: shoulders, flex: 10, width: 0.6 });
      // The arms reach forward for balance on the way down.
      const { fwd } = chestFrame();
      SIDES.forEach((s) => {
        const sh = pWorld(B['upperarm01.' + s]), a = deg(lerp(5, 85, p));
        const flat = V3(fwd.x, 0, fwd.z).normalize();
        arm(s, { wrist: sh.clone().add(V3(SX[s] * 0.25, -Math.cos(a) * (ARM_LEN * 0.95), 0)).add(flat.clone().multiplyScalar(Math.sin(a) * ARM_LEN * 0.95)), pole: V3(SX[s] * 0.3, -0.5, -1), dir: V3(0, -Math.cos(a), Math.sin(a)).normalize(), palm: V3(0, -Math.sin(a), -Math.cos(a)).add(medial(s).multiplyScalar(0.6)).normalize(), grip: 0.2 });
      });
    },
  },

  leg_press: {
    view: { az: -70, el: 14 },
    pose(p) {
      const r = V3(0, Math.sin(deg(45)), Math.cos(deg(45)));
      const H = V3(0, 4.9, -1.2);
      torso({ root: H, pitch: deg(-52), flex: deg(-2), headPitch: deg(14) });
      const hp = hips();
      const L = lerp(LEG_LEN * 0.94, 4.8, p);
      const ankles = SIDES.map((s) => pWorld(B['upperleg01.' + s]).add(V3(SX[s] * 0.7, 0, 0)).add(r.clone().multiplyScalar(L)));
      legs({ ankles, poles: [V3(0.4, 0.7, -0.6), V3(-0.4, 0.7, -0.6)], feet: [footQ(deg(10), deg(-135)), footQ(deg(-10), deg(-135))] });
      SIDES.forEach((s) => arm(s, { wrist: pWorld(B['upperleg01.' + s]).add(V3(SX[s] * 2.2, -0.3, -0.6)), pole: V3(SX[s], 0, -0.3), dir: Z, palm: medial(s), grip: 0.9 }));
      // Seat and back, the sled on its 45° rails, the platform under the feet.
      // The back pad lies along the spine, behind it.
      const spine = V3(0, Math.cos(deg(52)), -Math.sin(deg(52))), behind = V3(0, -Math.sin(deg(52)), -Math.cos(deg(52)));
      pad(3.2, 0.8, 3.0, V3(0, 3.9, -1.4)).rotation.x = deg(-10);
      pad(3.0, 0.8, 7.0, H.clone().add(spine.clone().multiplyScalar(3.0)).add(behind.clone().multiplyScalar(1.35))).rotation.x = deg(38);
      box(1.2, 3.2, 8, V3(0, 1.6, -2.6), MAT.frame());
      const plateAt = mid(ankles[0], ankles[1]).add(r.clone().multiplyScalar(0.95));
      const plat = box(6.0, 5.0, 0.4, plateAt, MAT.dark()); plat.quaternion.setFromUnitVectors(Z, r);
      SIDES.forEach((s) => { const base = V3(SX[s] * 3.4, 1.0, 2.0); rod(base, base.clone().add(r.clone().multiplyScalar(14)), 0.25, MAT.frame()); rod(plateAt.clone().add(V3(SX[s] * 3.0, 0, 0)), V3(SX[s] * 3.4, plateAt.y, plateAt.z), 0.2, MAT.dark()); });
      box(8, 0.4, 14, V3(0, 0.2, 5.5), MAT.frame());
    },
  },

  hack_squat: {
    view: { az: -72, el: 8 },
    pose(p) {
      const u = V3(0, Math.sin(deg(55)), -Math.cos(deg(55)));
      const s = lerp(0, -4.2, p);
      torso({ root: REST_ROOT().add(V3(0, -0.6, -0.9)).add(u.clone().multiplyScalar(s)), pitch: deg(-24), flex: deg(-2), headPitch: deg(14) });
      legs({ ankles: [V3(1.7, 1.4, 1.9), V3(-1.7, 1.4, 1.9)], poles: [V3(0.4, 0, 1), V3(-0.4, 0, 1)], feet: [footQ(deg(10), deg(-14)), footQ(deg(-10), deg(-14))] });
      SIDES.forEach((s) => { const sh = pWorld(B['upperarm01.' + s]); arm(s, { fist: sh.clone().add(V3(SX[s] * 1.7, 0.9, 0.2)), pole: V3(SX[s], -1, -0.2), dir: Y, palm: Z, grip: 0.9 }); });
      const { up } = chestFrame(), sh = shoulders();
      const backPad = pad(3.0, 0.8, 9.0, sh.clone().add(up.clone().multiplyScalar(-3.6)).add(V3(0, 0, -1.25))); backPad.rotation.x = Math.PI / 2 - deg(24);
      SIDES.forEach((s) => pad(1.4, 0.8, 1.8, pWorld(B['upperarm01.' + s]).add(V3(-SX[s] * 0.2, 0.75, -0.2))));
      SIDES.forEach((s) => rod(V3(SX[s] * 2.4, 0.6, 1.0), V3(SX[s] * 2.4, 0.6, 1.0).add(u.clone().multiplyScalar(18)), 0.25, MAT.frame()));
      const plat = box(6.0, 0.4, 4.4, V3(0, 0.75, 2.3), MAT.dark()); plat.rotation.x = deg(-14);
    },
  },

  leg_extension: {
    view: { az: -66, el: 8 }, concentric: 'go',
    pose(p) {
      sit({ seatY: 5.2, hipZ: -1.6, pitch: deg(-6), headPitch: deg(4), footZ: 2.0 });
      const a = deg(lerp(6, 84, p));
      const ankles = SIDES.map((s) => { const k = pWorld(B['upperleg01.' + s]).add(V3(SX[s] * 0.2, -0.45, THIGH())); return k.add(V3(0, -Math.cos(a), Math.sin(a)).multiplyScalar(SHIN())); });
      legs({ ankles, poles: [V3(0.1, 1, 0.3), V3(-0.1, 1, 0.3)], feet: [footQ(deg(6), -a + deg(10)), footQ(deg(-6), -a + deg(10))] });
      SIDES.forEach((s) => arm(s, { wrist: pWorld(B['upperleg01.' + s]).add(V3(SX[s] * 2.1, 0.0, -0.4)), pole: V3(SX[s], 0, -0.2), dir: Z, palm: medial(s), grip: 0.9 }));
      const knee = pWorld(B['lowerleg01.L']), ank = mid(ankles[0], ankles[1]);
      const front = V3(0, Math.sin(a), Math.cos(a));
      roller(ank.clone().add(V3(0, -Math.cos(a), Math.sin(a)).multiplyScalar(-0.4)).add(front.clone().multiplyScalar(0.75)), 3.6);
      lever(V3(3.0, knee.y, knee.z), V3(3.0, 0, 0).add(ank.clone().add(front.clone().multiplyScalar(0.75))).setX(3.0));
      const seatG = group();
      pad(3.2, 0.8, 4.0, V3(0, 4.8, 0.2), seatG); pad(3.0, 0.8, 7.0, V3(0, 8.3, -2.6), seatG).rotation.x = -Math.PI / 2 + deg(6);
      box(1.2, 4.4, 1.2, V3(0, 2.2, -0.4), MAT.frame()); box(5, 0.3, 2, V3(0, 0.15, -0.4), MAT.frame());
    },
  },

  bulgarian_split_squat: {
    view: { az: -80, el: 8 },
    pose(p) { split(p, 'R', { rearOnBench: true, lean: 10 }); dbsHanging(); flatBench(4.4, -4, 4, V3(0, 0, -6.4), Math.PI / 2); },
  },
  lunge: {
    view: { az: -80, el: 8 }, mode: 'alt',
    pose(p, side = 'L') {
      const f = smooth(0, 0.5, p), d = smooth(0.3, 1, p);
      // Step out, then sink.
      split(d, side, { stride: lerp(0.6, 6.4, f) });
      handsOnHips();
    },
  },
  walking_lunge: {
    view: { az: -80, el: 8 }, mode: 'alt',
    pose(p, side = 'L') {
      const f = smooth(0, 0.5, p), d = smooth(0.3, 1, p);
      split(d, side, { stride: lerp(0.6, 6.6, f) });
      dbsHanging();
    },
  },
  step_up: {
    view: { az: -75, el: 8 }, concentric: 'go',
    pose(p) {
      // Left foot on the box; drive up until standing on it, the right knee coming up.
      const boxH = 4.6;
      const up = smooth(0, 0.85, p);
      torso({ root: REST_ROOT().add(V3(0, lerp(-0.8, boxH - 0.1, up), lerp(-1.2, 1.4, up))), pitch: deg(lerp(18, 2, up)), headPitch: deg(-6) });
      const rf = smooth(0.55, 1, p);
      legs({
        ankles: [V3(1.0, boxH + 0.72, 1.6), V3(-1.0, lerp(0.72, boxH + 3.0, rf), lerp(-1.5, 2.2, rf))],
        poles: [V3(0.2, 0, 1), V3(-0.2, 0, 1)], feet: [footQ(deg(6)), footQ(deg(-6), deg(lerp(0, 25, rf)))],
      });
      dbsHanging();
      step(boxH, V3(0, 0, 1.8), 6, 5);
    },
  },
  wall_sit: {
    view: { az: -75, el: 8 }, mode: 'hold',
    pose() {
      torso({ root: V3(0, 4.9, -0.95), pitch: deg(-2), headPitch: deg(2) });
      legs({ ankles: [V3(1.6, 0.72, 3.6), V3(-1.6, 0.72, 3.6)], poles: [V3(0.3, 1, 0.3), V3(-0.3, 1, 0.3)] });
      SIDES.forEach((s) => { const k = pWorld(B['lowerleg01.' + s]); arm(s, { wrist: k.clone().add(V3(SX[s] * 0.3, 0.75, -0.9)), pole: V3(SX[s], -0.3, -0.5), dir: V3(0, -0.4, 1).normalize(), palm: V3(0, -1, 0), grip: 0.2 }); });
      wall(-2.3, 20, 12);
    },
  },

  romanian_deadlift: {
    view: { az: -68, el: 6 },
    pose(p) {
      const sh = rdl(p);
      const bar = V3(0, sh.y - R() - 0.6, sh.z + 0.15);
      barGrip(bar, 2.45, { dir: V3(0, -1, 0.05).normalize(), palm: V3(0, 0, -1), poles: [V3(0.4, 0, -1), V3(-0.4, 0, -1)] });
      barbell(bar);
    },
  },
  db_romanian_deadlift: {
    view: { az: -68, el: 6 },
    pose(p) {
      const sh = rdl(p);
      SIDES.forEach((s) => { const f = V3(SX[s] * 1.5, sh.y - R() - 0.6, sh.z + 0.2); arm(s, { fist: f, pole: V3(SX[s] * 0.3, 0, -1), dir: V3(0, -1, 0.05).normalize(), palm: V3(0, 0, -1) }); dumbbell(f, X); });
    },
  },
  good_morning: {
    view: { az: -68, el: 6 },
    pose(p) {
      hinge({ pitch: deg(lerp(2, 68, p)), drop: lerp(0, 0.6, p), flex: deg(lerp(0, 4, p)), headPitch: -deg(lerp(2, 68, p)) * 0.35, width: 0.4, knees: [V3(0.2, 0, 1), V3(-0.2, 0, 1)], over: MIDFOOT - 0.2, measure: hips });
      const bar = backBar(), { q } = chestFrame();
      SIDES.forEach((s) => arm(s, { fist: bar.clone().add(V3(SX[s] * 3.35, 0, 0)), pole: V3(SX[s] * 0.3, -1, -0.7).applyQuaternion(q), dir: V3(0, 1, 0.15).applyQuaternion(q).normalize(), palm: Z.clone().applyQuaternion(q) }));
      barbell(bar);
    },
  },

  lying_leg_curl: {
    view: { az: -80, el: 12 }, concentric: 'go',
    pose(p) {
      torso({ root: V3(0, 5.5, -0.3), pitch: deg(98), flex: deg(-4), headPitch: deg(-30) });
      const a = deg(lerp(4, 112, p));
      const ankles = SIDES.map((s) => { const h = pWorld(B['upperleg01.' + s]); const k = h.clone().add(V3(SX[s] * 0.1, 0.2, -THIGH())); return k.add(V3(0, Math.sin(a), -Math.cos(a)).multiplyScalar(SHIN())); });
      legs({ ankles, poles: [V3(0, -1, 0), V3(0, -1, 0)], feet: [footQ(0, deg(70) + a), footQ(0, deg(70) + a)] });
      const sh = shoulders();
      SIDES.forEach((s) => arm(s, { fist: V3(SX[s] * 2.2, 4.4, sh.z + 1.6), pole: V3(SX[s], 0.3, -0.5), dir: V3(0, -1, 0.3).normalize(), palm: medial(s) }));
      const ank = mid(ankles[0], ankles[1]), behind = V3(0, Math.cos(a), Math.sin(a));
      roller(ank.clone().add(behind.clone().multiplyScalar(0.75)).add(V3(0, Math.sin(a), -Math.cos(a)).multiplyScalar(-0.3)), 3.6);
      const knee = pWorld(B['lowerleg01.L']);
      lever(V3(3.0, knee.y, knee.z), ank.clone().add(behind.clone().multiplyScalar(0.75)).setX(3.0));
      const b = pad(3.0, 0.8, 11, V3(0, 4.55, 1.2)); b.rotation.x = deg(6);
      box(1.2, 4.0, 1.2, V3(0, 2.0, 1.2), MAT.frame()); box(4, 0.3, 1.5, V3(0, 0.15, 1.2), MAT.frame());
    },
  },
  seated_leg_curl: {
    view: { az: -68, el: 10 }, concentric: 'go',
    pose(p) {
      sit({ seatY: 5.0, hipZ: -1.8, pitch: deg(-8), headPitch: deg(4), footZ: 5 });
      const a = deg(lerp(4, 96, p));
      const ankles = SIDES.map((s) => { const k = pWorld(B['upperleg01.' + s]).add(V3(SX[s] * 0.2, -0.3, THIGH())); return k.add(V3(0, -Math.sin(a), Math.cos(a)).multiplyScalar(SHIN())); });
      legs({ ankles, poles: [V3(0.1, 1, 0), V3(-0.1, 1, 0)], feet: [footQ(deg(6), -deg(80) + a), footQ(deg(-6), -deg(80) + a)] });
      SIDES.forEach((s) => arm(s, { wrist: pWorld(B['upperleg01.' + s]).add(V3(SX[s] * 2.0, 0.2, 0.6)), pole: V3(SX[s], 0, -0.2), dir: Z, palm: medial(s), grip: 0.9 }));
      const ank = mid(ankles[0], ankles[1]), under = V3(0, -Math.cos(a), -Math.sin(a));
      roller(ank.clone().add(under.clone().multiplyScalar(0.7)), 3.6);
      const knee = pWorld(B['lowerleg01.L']);
      pad(3.4, 0.8, 1.6, mid(pWorld(B['upperleg01.L']), knee).add(V3(-0.15, 1.0, 0.8)));
      lever(V3(3.0, knee.y, knee.z), ank.clone().add(under.clone().multiplyScalar(0.7)).setX(3.0));
      const seatG = group();
      pad(3.2, 0.8, 4.2, V3(0, 4.6, -0.4), seatG); pad(3.0, 0.8, 7.0, V3(0, 8.2, -3.2), seatG).rotation.x = -Math.PI / 2 + deg(10);
      box(1.2, 4.2, 1.2, V3(0, 2.1, -0.8), MAT.frame()); box(5, 0.3, 2, V3(0, 0.15, -0.8), MAT.frame());
    },
  },

  hip_thrust: {
    view: { az: -72, el: 10 }, concentric: 'go',
    pose(p) {
      const C = V3(0, 4.9, -2.6);
      bridge(C, deg(lerp(38, 2, p)), 3.0, { onBench: true });
      const bar = hips().add(V3(0, 1.3, 0.6));
      barGrip(bar, 3.1, { dir: V3(0, 0, -1).add(V3(0, 0.5, 0)).normalize(), palm: V3(0, -1, 0), poles: [V3(1, 0.4, 0), V3(-1, 0.4, 0)], grip: 0.9 });
      barbell(bar);
      flatBench(4.4, -6, 6, V3(0, 0, -3.6), Math.PI / 2);
    },
  },
  glute_bridge: {
    view: { az: -72, el: 12 }, concentric: 'go',
    pose(p) {
      const C = V3(0, 0.95, -3.4);
      bridge(C, deg(lerp(0, -26, p)), 1.6);
      SIDES.forEach((s) => { const sh = pWorld(B['upperarm01.' + s]); arm(s, { wrist: V3(SX[s] * 2.4, 0.4, sh.z + 4.0), pole: V3(SX[s] * 0.3, 0.3, -1), dir: Z, palm: V3(0, -1, 0), grip: 0.1 }); });
      floorMat(V3(0, 0, -1), 6.5, 19);
    },
  },

  kettlebell_swing: {
    view: { az: -72, el: 6 }, mode: 'loop', seconds: 3.2, cycles: 2,
    pose(ph) {
      const u = 0.5 - 0.5 * Math.cos(2 * Math.PI * ph); // 0 deep in the hinge, 1 standing with the bell out front
      const pitch = deg(lerp(58, -3, u));
      hinge({ pitch, drop: lerp(1.4, 0, u), flex: deg(lerp(6, 0, u)), headPitch: -pitch * 0.4, width: 0.9, knees: [V3(0.4, 0, 1), V3(-0.4, 0, 1)], over: MIDFOOT - 0.2, measure: hips });
      const sh = shoulders();
      const a = lerp(-0.42, Math.PI / 2 * 0.98, u) + pitch * (1 - u);
      const dir = V3(0, -Math.cos(a), Math.sin(a));
      const handle = sh.clone().add(dir.clone().multiplyScalar(ARM_LEN * 0.97 + 0.6));
      const palm = V3(0, -Math.sin(a), -Math.cos(a));
      bothFists(SIDES.map((s) => handle.clone().add(V3(SX[s] * 0.42, 0, 0))), { dirs: [dir, dir], palms: [palm, palm], poles: [V3(0.3, 0, -1), V3(-0.3, 0, -1)] });
      kettlebell(handle, dir.clone().negate(), palm.clone().negate());
    },
  },

  standing_calf_raise: {
    view: { az: -66, el: 6 }, concentric: 'go',
    pose(p) {
      const a = deg(lerp(-14, 34, p)), blockY = 1.0;
      const c = calfFeet(a, blockY);
      const lift = c.ankles[0].y - 0.72;
      torso({ root: REST_ROOT().add(V3(0, lift - 0.15, -0.1 - (0.15 - c.ankles[0].z))), headPitch: deg(-2) });
      legs(c);
      SIDES.forEach((s) => { const sh = pWorld(B['upperarm01.' + s]); arm(s, { fist: sh.clone().add(V3(SX[s] * 1.2, 0.9, 0.3)), pole: V3(SX[s], -1, 0), dir: Y, palm: Z, grip: 0.9 }); });
      box(6, blockY, 2.2, V3(0, blockY / 2, 1.6), MAT.dark());
      const sh = shoulders();
      SIDES.forEach((s) => pad(1.6, 0.8, 2.0, pWorld(B['upperarm01.' + s]).add(V3(-SX[s] * 0.3, 0.7, -0.1))));
      box(5.4, 0.5, 0.6, sh.clone().add(V3(0, 1.2, -1.2)), MAT.frame());
      SIDES.forEach((s) => box(0.6, sh.y + 1.4, 0.6, V3(SX[s] * 2.7, (sh.y + 1.4) / 2, sh.z - 1.2), MAT.frame()));
    },
  },
  seated_calf_raise: {
    view: { az: -66, el: 10 }, concentric: 'go',
    pose(p) {
      const a = deg(lerp(-12, 32, p));
      sit({ seatY: 4.6, hipZ: -1.6, pitch: deg(6), headPitch: deg(-4), footZ: 3.0 });
      const c = calfFeet(a, 1.0, { knees: [V3(0.2, 1, 0.2), V3(-0.2, 1, 0.2)] });
      c.ankles.forEach((v) => { v.z += 2.6; });
      legs(c);
      const k = mid(pWorld(B['lowerleg01.L']), pWorld(B['lowerleg01.R']));
      pad(4.6, 0.8, 1.6, k.clone().add(V3(0, 0.9, -0.5)));
      SIDES.forEach((s) => arm(s, { fist: k.clone().add(V3(SX[s] * 2.6, 1.0, -0.5)), pole: V3(SX[s], -0.5, -0.5), dir: Z, palm: medial(s) }));
      lever(V3(0, k.y + 0.9, k.z - 6), k.clone().add(V3(0, 0.9, -0.5)));
      box(6, 1.0, 2.2, V3(0, 0.5, 2.9), MAT.dark());
      pad(3.2, 0.8, 3.4, V3(0, 4.2, -1.6)); box(1.2, 3.8, 1.2, V3(0, 1.9, -1.6), MAT.frame());
    },
  },
  bodyweight_calf_raise: {
    view: { az: -66, el: 6 }, concentric: 'go',
    pose(p) {
      const a = deg(lerp(0, 32, p));
      const c = calfFeet(a, 0.1);
      torso({ root: REST_ROOT().add(V3(0, c.ankles[0].y - 0.72, -0.1 - (0.15 - c.ankles[0].z))), headPitch: deg(-2) });
      legs(c);
      handsOnHips();
    },
  },
};
