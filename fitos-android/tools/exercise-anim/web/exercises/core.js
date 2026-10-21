// Core exercises.
import { V3, X, Y, Z, deg, lerp, arm, hang, sit, stand, supine, shoulders, hips, bothFists, medial, SX, ARM_LEN, chestFrame, legs, footQ, torso, REST_ROOT, pWorld, B, mid, plank, liftFor, placeShoulders, hangingLegs, setWorld, qWorld, qAxis } from './common.js';
import { THREE, smooth, LEG_LEN } from '../figure.js';
import { floorMat, pullUpBar, cableTower, rope, part, rod, MAT, box } from '../props.js';

const SIDES = ['L', 'R'];
const UPPER = () => pWorld(B['upperarm01.L']).distanceTo(pWorld(B['lowerarm01.L']));

/** Lying face up on a mat, knees bent, feet flat. */
function onBack({ flex = 0, headPitch = 0, hipZ = 1.2, feet = true } = {}) {
  supine({ top: 0.1, hipZ, feet: 'none', flex, headPitch });
  if (feet) legs({ ankles: [V3(1.6, 0.82, hipZ + 4.0), V3(-1.6, 0.82, hipZ + 4.0)], poles: [V3(0.2, 1, 0.3), V3(-0.2, 1, 0.3)], feet: [footQ(deg(8)), footQ(deg(-8))] });
}
const handsBehindHead = () => SIDES.forEach((s) => {
  const h = pWorld(B.head), { up, left, fwd } = chestFrame();
  arm(s, { wrist: h.clone().add(left.clone().multiplyScalar(SX[s] * 0.7)).add(up.clone().multiplyScalar(0.2)).add(fwd.clone().multiplyScalar(-0.9)), pole: left.clone().multiplyScalar(SX[s]).add(fwd.clone().multiplyScalar(0.3)), dir: left.clone().multiplyScalar(-SX[s]).add(up.clone().multiplyScalar(0.5)).normalize(), palm: fwd.clone(), grip: 0.3 });
});
const armsAlongFloor = () => SIDES.forEach((s) => { const sh = pWorld(B['upperarm01.' + s]); arm(s, { wrist: V3(SX[s] * 2.6, 0.45, sh.z + 4.6), pole: V3(SX[s] * 0.3, 1, 0), dir: Z, palm: V3(0, -1, 0), grip: 0.1 }); });
/** Kneeling: knees on the floor at z = kz, shins back, hips over or ahead of the knees by angle a. */
function kneel(kz, a, { pitch = 0, flex = 0, headPitch = 0 } = {}) {
  const thigh = pWorld(B['upperleg01.L']).distanceTo(pWorld(B['lowerleg01.L']));
  torso({ root: V3(0, 0.55 + thigh * Math.cos(a) + 0.3, kz + thigh * Math.sin(a) - 0.4), pitch, flex, headPitch });
  legs({ ankles: [V3(1.3, 0.75, kz - 4.1), V3(-1.3, 0.75, kz - 4.1)], poles: [V3(0, -1, 0.2), V3(0, -1, 0.2)], feet: [footQ(0, deg(78)), footQ(0, deg(78))] });
}

