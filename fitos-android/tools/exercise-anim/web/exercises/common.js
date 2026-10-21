// Postures and grips shared by the exercises.
import { V3, X, Y, Z, deg, lerp, torso, legs, arm, hang, stance, footQ, shoulder, hip, mid, chestFrame, REST_ROOT, ARM_LEN, pWorld, B } from '../figure.js';

export const SX = { L: 1, R: -1 };
export const KNEES_FWD = [V3(0.3, 0, 1), V3(-0.3, 0, 1)];
export const KNEES_UP = [V3(0.3, 1, 0.2), V3(-0.3, 1, 0.2)];
export const ELBOWS_BACK = [V3(0.3, 0, -1), V3(-0.3, 0, -1)];
export const ELBOWS_OUT = [V3(1, -0.4, -0.2), V3(-1, -0.4, -0.2)];
export const ELBOWS_DOWN = [V3(0.5, -1, 0), V3(-0.5, -1, 0)];

/** Moves the pelvis forward or back (several passes) until measure().z sits at z. */
export function balance(applyAt, measure, z, passes = 3) {
  let back = 0, m;
  for (let k = 0; k < passes; k++) { applyAt(back); m = measure(); back += m.z - z; }
  applyAt(back);
  return measure();
}

/** Standing: hips dropped and pitched forward, spine flexed, feet planted. */
export function stand({ drop = 0, back = 0, pitch = 0, flex = 0, side = 0, twist = 0, headPitch = 0, headYaw = 0, width = 0.35, knees = KNEES_FWD, feet, ankles, yaw = 0, at = V3() } = {}) {
  torso({ root: REST_ROOT().add(V3(0, -drop, -back)).add(at), yaw, pitch, flex, side, twist, headPitch, headYaw });
  legs({ ankles: ankles || stance(width, yaw, at), poles: knees, feet: feet || [footQ(yaw + deg(8)), footQ(yaw - deg(8))] });
}

/** Seated on a seat whose top is at `seatY`, feet on the floor at `footZ`. */
export function sit({ seatY = 4.6, hipZ = -0.6, pitch = 0, flex = 0, headPitch = 0, footZ = 4.3, footX = 2.1, twist = 0, side = 0, knees = KNEES_UP, at = V3(), yaw = 0 } = {}) {
  torso({ root: V3(0, seatY + 0.95, hipZ).add(at), yaw, pitch, flex, headPitch, twist, side });
  legs({ ankles: [V3(footX, 0.72, footZ).add(at), V3(-footX, 0.72, footZ).add(at)], poles: knees, feet: [footQ(yaw + deg(10)), footQ(yaw - deg(10))] });
}

/** Lying face up along -z (head toward -z), back on a surface whose top is at `top`. */
export function supine({ top = 4.4, hipZ = 1.2, incline = 0, flex = deg(-3), headPitch = 0, feet = 'floor', footZ = 5.6, footX = 2.6 } = {}) {
  torso({ root: V3(0, top + 0.62, hipZ), pitch: -(Math.PI / 2 - incline), flex, headPitch });
  if (feet === 'floor') legs({ ankles: [V3(footX, 0.72, footZ), V3(-footX, 0.72, footZ)], poles: [V3(0.3, 1, 0.2), V3(-0.3, 1, 0.2)], feet: [footQ(deg(20)), footQ(deg(-20))] });
}

export const shoulders = () => mid(shoulder('L'), shoulder('R'));
export const hips = () => mid(hip('L'), hip('R'));

/** Grips on a bar along x at `bar`, hands `half` either side of the middle. */
export function barGrip(bar, half, { dir = Y, palm = Z, poles = ELBOWS_OUT, grip = 1 } = {}) {
  return ['L', 'R'].map((s, i) => arm(s, { fist: bar.clone().add(V3(SX[s] * half, 0, 0)), pole: poles[i], dir, palm, grip }));
}
/** Both hands on dumbbell handles: fists at the given points. */
export function bothFists(points, { dirs, palms, poles, grip = 1 }) {
  return ['L', 'R'].map((s, i) => arm(s, { fist: points[i], pole: poles[i], dir: dirs[i], palm: palms[i], grip }));
}
/** Medial: from a side toward the middle (the way a neutral-grip palm faces). */
export const medial = (s) => V3(-SX[s], 0, 0);
export const outward = (s) => V3(SX[s], 0, 0);
/** Both arms hanging. */
export const hangBoth = (o) => ['L', 'R'].map((s) => hang(s, o));

