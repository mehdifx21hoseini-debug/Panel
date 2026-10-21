// Cardio and whole-body movements: walking, machines, jumps.
import { V3, X, Y, Z, deg, lerp, arm, hang, sit, stand, shoulders, hips, bothFists, medial, SX, ARM_LEN, chestFrame, legs, footQ, torso, REST_ROOT, pWorld, B, mid, plank, liftFor, setWorld, qWorld, qAxis } from './common.js';
import { THREE, smooth, easeInOut, LEG_LEN } from '../figure.js';
import { treadmill, bike, elliptical, rower, dumbbell, floorMat, band, rod, part, box, MAT, handleBar } from '../props.js';

const SIDES = ['L', 'R'];
const tau = 2 * Math.PI;

/**
 * Walking in place: each foot spends 60% of a stride on the ground sliding back and 40% swinging
 * forward; the pelvis bobs and turns a little, the arms swing against the legs.
 */
function walk(ph, { stride = 3.2, lift = 1.0, armSwing = 22, carry = null, speed = 1 } = {}) {
  const footAt = (phase) => {
    const t = ((phase % 1) + 1) % 1;
    if (t < 0.6) { const u = t / 0.6; return { z: lerp(stride, -stride, u), y: 0, pitch: lerp(deg(-12), deg(26), smooth(0.6, 1, u)), ground: true }; }
    const u = easeInOut((t - 0.6) / 0.4);
    return { z: lerp(-stride, stride, u), y: lift * Math.sin(Math.PI * u), pitch: lerp(deg(26), deg(-12), smooth(0.3, 1, u)), ground: false };
  };
  const fL = footAt(ph), fR = footAt(ph + 0.5);
  const bob = 0.22 * Math.cos(2 * tau * ph);
  torso({ root: REST_ROOT().add(V3(0.12 * Math.sin(tau * ph), -0.35 + bob, -0.2)), yaw: deg(4) * Math.sin(tau * ph), pitch: deg(4 + 2 * speed), twist: -deg(6) * Math.sin(tau * ph), headYaw: -deg(3) * Math.sin(tau * ph) });
  legs({
    ankles: [V3(1.2, 0.72 + fL.y + Math.max(0, Math.sin(fL.pitch)) * 1.0, fL.z), V3(-1.2, 0.72 + fR.y + Math.max(0, Math.sin(fR.pitch)) * 1.0, fR.z)],
    poles: [V3(0.1, 0, 1), V3(-0.1, 0, 1)], feet: [footQ(deg(4), fL.pitch), footQ(deg(-4), fR.pitch)], toes: [fL.pitch > deg(5) && fL.ground ? 'flat' : null, fR.pitch > deg(5) && fR.ground ? 'flat' : null],
  });
  SIDES.forEach((s, i) => {
    const sw = deg(armSwing) * Math.sin(tau * ph + (i ? 0 : Math.PI));
    const sh = pWorld(B['upperarm01.' + s]);
    if (carry) {
      const fist = sh.clone().add(V3(SX[s] * 0.7, -ARM_LEN * 0.97 - 0.6, 0.2 + Math.sin(sw) * 0.6));
      arm(s, { fist, pole: V3(SX[s] * 0.3, 0, -1), dir: V3(0, -1, 0), palm: medial(s) });
      dumbbell(fist, Z, 1.15);
      return;
    }
    const bend = deg(12 + 14 * speed);
    const up = V3(0, -Math.cos(sw), Math.sin(sw)), elbow = sh.clone().add(V3(SX[s] * 0.3, 0, 0)).add(up.clone().multiplyScalar(ARM_LEN * 0.52));
    const fore = V3(0, -Math.cos(sw + bend), Math.sin(sw + bend));
    arm(s, { wrist: elbow.add(fore.clone().multiplyScalar(ARM_LEN * 0.47)).add(V3(SX[s] * 0.1, 0, 0)), pole: V3(0, 0, -1), dir: fore, palm: medial(s), grip: 0.35 });
  });
}