export default {
  plank: {
    view: { az: -62, el: 12 }, mode: 'hold',
    pose() {
      const shY = 0.45 + UPPER() + 0.35;
      const lift = liftFor(shY, {}, 'fa');
      const sh = plank(lift);
      SIDES.forEach((s) => arm(s, { wrist: V3(SX[s] * 0.9, 0.42, sh.z + 2.8), pole: V3(SX[s] * 0.2, -1, -0.4), dir: V3(-SX[s] * 0.3, 0, 1).normalize(), palm: V3(-SX[s], -0.3, 0).normalize(), grip: 0.7 }));
      floorMat(V3(0, 0, -3.5), 6.5, 20);
    },
  },

  side_plank: {
    view: { az: -6, el: 8 }, mode: 'hold',
    pose() {
      // On the right forearm and the side of the right foot; the body is a straight slope.
      const lift = deg(13.5);
      torso({ root: REST_ROOT(), roll: Math.PI / 2 - lift, headRoll: deg(6) });
      const footR = pWorld(B['foot.R']);
      B.root.position.add(V3(5.6 - footR.x, 0.55 - footR.y, 0)); B.root.updateMatrixWorld(true);
      SIDES.forEach((s) => setWorld(B['foot.' + s], qWorld(B['lowerleg02.' + s])));
      const shR = pWorld(B['upperarm01.R']);
      arm('R', { wrist: V3(shR.x + 0.3, 0.42, shR.z + 2.7), pole: V3(0, -1, -0.2), dir: Z, palm: V3(0, -1, 0), grip: 0.5 });
      const h = pWorld(B['upperleg01.L']), { up, left, fwd } = chestFrame();
      arm('L', { wrist: h.clone().add(left.clone().multiplyScalar(1.2)).add(up.clone().multiplyScalar(1.5)), pole: left.clone().add(fwd.clone().multiplyScalar(-0.5)), dir: up.clone().negate(), palm: left.clone().negate(), grip: 0.2 });
      floorMat(V3(0, 0, 0.5), 21, 6.5);
    },
  },

  crunch: {
    view: { az: -72, el: 12 }, concentric: 'go',
    pose(p) { onBack({ flex: deg(lerp(0, 42, p)), headPitch: deg(lerp(4, 14, p)) }); handsBehindHead(); floorMat(V3(0, 0, -1), 6.5, 19); },
  },

  hanging_leg_raise: {
    view: { az: -72, el: 6 }, concentric: 'go',
    pose(p) {
      const barY = 23.6;
      placeShoulders(V3(0, barY - ARM_LEN * 0.97 - 0.85, -0.3), { pitch: deg(lerp(0, -10, p)), flex: deg(lerp(0, 8, p)) });
      const a = deg(lerp(4, 96, p));
      SIDES.forEach((s, i) => {
        const h = pWorld(B['upperleg01.' + s]);
        legs({ ankles: i ? [null, h.clone().add(V3(0, -Math.cos(a), Math.sin(a)).multiplyScalar(LEG_LEN * 0.965))] : [h.clone().add(V3(0, -Math.cos(a), Math.sin(a)).multiplyScalar(LEG_LEN * 0.965)), null], poles: [V3(0, 0.3, 1), V3(0, 0.3, 1)], feet: [footQ(deg(4), deg(25) - a * 0.2), footQ(deg(-4), deg(25) - a * 0.2)] });
      });
      SIDES.forEach((s) => arm(s, { fist: V3(SX[s] * 3.0, barY, 0), pole: V3(SX[s], -0.6, -0.3), dir: Y, palm: Z }));
      pullUpBar(barY, 0);
    },
  },

  lying_leg_raise: {
    view: { az: -72, el: 12 }, concentric: 'go',
    pose(p) {
      onBack({ feet: false });
      const a = deg(lerp(8, 88, p));
      const ankles = SIDES.map((s) => pWorld(B['upperleg01.' + s]).add(V3(SX[s] * 0.2, Math.sin(a) * LEG_LEN * 0.97, Math.cos(a) * LEG_LEN * 0.97)));
      const knee = V3(0, Math.cos(a), -Math.sin(a));
      legs({ ankles, poles: [knee, knee], feet: [footQ(deg(4), -a + deg(20)), footQ(deg(-4), -a + deg(20))] });
      armsAlongFloor();
      floorMat(V3(0, 0, -1), 6.5, 19);
    },
  },

  cable_crunch: {
    view: { az: -72, el: 8 }, concentric: 'go',
    pose(p) {
      kneel(0, deg(lerp(10, 22, p)), { pitch: deg(lerp(24, 54, p)), flex: deg(lerp(6, 48, p)), headPitch: deg(lerp(-6, 6, p)) });
      handsBehindHead();
      const pts = SIDES.map((s) => pWorld(B['wrist.' + s]));
      const pulley = V3(0, 20.5, 6.5);
      const top = mid(pts[0], pts[1]).lerp(pulley, 0.08);
      rope(top, pts);
      cableTower(pulley, top);
      floorMat(V3(0, 0, -1), 6.5, 10);
    },
  },

  ab_wheel: {
    view: { az: -72, el: 10 },
    pose(p) {
      kneel(-3.0, deg(lerp(14, 58, p)), { pitch: deg(lerp(48, 86, p)), flex: deg(lerp(14, 0, p)), headPitch: deg(-16) });
      const sh = shoulders(), A = ARM_LEN * 0.97 + 0.6, dy = sh.y - 1.1;
      const wheel = V3(0, 0.95, sh.z + Math.sqrt(Math.max(0.5, A * A - dy * dy)));
      const d = wheel.clone().sub(sh).normalize();
      SIDES.forEach((s) => arm(s, { fist: wheel.clone().add(V3(SX[s] * 0.75, 0, 0)), pole: V3(SX[s] * 0.2, 1, -0.3), dir: d, palm: V3(0, -d.z, d.y), grip: 0.9 }));
      part(new THREE.CylinderGeometry(0.95, 0.95, 0.7, 40), MAT.rubber(), wheel, { z: Math.PI / 2 });
      rod(wheel.clone().add(V3(-1.3, 0, 0)), wheel.clone().add(V3(1.3, 0, 0)), 0.16, MAT.chrome());
      floorMat(V3(0, 0, 1), 6.5, 18);
    },
  },

  russian_twist: {
    view: { az: -20, el: 10 }, mode: 'loop', seconds: 3, cycles: 2,
    pose(ph) {
      const tw = deg(34) * Math.sin(2 * Math.PI * ph);
      torso({ root: V3(0, 1.05, -0.6), pitch: deg(-38), flex: deg(14), twist: tw, headYaw: tw * 0.3, headPitch: deg(14) });
      legs({ ankles: [V3(1.1, 2.2, 5.6), V3(-1.1, 2.2, 5.6)], poles: [V3(0.3, 1, 0), V3(-0.3, 1, 0)], feet: [footQ(deg(10), deg(20)), footQ(deg(-10), deg(20))] });
      const { up, fwd } = chestFrame();
      const c = shoulders().add(fwd.clone().multiplyScalar(2.8)).add(up.clone().multiplyScalar(-2.3));
      bothFists(SIDES.map((s) => c.clone().add(V3(SX[s] * 0.25, 0, 0))), { dirs: [fwd, fwd], palms: [medial('L'), medial('R')], poles: [V3(0.5, -1, -0.2), V3(-0.5, -1, -0.2)], grip: 0.6 });
      floorMat(V3(0, 0, 1), 6.5, 14);
    },
  },

  dead_bug: {
    view: { az: -72, el: 16 }, mode: 'alt',
    pose(p, side = 'L') {
      onBack({ feet: false, hipZ: 0.6 });
      // Arms up and hips and knees at right angles; the opposite arm and leg reach out.
      const other = side === 'L' ? 'R' : 'L';
      SIDES.forEach((s) => {
        const h = pWorld(B['upperleg01.' + s]);
        const out = s === other ? p : 0;
        const knee = h.clone().add(V3(SX[s] * 0.3, lerp(4.1, 1.2, out), lerp(0.4, 3.8, out)));
        const ankle = knee.clone().add(V3(0, lerp(0, -0.4, out), lerp(4.2, 4.3, out)));
        legs({ ankles: s === 'L' ? [ankle, null] : [null, ankle], poles: [V3(0, 1, 0.2), V3(0, 1, 0.2)], feet: [footQ(0, deg(-20)), footQ(0, deg(-20))] });
        const sh = pWorld(B['upperarm01.' + s]), a = deg(lerp(0, 82, s === side ? p : 0));
        arm(s, { wrist: sh.clone().add(V3(SX[s] * 0.4, Math.cos(a) * ARM_LEN * 0.96, -Math.sin(a) * ARM_LEN * 0.96)), pole: V3(SX[s], 0, 0), dir: V3(0, Math.cos(a), -Math.sin(a)), palm: medial(s), grip: 0.2 });
      });
      floorMat(V3(0, 0, -1), 6.5, 19);
    },
  },
};
