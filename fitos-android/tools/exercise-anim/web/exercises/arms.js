// Arm exercises: biceps, triceps, forearms.
import { V3, X, Y, Z, deg, lerp, arm, hang, sit, stand, hinge, supine, shoulders, hips, barGrip, bothFists, medial, SX, ARM_LEN, chestFrame, legs, footQ, torso, REST_ROOT, pWorld, B, mid, plank, liftFor, KNEES_UP } from './common.js';
import { THREE } from '../figure.js';
import { barbell, ezBar, dumbbell, flatBench, inclineBench, preacherBench, cableTower, handleBar, rope, band, box, MAT, rod, step, pad } from '../props.js';

const SIDES = ['L', 'R'];
const R = () => ARM_LEN * 0.97;
const FORE = () => pWorld(B['lowerarm01.L']).distanceTo(pWorld(B['wrist.L'])) + 0.6;
/** The handle axis a hand turned to (dir, palm) holds. */
const axisOf = (dir, palm) => dir.clone().cross(palm).normalize();

/**
 * A curl: the elbow stays by the side (drifting a little forward at the top), the forearm
 * turns through `a`. turn: 0 palms up, 1 thumbs up (hammer), 0.3 the EZ bar's angle.
 */
function curlHand(s, a, { turn = 0, elbowFwd = 0.5, widen = 0.35 } = {}) {
  const sh = pWorld(B['upperarm01.' + s]);
  const p = (a - deg(8)) / deg(132);
  const elbow = sh.clone().add(V3(SX[s] * widen, -ARM_LEN * 0.53, lerp(0.15, elbowFwd, p * p)));
  const dir = V3(0, -Math.cos(a), Math.sin(a));
  const up = V3(0, Math.sin(a), Math.cos(a));
  const palm = up.clone().multiplyScalar(Math.cos(turn * Math.PI / 2)).add(medial(s).multiplyScalar(Math.sin(turn * Math.PI / 2))).normalize();
  return { fist: elbow.clone().add(dir.clone().multiplyScalar(FORE())), dir, palm };
}
function standingCurl(p, tool) {
  stand({ width: 0.3, pitch: deg(lerp(0, -2, p)), headPitch: deg(-3) });
  const a = deg(lerp(8, 140, p));
  const turn = tool === 'hammer' ? 1 : tool === 'ez' ? 0.3 : 0;
  const hands = SIDES.map((s) => curlHand(s, a, { turn, widen: tool === 'db' || tool === 'hammer' ? 0.35 : tool === 'ez' ? -0.05 : 0.15 }));
  const out = hands.map((h, i) => arm(SIDES[i], { fist: h.fist, pole: V3(SX[SIDES[i]] * 0.15, 0, -1), dir: h.dir, palm: h.palm }));
  const barAt = mid(out[0].fist, out[1].fist);
  if (tool === 'barbell') barbell(barAt, { light: true });
  else if (tool === 'ez') ezBar(barAt);
  else if (tool === 'cable') { handleBar(barAt, X, 4.6); cableTower(V3(0, 1.2, barAt.z + 7.5), barAt); }
  else if (tool === 'band') out.forEach((o, i) => { const f = pWorld(B['foot.' + SIDES[i]]).add(V3(0, -0.6, 0.6)); band([f, f.clone().lerp(o.fist, 0.5).add(V3(0, 0, 0.4)), o.fist]); });
  else hands.forEach((h, i) => dumbbell(out[i].fist, axisOf(h.dir, h.palm)));
}