/** Rowing stroke through one phase: drive (legs, then back, then arms), then recovery in reverse. */
function rowStroke(ph) {
  const drive = ph < 0.42 ? ph / 0.42 : 1 - (ph - 0.42) / 0.58;
  const legsT = ph < 0.42 ? smooth(0, 0.65, drive) : smooth(0.35, 1, drive);
  const backT = ph < 0.42 ? smooth(0.35, 0.85, drive) : smooth(0.2, 0.7, drive);
  const armsT = ph < 0.42 ? smooth(0.6, 1, drive) : smooth(0, 0.4, drive);
  return { legsT, backT, armsT };
}

export default {
  treadmill_walk: {
    view: { az: -72, el: 8 }, mode: 'loop', seconds: 2.4, cycles: 2,
    pose(ph) { walk(ph); treadmill(ph * 2, V3(0, 0, 0.6)); },
  },
  brisk_walk: { view: { az: -72, el: 6 }, mode: 'loop', seconds: 2.2, cycles: 2, pose(ph) { walk(ph, { stride: 3.6, armSwing: 30, speed: 1.6 }); } },
  farmer_walk: { view: { az: -68, el: 6 }, mode: 'loop', seconds: 2.4, cycles: 2, pose(ph) { walk(ph, { stride: 2.6, lift: 0.8, carry: true }); } },

  stationary_bike: {
    view: { az: -72, el: 8 }, mode: 'loop', seconds: 3, cycles: 3,
    pose(ph) {
      const crank = V3(0, 3.6, 1.6), r = 1.75, seatY = 9.6;
      torso({ root: V3(0, seatY + 0.8, -3.0), pitch: deg(24), flex: deg(8), headPitch: deg(-14) });
      const th = tau * ph;
      const ankles = SIDES.map((s, i) => { const a = th + (i ? Math.PI : 0); return crank.clone().add(V3(SX[s] * 1.5, Math.cos(a) * r + 0.7, Math.sin(a) * r - 0.6)); });
      legs({ ankles, poles: [V3(0.15, 0.2, 1), V3(-0.15, 0.2, 1)], feet: SIDES.map((s, i) => footQ(SX[s] * deg(4), deg(12) + deg(14) * Math.sin(th + (i ? Math.PI : 0)))) });
      SIDES.forEach((s) => arm(s, { fist: V3(SX[s] * 2.4, 11.0, 4.4), pole: V3(SX[s], -0.6, -0.3), dir: V3(-SX[s], 0, 0.3).normalize(), palm: V3(0, -0.5, -1).normalize(), grip: 0.9 }));
      bike(ph, V3(), seatY);
      SIDES.forEach((s, i) => { const a = th + (i ? Math.PI : 0); const pedal = crank.clone().add(V3(SX[s] * 1.5, Math.cos(a) * r, Math.sin(a) * r)); rod(crank.clone().add(V3(SX[s] * 0.6, 0, 0)), pedal.clone().setX(SX[s] * 0.9), 0.15, MAT.dark()); box(1.0, 0.25, 1.6, pedal, MAT.rubber()); });
    },
  },

  elliptical: {
    view: { az: -72, el: 8 }, mode: 'loop', seconds: 3, cycles: 2,
    pose(ph) {
      const th = tau * ph;
      const footAt = (a) => V3(0, 2.0 + 0.9 * Math.sin(a), -0.6 + 2.6 * Math.cos(a));
      const fL = footAt(th), fR = footAt(th + Math.PI);
      torso({ root: REST_ROOT().add(V3(0.08 * Math.sin(th), 1.0 + 0.25 * Math.cos(2 * th), -0.4)), pitch: deg(6), twist: deg(5) * Math.sin(th), headPitch: deg(-4) });
      legs({ ankles: [fL.clone().add(V3(1.3, 0.72, 0)), fR.clone().add(V3(-1.3, 0.72, 0))], poles: [V3(0.1, 0, 1), V3(-0.1, 0, 1)], feet: [footQ(deg(4), deg(6) * Math.sin(th)), footQ(deg(-4), deg(6) * Math.sin(th + Math.PI))] });
      SIDES.forEach((s, i) => {
        const a = th + (i ? 0 : Math.PI);
        const fist = V3(SX[s] * 2.3, 12.4, 4.0 + 1.6 * Math.cos(a));
        arm(s, { fist, pole: V3(SX[s] * 0.6, -1, -0.3), dir: Y, palm: medial(s), grip: 1 });
        rod(fist.clone().add(V3(0, -9.5, -1.6)), fist.clone().add(V3(0, 1.0, 0.2)), 0.22, MAT.frame());
      });
      [fL, fR].forEach((f, i) => { box(1.5, 0.3, 3.6, f.clone().add(V3((i ? -1 : 1) * 1.3, 0.05, 0.4)), MAT.dark()); rod(f.clone().add(V3((i ? -1 : 1) * 1.3, 0, -1.4)), V3((i ? -1 : 1) * 1.3, 2.8, -7.6), 0.2, MAT.frame()); });
      elliptical(V3());
    },
  },

  rowing_machine: {
    view: { az: -72, el: 10 }, mode: 'loop', seconds: 3.2, cycles: 1,
    pose(ph) {
      const { legsT, backT, armsT } = rowStroke(ph);
      const seatZ = lerp(2.0, -2.6, legsT), seatY = 2.6;
      torso({ root: V3(0, seatY + 0.85, seatZ), pitch: deg(lerp(28, -24, backT)), flex: deg(lerp(10, -2, backT)), headPitch: deg(lerp(-10, 8, backT)) });
      legs({ ankles: [V3(1.2, 2.5, 7.0), V3(-1.2, 2.5, 7.0)], poles: [V3(0.25, 1, 0.2), V3(-0.25, 1, 0.2)], feet: [footQ(deg(6), deg(-52)), footQ(deg(-6), deg(-52))] });
      const sh = shoulders(), { up, fwd } = chestFrame();
      const reach = sh.clone().add(V3(0, -1.6, 0)).add(V3(0, 0, ARM_LEN * 0.97 + 0.4));
      const pulled = sh.clone().add(up.clone().multiplyScalar(-2.4)).add(fwd.clone().multiplyScalar(1.4));
      const handle = reach.lerp(pulled, armsT);
      bothFists(SIDES.map((s) => handle.clone().add(V3(SX[s] * 1.1, 0, 0))), { dirs: [V3(0, 0, 1), V3(0, 0, 1)].map((v) => v.clone().add(V3(0, 0.2, 0)).normalize()), palms: [V3(0, -1, 0), V3(0, -1, 0)], poles: [V3(0.6, -0.2, -1), V3(-0.6, -0.2, -1)] });
      handleBar(handle, X, 3.6);
      rod(handle, V3(0, 3.6, 9.0), 0.06, MAT.cable());
      pad_(seatZ, seatY);
      rower(V3());
      SIDES.forEach((s) => { const f = V3(SX[s] * 1.2, 2.0, 7.4); box(1.5, 0.3, 3.0, f, MAT.dark()).rotation.x = deg(-52); });
    },
  },

  jumping_jack: {
    view: { az: -24, el: 6 }, mode: 'loop', seconds: 2.4, cycles: 2,
    pose(ph) {
      const u = 0.5 - 0.5 * Math.cos(tau * ph);
      const hop = Math.max(0, Math.sin(2 * tau * ph)) * 0.9;
      const w = lerp(0.3, 2.6, u);
      torso({ root: REST_ROOT().add(V3(0, -0.4 + hop, -0.1)), headPitch: deg(-2) });
      legs({ ankles: [V3(1.9 + w, 0.72 + hop * 0.9, 0.2), V3(-1.9 - w, 0.72 + hop * 0.9, 0.2)], poles: [V3(0.5, 0, 1), V3(-0.5, 0, 1)], feet: [footQ(deg(12), deg(hop * 20)), footQ(deg(-12), deg(hop * 20))] });
      const a = deg(lerp(12, 168, u));
      SIDES.forEach((s) => {
        const sh = pWorld(B['upperarm01.' + s]), r = ARM_LEN * 0.95;
        const wrist = sh.clone().add(V3(SX[s] * Math.sin(a) * r, -Math.cos(a) * r, 0.25));
        arm(s, { wrist, pole: V3(SX[s] * 0.2, 0, -1), dir: wrist.clone().sub(sh).normalize(), palm: V3(-SX[s] * Math.cos(a), -Math.sin(a), 0).normalize().add(V3(0, 0, 0.3)).normalize(), grip: 0.2 });
      });
    },
  },

  jump_rope: {
    view: { az: -60, el: 6 }, mode: 'loop', seconds: 2, cycles: 4,
    pose(ph) {
      // One turn of the rope per hop; the rope passes under the feet at the top of the hop.
      const hop = Math.max(0, Math.sin(tau * ph - 0.6)) * 0.55;
      torso({ root: REST_ROOT().add(V3(0, -0.3 + hop, -0.15)), pitch: deg(3), headPitch: deg(-4) });
      legs({ ankles: [V3(1.6, 0.72 + hop * 0.95, 0.4), V3(-1.6, 0.72 + hop * 0.95, 0.4)], poles: [V3(0.2, 0, 1), V3(-0.2, 0, 1)], feet: [footQ(deg(6), deg(14 + hop * 30)), footQ(deg(-6), deg(14 + hop * 30))], toes: ['flat', 'flat'] });
      const psi = tau * ph;
      const hands = SIDES.map((s) => { const sh = pWorld(B['upperarm01.' + s]); return sh.clone().add(V3(SX[s] * 2.3, -ARM_LEN * 0.82 + 0.25 * Math.cos(psi), 0.8 + 0.25 * Math.sin(psi))); });
      SIDES.forEach((s, i) => arm(s, { fist: hands[i], pole: V3(SX[s] * 0.6, 0, -1), dir: V3(SX[s] * 0.2, -0.3, 1).normalize(), palm: medial(s), grip: 1 }));
      // The rope swings around the axis through the hands.
      const c = mid(hands[0], hands[1]), R = 8.6;
      const pts = [];
      for (let k = 0; k <= 16; k++) {
        const t = k / 16, x = lerp(hands[0].x, hands[1].x, t);
        const sag = Math.sin(Math.PI * t);
        const rr = R * sag;
        pts.push(V3(x, c.y + Math.cos(psi) * rr * 0.98 * (1) - (1 - sag) * 0, c.z + Math.sin(psi) * rr * 0.55).lerp(V3(x, lerp(hands[0].y, hands[1].y, t), lerp(hands[0].z, hands[1].z, t)), 1 - sag));
      }
      band(pts, 0.05).material = MAT.rubber();
      SIDES.forEach((s, i) => rod(hands[i].clone().add(V3(0, -0.6, 0)), hands[i].clone().add(V3(0, 0.6, 0)), 0.2, MAT.rubber()));
    },
  },

  mountain_climber: {
    view: { az: -66, el: 12 }, mode: 'loop', seconds: 2, cycles: 2,
    pose(ph) {
      const wristY = 0.48, lift = liftFor(wristY + ARM_LEN * 0.97, {}, 'mc');
      const sh = plank(lift + 2);
      const h = hips();
      const drive = (phase) => { const t = ((phase % 1) + 1) % 1; return t < 0.5 ? easeInOut(t / 0.5) : 1 - easeInOut((t - 0.5) / 0.5); };
      const ankles = SIDES.map((s, i) => {
        const d = drive(ph + (i ? 0.5 : 0));
        const back = V3(SX[s] * 1.0, 1.4, -10.3), front = h.clone().add(V3(SX[s] * 0.9, -2.0, 2.4));
        return back.lerp(front, d);
      });
      legs({ ankles, poles: [V3(0, -1, 0.6), V3(0, -1, 0.6)], feet: SIDES.map(() => footQ(0, deg(60))), toes: ['flat', 'flat'] });
      SIDES.forEach((s) => arm(s, { wrist: V3(SX[s] * 2.5, wristY, sh.z + 0.25), pole: V3(SX[s] * 0.5, 0.5, -1), dir: V3(SX[s] * 0.15, 0, 1).normalize(), palm: V3(0, -1, 0), grip: 0.05 }));
    },
  },

  burpee: {
    view: { az: -66, el: 8 }, mode: 'loop', seconds: 3.6, cycles: 1,
    pose(ph) {
      // stand → squat, hands down → feet back to a plank → feet in → jump, arms up → land.
      const K = [
        { t: 0.0, root: V3(0, 0, 0), pitch: 0, feet: 0, hands: 'side' },
        { t: 0.16, root: V3(0, -4.4, -1.4), pitch: 50, feet: 0, hands: 'floor' },
        { t: 0.3, root: V3(0, -3.6, 1.3), pitch: 74, feet: 1, hands: 'floor' },
        { t: 0.46, root: V3(0, -3.6, 1.3), pitch: 74, feet: 1, hands: 'floor' },
        { t: 0.6, root: V3(0, -4.4, -1.4), pitch: 50, feet: 0, hands: 'floor' },
        { t: 0.74, root: V3(0, 0, 0), pitch: 0, feet: 0, hands: 'up' },
        { t: 0.84, root: V3(0, 1.6, 0), pitch: -2, feet: 0, hands: 'up', air: 1 },
        { t: 1.0, root: V3(0, 0, 0), pitch: 0, feet: 0, hands: 'side' },
      ];
      let i = 0; while (i < K.length - 2 && ph > K[i + 1].t) i++;
      const a = K[i], b = K[i + 1], u = easeInOut((ph - a.t) / (b.t - a.t));
      const root = REST_ROOT().add(a.root.clone().lerp(b.root, u)), pitch = deg(lerp(a.pitch, b.pitch, u)), fb = lerp(a.feet, b.feet, u), air = lerp(a.air || 0, b.air || 0, u);
      torso({ root, pitch, flex: deg(10 * Math.sin(pitch)), headPitch: -pitch * 0.5 });
      const ankles = SIDES.map((s) => V3(SX[s] * 1.5, 0.72 + air * 1.1 + fb * 0.6, lerp(0.2, -9.6, fb)));
      legs({ ankles, poles: [V3(0.4, 0, 1), V3(-0.4, 0, 1)], feet: SIDES.map((s) => footQ(SX[s] * deg(8), deg(lerp(0, 62, fb) + air * 30))), toes: fb > 0.3 ? ['flat', 'flat'] : [null, null] });
      const handAt = (kind, s) => {
        const sh = pWorld(B['upperarm01.' + s]);
        if (kind === 'floor') return { wrist: V3(SX[s] * 2.3, 0.48, 3.6), dir: V3(SX[s] * 0.1, 0, 1).normalize(), palm: V3(0, -1, 0) };
        if (kind === 'up') return { wrist: sh.clone().add(V3(SX[s] * 1.0, ARM_LEN * 0.95, 0.4)), dir: Y, palm: Z };
        return { wrist: sh.clone().add(V3(SX[s] * 0.35, -ARM_LEN * 0.95, 0.3)), dir: V3(0, -1, 0), palm: medial(s) };
      };
      SIDES.forEach((s) => {
        const h0 = handAt(a.hands, s), h1 = handAt(b.hands, s);
        arm(s, { wrist: h0.wrist.clone().lerp(h1.wrist, u), pole: V3(SX[s] * 0.4, 0, -1), dir: h0.dir.clone().lerp(h1.dir, u).normalize(), palm: h0.palm.clone().lerp(h1.palm, u).normalize(), grip: 0.1 });
      });
    },
  },
};

function pad_(z, y) { box(2.6, 0.5, 2.6, V3(0, y - 0.25, z), MAT.pad()); }