export { V3, X, Y, Z, deg, lerp, arm, hang, chestFrame, shoulder, hip, mid, ARM_LEN, pWorld, B, footQ, torso, legs, stance, REST_ROOT };

/**
 * A straight body on the balls of the feet (push-up, plank): rises at `lift` degrees from the
 * floor, the toes tucked and staying at `toe`.
 */
export function plank(lift, { toe = V3(0, 0.28, -10.5), flex = deg(-2), headPitch = deg(-12), pitchExtra = 0 } = {}) {
  torso({ root: REST_ROOT(), pitch: deg(90 - lift) + pitchExtra, flex, headPitch });
  ['L', 'R'].forEach((s) => {
    setWorld(B['foot.' + s], qWorld(B['lowerleg02.' + s]).multiply(qAxis(X, deg(-8))));
    for (let t = 1; t <= 5; t++) setWorld(B[`toe${t}-1.${s}`], qAxis(Y, 0));
  });
  const ball = mid(pWorld(B['toe1-1.L']), pWorld(B['toe1-1.R']));
  B.root.position.add(toe.clone().sub(ball)); B.root.updateMatrixWorld(true);
  return shoulders();
}
/** The lift at which plank(lift) puts the shoulders at height y (cached). */
const liftCache = {};
export function liftFor(y, opts = {}, tag = '') {
  const k = tag + y.toFixed(3); if (liftCache[k] != null) return liftCache[k];
  let lo = -10, hi = 60;
  for (let i = 0; i < 32; i++) { const m = (lo + hi) / 2; if (plank(m, opts).y < y) lo = m; else hi = m; }
  return (liftCache[k] = (lo + hi) / 2);
}
import { setWorld, qWorld, qAxis } from '../figure.js';
export { setWorld, qWorld, qAxis };

/**
 * Hanging from a bar or supported on the hands: moves the pelvis (several passes) until the
 * shoulders' middle reaches `want`, the trunk pitched as given. Feet then hang below the hips.
 */
export function placeShoulders(want, { pitch = 0, flex = 0, headPitch = 0, yaw = 0, roll = 0, twist = 0, start = REST_ROOT() } = {}) {
  let off = V3();
  for (let k = 0; k < 4; k++) {
    torso({ root: start.clone().add(off), yaw, pitch, flex, headPitch, roll, twist });
    off.add(want.clone().sub(shoulders()));
  }
  torso({ root: start.clone().add(off), yaw, pitch, flex, headPitch, roll, twist });
}
/** Legs hanging with the knees bent and the ankles crossed behind. */
export function hangingLegs(bend = 0.55) {
  const h = hips();
  legs({
    ankles: [h.clone().add(V3(0.2, -7.4 + bend * 2.6, -bend * 4.2)), h.clone().add(V3(-0.3, -7.2 + bend * 2.6, -bend * 3.6))],
    poles: [V3(0.15, -0.3, 1), V3(-0.15, -0.3, 1)], feet: [footQ(deg(4), deg(28)), footQ(deg(-4), deg(28))],
  });
}
/**
 * A hip hinge: pitch the trunk, soften the knees, and slide the hips back until `measure()`
 * (shoulders, a bar…) is over the middle of the foot.
 */
export function hinge({ pitch, drop = 0, flex = 0, headPitch, width = 0.35, knees = KNEES_FWD, over = 0.75, measure = shoulders, ankles, feet }) {
  balance((back) => {
    torso({ root: REST_ROOT().add(V3(0, -drop, -back)), pitch, flex, headPitch: headPitch ?? -pitch * 0.45 });
  }, measure, over);
  legs({ ankles: ankles || stance(width), poles: knees, feet: feet || [footQ(deg(8)), footQ(deg(-8))] });
}