/** Upper arms by the head, elbows up; the forearms lower behind and extend. */
function overheadExtension(p, { cable = false } = {}) {
  if (cable) stand({ ankles: [V3(2.0, 0.72, 1.6), V3(-2.0, 0.72, -1.8)], pitch: deg(24), flex: deg(6), headPitch: deg(-14), knees: [V3(0.2, 0, 1), V3(-0.2, 0, 1)] });
  else stand({ width: 0.4, pitch: deg(-3), headPitch: deg(-6) });
  const { up, fwd } = chestFrame();
  const sh = shoulders();
  const elbow = sh.clone().add(up.clone().multiplyScalar(ARM_LEN * 0.5)).add(fwd.clone().multiplyScalar(0.3));
  const a = deg(lerp(25, 160, p)); // 25: arms straight up; 160: hands low behind the head
  const d = up.clone().multiplyScalar(Math.cos(a)).add(fwd.clone().multiplyScalar(-Math.sin(a)));
  const hand = elbow.clone().add(d.clone().multiplyScalar(FORE() - 0.1));
  return { hand, d, up, fwd, sh, elbow };
}

export default {
  barbell_curl: { view: { az: -48, el: 6 }, concentric: 'go', pose: (p) => standingCurl(p, 'barbell') },
  ez_curl: { view: { az: -48, el: 6 }, concentric: 'go', pose: (p) => standingCurl(p, 'ez') },
  dumbbell_curl: { view: { az: -42, el: 6 }, concentric: 'go', pose: (p) => standingCurl(p, 'db') },
  hammer_curl: { view: { az: -42, el: 6 }, concentric: 'go', pose: (p) => standingCurl(p, 'hammer') },
  cable_curl: { view: { az: -62, el: 6 }, concentric: 'go', pose: (p) => standingCurl(p, 'cable') },
  band_curl: { view: { az: -42, el: 6 }, concentric: 'go', pose: (p) => standingCurl(p, 'band') },

  incline_db_curl: {
    view: { az: -62, el: 10 }, concentric: 'go',
    pose(p) {
      torso({ root: V3(0, 5.3, -0.2), pitch: -(Math.PI / 2 - deg(50)), flex: deg(-2), headPitch: deg(12) });
      legs({ ankles: [V3(2.4, 0.72, 4.8), V3(-2.4, 0.72, 4.8)], poles: KNEES_UP, feet: [footQ(deg(15)), footQ(deg(-15))] });
      const a = deg(lerp(4, 128, p));
      SIDES.forEach((s) => {
        const sh = pWorld(B['upperarm01.' + s]);
        const elbow = sh.clone().add(V3(SX[s] * 0.4, -ARM_LEN * 0.53, -0.2));
        const dir = V3(0, -Math.cos(a), Math.sin(a)), palm = V3(0, Math.sin(a), Math.cos(a));
        const o = arm(s, { fist: elbow.clone().add(dir.clone().multiplyScalar(FORE())), pole: V3(0, 0, -1), dir, palm });
        dumbbell(o.fist, X);
      });
      inclineBench(deg(50));
    },
  },

  preacher_curl: {
    view: { az: -66, el: 10 }, concentric: 'go',
    pose(p) {
      sit({ seatY: 5.0, hipZ: -1.2, pitch: deg(18), flex: deg(6), headPitch: deg(-10), footZ: 3.4, footX: 2.0 });
      const sh = shoulders();
      // Upper arms rest down the angled pad; the forearms rise from along it to upright.
      const along = V3(0, -Math.sin(deg(48)), Math.cos(deg(48)));
      const elbows = SIDES.map((s) => pWorld(B['upperarm01.' + s]).add(V3(SX[s] * -0.3, 0, 0)).add(along.clone().multiplyScalar(ARM_LEN * 0.53)));
      const a = deg(lerp(-34, 85, p)); // from the pad's line up past vertical
      const dir = V3(0, Math.sin(a), Math.cos(a)), palm = V3(0, Math.cos(a), -Math.sin(a));
      const out = SIDES.map((s, i) => arm(s, { fist: elbows[i].clone().add(dir.clone().multiplyScalar(FORE())), pole: V3(SX[s] * 0.2, -0.8, -0.6), dir, palm }));
      ezBar(mid(out[0].fist, out[1].fist));
      preacherBench(5.0, sh.y - 1.3, sh.z + 2.2);
    },
  },

  concentration_curl: {
    view: { az: -40, el: 14 }, concentric: 'go',
    pose(p) {
      // Seated on a bench, right elbow braced on the inside of the right thigh.
      sit({ seatY: 4.4, hipZ: -1.0, pitch: deg(34), flex: deg(10), headPitch: deg(-24), footZ: 3.2, footX: 3.1, knees: [V3(1, 1, 0.6), V3(-1, 1, 0.6)] });
      const kneeR = pWorld(B['lowerleg01.R']), hipR = pWorld(B['upperleg01.R']);
      const elbow = hipR.clone().lerp(kneeR, 0.72).add(V3(0.75, 0.55, 0));
      const a = deg(lerp(10, 135, p));
      const dir = V3(0.25 * Math.cos(a), -Math.cos(a), Math.sin(a) * 0.6).normalize(), palm = V3(-0.3, Math.sin(a), Math.cos(a)).normalize();
      const o = arm('R', { fist: elbow.clone().add(dir.clone().multiplyScalar(FORE())), pole: V3(0.3, -0.2, -1), dir, palm });
      dumbbell(o.fist, axisOf(dir, palm));
      const kneeL = pWorld(B['lowerleg01.L']);
      arm('L', { wrist: kneeL.clone().add(V3(0.2, 0.7, -0.5)), pole: V3(1, 0, -0.3), dir: V3(0, -0.3, 1).normalize(), palm: V3(0, -1, 0), grip: 0.4 });
      flatBench(4.4, -5, 3, V3(0, 0, -1.2), Math.PI / 2);
    },
  },

  close_grip_bench: {
    view: { az: -38, el: 22 },
    pose(p) {
      supine({ top: 4.4, hipZ: 1.2 });
      const sh = shoulders(), { up, fwd } = chestFrame();
      const top = sh.clone().add(fwd.clone().multiplyScalar(R() + 0.55)).add(up.clone().multiplyScalar(-0.4));
      const low = sh.clone().add(fwd.clone().multiplyScalar(2.2)).add(up.clone().multiplyScalar(-1.8));
      const bar = top.lerp(low, p);
      barGrip(bar, 1.6, { dir: fwd, palm: up.clone().negate(), poles: [V3(0.3, -1, 0.7), V3(-0.3, -1, 0.7)] });
      barbell(bar);
      flatBench(4.4, -11.5, 3.0);
    },
  },

  triceps_pushdown: {
    view: { az: -62, el: 8 },
    pose(p) {
      stand({ width: 0.45, pitch: deg(10), flex: deg(4), headPitch: deg(-8) });
      // Elbows pinned at the sides; the bar goes from chest height down to the thighs.
      const t = 1 - p;
      const a = deg(lerp(14, 108, t));
      const pts = SIDES.map((s) => {
        const sh = pWorld(B['upperarm01.' + s]);
        const elbow = sh.clone().add(V3(SX[s] * 0.1, -ARM_LEN * 0.52, 0.45));
        return elbow.add(V3(0, -Math.cos(a), Math.sin(a)).multiplyScalar(FORE()));
      });
      const dir = V3(0, -Math.cos(a), Math.sin(a)), palm = V3(0, -Math.sin(a), -Math.cos(a)).negate().negate();
      const out = SIDES.map((s, i) => arm(s, { fist: pts[i].clone().add(V3(-SX[s] * 0.9, 0, 0)), pole: V3(SX[s] * 0.1, 0, -1), dir, palm: V3(0, Math.sin(a) > 0.5 ? -1 : -1, 0).multiplyScalar(0).add(V3(0, -Math.sin(a), -Math.cos(a))).normalize() }));
      const bar = mid(out[0].fist, out[1].fist);
      handleBar(bar, X, 3.6);
      cableTower(V3(0, 20.5, bar.z + 1.4), bar);
    },
  },

  overhead_db_extension: {
    view: { az: -66, el: 6 }, concentric: 'go',
    pose(p) {
      const { hand, d, up, fwd } = overheadExtension(1 - p);
      const pts = SIDES.map((s) => hand.clone().add(V3(SX[s] * 0.42, 0, 0)));
      bothFists(pts, { dirs: [d, d], palms: [medial('L'), medial('R')], poles: [V3(0.3, 1, 0.4), V3(-0.3, 1, 0.4)], grip: 0.7 });
      dumbbell(hand.clone().add(d.clone().multiplyScalar(0.55)), d, 1.05);
    },
  },

  cable_overhead_extension: {
    view: { az: -66, el: 8 }, concentric: 'go',
    pose(p) {
      const { hand, d, sh } = overheadExtension(1 - p, { cable: true });
      const pts = SIDES.map((s) => hand.clone().add(V3(SX[s] * 0.5, 0, 0)));
      bothFists(pts, { dirs: [d, d], palms: [medial('L'), medial('R')], poles: [V3(0.3, 1, 0.4), V3(-0.3, 1, 0.4)] });
      const pulley = V3(0, sh.y - 3.8, sh.z - 8.5);
      const top = mid(pts[0], pts[1]).lerp(pulley, 0.12);
      rope(top, pts);
      cableTower(pulley, top);
    },
  },

  skull_crusher: {
    view: { az: -60, el: 18 },
    pose(p) {
      supine({ top: 4.4, hipZ: 1.2 });
      const sh = shoulders(), { up, fwd } = chestFrame();
      // Upper arms angled slightly back toward the head and held still.
      const elbows = SIDES.map((s) => pWorld(B['upperarm01.' + s]).add(V3(-SX[s] * 0.5, 0, 0)).add(fwd.clone().multiplyScalar(ARM_LEN * 0.5)).add(up.clone().multiplyScalar(0.7)));
      const a = deg(lerp(15, 120, p));
      const d = fwd.clone().multiplyScalar(Math.cos(a)).add(up.clone().multiplyScalar(Math.sin(a)));
      const palm = fwd.clone().multiplyScalar(-Math.sin(a)).add(up.clone().multiplyScalar(Math.cos(a))).negate();
      const out = SIDES.map((s, i) => arm(s, { fist: elbows[i].clone().add(d.clone().multiplyScalar(FORE())), pole: up.clone().negate().add(V3(SX[s] * 0.2, 0, 0)), dir: d, palm }));
      ezBar(mid(out[0].fist, out[1].fist));
      flatBench(4.4, -11.5, 3.0);
    },
  },

  db_kickback: {
    view: { az: -82, el: 8 }, concentric: 'go',
    pose(p) {
      hinge({ pitch: deg(62), drop: 1.0, flex: deg(4), headPitch: deg(-26), knees: [V3(0.2, 0, 1), V3(-0.2, 0, 1)], over: 1.0 });
      const { up, fwd } = chestFrame();
      const a = deg(lerp(95, 6, p)); // angle of the forearm from the upper arm's line
      SIDES.forEach((s) => {
        const sh = pWorld(B['upperarm01.' + s]);
        const elbow = sh.clone().add(up.clone().multiplyScalar(-ARM_LEN * 0.52)).add(V3(SX[s] * 0.35, 0, 0)).add(fwd.clone().multiplyScalar(0.2));
        const along = up.clone().negate();
        const d = along.clone().multiplyScalar(Math.cos(a)).add(fwd.clone().multiplyScalar(Math.sin(a)));
        const o = arm(s, { fist: elbow.add(d.clone().multiplyScalar(FORE())), pole: up.clone().add(fwd.clone().multiplyScalar(-0.3)), dir: d, palm: medial(s) });
        dumbbell(o.fist, axisOf(d, medial(s)));
      });
    },
  },

  bench_dip: {
    view: { az: -72, el: 8 },
    pose(p) {
      // Hands on a bench behind, heels forward on the floor; the hips sink by bending the elbows.
      const benchTop = 4.6, handZ = -1.2;
      const want = V3(0, lerp(benchTop + R() + 0.2, benchTop + 2.6, p), handZ + 0.6);
      let off = V3();
      for (let k = 0; k < 4; k++) {
        torso({ root: V3(0, want.y - 4.6, 0.6).add(off), pitch: deg(lerp(-2, 6, p)), flex: deg(4), headPitch: deg(-4) });
        off.add(want.clone().sub(shoulders()));
      }
      legs({ ankles: [V3(1.5, 0.72, 8.8), V3(-1.5, 0.72, 8.8)], poles: [V3(0.1, 1, 0), V3(-0.1, 1, 0)], feet: [footQ(deg(6), deg(-40)), footQ(deg(-6), deg(-40))] });
      SIDES.forEach((s) => arm(s, { wrist: V3(SX[s] * 2.0, benchTop + 0.48, handZ), pole: V3(0, 0, -1), dir: V3(0, 0, 1), palm: V3(0, -1, 0), grip: 0.4 }));
      flatBench(benchTop, -2.6, 2.6, V3(0, 0, handZ - 1.0), Math.PI / 2);
    },
  },

  diamond_push_up: {
    view: { az: -62, el: 13 },
    pose(p) {
      const wristY = 0.48;
      const top = liftFor(wristY + R() + 0.05, {}, 'd'), low = liftFor(wristY + 1.9, {}, 'd');
      const shTop = plank(top);
      plank(lerp(top, low, p));
      SIDES.forEach((s) => arm(s, { wrist: V3(SX[s] * 0.9, wristY, shTop.z + 0.6), pole: V3(SX[s] * 0.2, 0.5, -1), dir: V3(-SX[s] * 0.5, 0, 1).normalize(), palm: V3(0, -1, 0), grip: 0.05 }));
    },
  },

  band_triceps_extension: {
    view: { az: -62, el: 8 },
    pose(p) {
      stand({ width: 0.45, pitch: deg(8), flex: deg(4), headPitch: deg(-8) });
      const t = 1 - p, a = deg(lerp(14, 108, t));
      const dir = V3(0, -Math.cos(a), Math.sin(a)), palm = V3(0, -Math.sin(a), -Math.cos(a));
      const out = SIDES.map((s) => {
        const sh = pWorld(B['upperarm01.' + s]);
        const elbow = sh.clone().add(V3(SX[s] * 0.15, -ARM_LEN * 0.52, 0.45));
        return arm(s, { fist: elbow.add(dir.clone().multiplyScalar(FORE())).add(V3(-SX[s] * 0.6, 0, 0)), pole: V3(SX[s] * 0.1, 0, -1), dir, palm });
      });
      const anchor = V3(0, 19.5, shoulders().z + 3.2);
      out.forEach((o) => band([o.fist, o.fist.clone().lerp(anchor, 0.5), anchor]));
      box(6, 21, 0.4, V3(0, 10.5, anchor.z + 0.5), MAT.wall()).castShadow = false;
    },
  },

  wrist_curl: {
    view: { az: -60, el: 18 }, concentric: 'go',
    pose(p) {
      // Seated, forearms along the thighs, palms up; only the wrists move.
      sit({ seatY: 4.6, hipZ: -1.0, pitch: deg(30), flex: deg(8), headPitch: deg(-24), footZ: 4.0, footX: 1.7 });
      const a = deg(lerp(-50, 40, p));
      SIDES.forEach((s) => {
        const knee = pWorld(B['lowerleg01.' + s]), hipJ = pWorld(B['upperleg01.' + s]);
        const along = knee.clone().sub(hipJ).normalize();
        const wrist = knee.clone().add(along.clone().multiplyScalar(0.5)).add(V3(-SX[s] * 0.1, 0.65, 0));
        const dir = along.clone().multiplyScalar(Math.cos(a)).add(V3(0, Math.sin(a), 0)).normalize();
        const palm = V3(0, Math.cos(a), 0).add(along.clone().multiplyScalar(-Math.sin(a))).normalize();
        const o = arm(s, { wrist, pole: V3(SX[s] * 0.5, -1, -0.5), dir, palm, grip: 0.9 });
        dumbbell(o.fist, axisOf(dir, palm));
      });
      flatBench(4.6, -4.5, 2.5, V3(0, 0, -1.4), Math.PI / 2);
    },
  },
};
