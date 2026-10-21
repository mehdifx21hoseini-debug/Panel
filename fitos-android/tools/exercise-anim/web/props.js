// Gym equipment for the exercise figure, built from simple solids.
import { THREE, V3, X, Y, Z, deg, scene } from './figure.js';

export const equipment = new THREE.Group();
scene.add(equipment);
export function clearEquipment() { equipment.clear(); }

const M = {};
const mat = (name, color, rough, metal = 0) => (M[name] ||= new THREE.MeshStandardMaterial({ color, roughness: rough, metalness: metal }));
export const MAT = {
  chrome: () => mat('chrome', 0xc3c8cf, 0.28, 0.85),
  plate: () => mat('plate', 0x3b4049, 0.55, 0.1),
  rubber: () => mat('rubber', 0x2a2d33, 0.85),
  pad: () => mat('pad', 0x343a44, 0.6),
  frame: () => mat('frame', 0x9aa1ab, 0.4, 0.55),
  dark: () => mat('dark', 0x4a515c, 0.45, 0.4),
  cable: () => mat('cable', 0x25282d, 0.5),
  band: () => mat('band', 0x2e9d6a, 0.55),
  wood: () => mat('wood', 0xc49a6c, 0.7),
  mat: () => mat('mat', 0x50607a, 0.9),
  wall: () => mat('wall', 0xdfe3e8, 0.95),
  belt: () => mat('belt', 0x2b2e33, 0.9),
};

/** Adds a solid to a group (or the scene's equipment) with shadows on. */
export function part(geo, material, at = V3(), rot = null, parent = equipment) {
  const m = new THREE.Mesh(geo, material); m.castShadow = true; m.receiveShadow = true;
  m.position.copy(at); if (rot) m.rotation.set(rot.x || 0, rot.y || 0, rot.z || 0);
  parent.add(m); return m;
}
export function group(at = V3(), yaw = 0, parent = equipment) { const g = new THREE.Group(); g.position.copy(at); g.rotation.y = yaw; parent.add(g); return g; }
const cyl = (r, h, seg = 20) => new THREE.CylinderGeometry(r, r, h, seg);
/** A rod from a to b. */
export function rod(a, b, r, material, parent = equipment) {
  const d = b.clone().sub(a);
  const m = part(cyl(r, d.length(), 14), material, a.clone().add(d.clone().multiplyScalar(0.5)), null, parent);
  m.quaternion.setFromUnitVectors(Y, d.normalize()); return m;
}
export const box = (w, h, d, at, material, parent = equipment) => part(new THREE.BoxGeometry(w, h, d), material, at, null, parent);
/** A padded block with rounded edges, centred at `at`. */
export function pad(w, h, d, at, parent = equipment, material = MAT.pad()) {
  const r = Math.min(0.4, h / 2 - 0.01), s = new THREE.Shape();
  s.moveTo(-w / 2 + r, -h / 2); s.lineTo(w / 2 - r, -h / 2); s.quadraticCurveTo(w / 2, -h / 2, w / 2, -h / 2 + r); s.lineTo(w / 2, h / 2 - r);
  s.quadraticCurveTo(w / 2, h / 2, w / 2 - r, h / 2); s.lineTo(-w / 2 + r, h / 2); s.quadraticCurveTo(-w / 2, h / 2, -w / 2, h / 2 - r); s.lineTo(-w / 2, -h / 2 + r); s.quadraticCurveTo(-w / 2, -h / 2, -w / 2 + r, -h / 2);
  const g = new THREE.ExtrudeGeometry(s, { depth: d, bevelEnabled: true, bevelSize: 0.16, bevelThickness: 0.16, bevelSegments: 4 });
  g.translate(0, 0, -d / 2);
  return part(g, material, at, null, parent);
}
const orient = (o, axis) => { o.quaternion.setFromUnitVectors(X, axis.clone().normalize()); return o; };

// ---------- free weights ----------
function plates(g, sign, x0, sizes) {
  sizes.reduce((x, [w, r]) => {
    part(cyl(r, w, 48), MAT.plate(), V3(sign * (x + w / 2), 0, 0), { z: Math.PI / 2 }, g);
    part(new THREE.TorusGeometry(r - 0.06, 0.07, 8, 48), MAT.plate(), V3(sign * (x + w / 2), 0, 0), { y: Math.PI / 2 }, g);
    part(cyl(0.6, w + 0.04, 24), MAT.dark(), V3(sign * (x + w / 2), 0, 0), { z: Math.PI / 2 }, g);
    return x + w + 0.03;
  }, x0);
}
/** An Olympic bar with plates, centred at `at`, lying along `axis`. */
export function barbell(at, { axis = X, r = 2.25, light = false } = {}) {
  const g = orient(group(at), axis);
  part(cyl(0.14, 22), MAT.chrome(), V3(), { z: Math.PI / 2 }, g);
  [-1, 1].forEach((s) => {
    part(cyl(0.25, 4.2), MAT.chrome(), V3(s * 8.9, 0, 0), { z: Math.PI / 2 }, g);
    part(cyl(0.42, 0.3, 24), MAT.chrome(), V3(s * 6.95, 0, 0), { z: Math.PI / 2 }, g);
    plates(g, s, 7.15, light ? [[0.45, r * 0.8]] : [[0.55, r], [0.42, r * 0.78]]);
  });
  return g;
}
/** An EZ bar: two bends where the hands go. */
export function ezBar(at, { axis = X } = {}) {
  const g = orient(group(at), axis);
  const pts = [[-6.3, 0], [-3.0, 0], [-2.4, -0.35], [-1.8, 0.2], [-1.2, -0.25], [-0.6, 0.15], [0, 0], [0.6, 0.15], [1.2, -0.25], [1.8, 0.2], [2.4, -0.35], [3.0, 0], [6.3, 0]].map(([x, z]) => V3(x, 0, z));
  part(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), 80, 0.14, 12), MAT.chrome(), V3(), null, g);
  [-1, 1].forEach((s) => {
    part(cyl(0.25, 3.0), MAT.chrome(), V3(s * 7.7, 0, 0), { z: Math.PI / 2 }, g);
    part(cyl(0.42, 0.3, 24), MAT.chrome(), V3(s * 6.3, 0, 0), { z: Math.PI / 2 }, g);
    plates(g, s, 6.5, [[0.45, 1.6]]);
  });
  return g;
}
/** A hex dumbbell; `axis` runs along the handle. */
export function dumbbell(at, axis = X, size = 1) {
  const g = orient(group(at), axis);
  part(cyl(0.16, 1.5), MAT.chrome(), V3(), { z: Math.PI / 2 }, g);
  [-1, 1].forEach((s) => part(cyl(0.62 * size, 0.62 * size, 6), MAT.rubber(), V3(s * (0.75 + 0.31 * size), 0, 0), { z: Math.PI / 2 }, g));
  return g;
}
/** A kettlebell hanging from its handle at `at`, the bell below along -up. */
export function kettlebell(at, up = Y, facing = Z) {
  const g = group(at);
  g.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(up.clone().cross(facing).normalize(), up.clone().normalize(), facing.clone().normalize()));
  part(new THREE.SphereGeometry(1.15, 32, 24), MAT.rubber(), V3(0, -1.55, 0), null, g);
  part(new THREE.TorusGeometry(0.7, 0.13, 12, 32, Math.PI), MAT.rubber(), V3(0, -0.55, 0), null, g);
  [-1, 1].forEach((s) => part(cyl(0.13, 0.6), MAT.rubber(), V3(s * 0.7, -0.85, 0), null, g));
  return g;
}
/** A resistance band through the given points. */
export function band(points, r = 0.09) {
  return part(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points), 48, r, 10), MAT.band());
}
/** A short handle bar (cable attachment) along axis. */
export function handleBar(at, axis = X, length = 4.6) { const g = orient(group(at), axis); part(cyl(0.15, length), MAT.chrome(), V3(), { z: Math.PI / 2 }, g); return g; }
/** A D handle: grip along axis with a loop toward `toward`. */
export function dHandle(at, axis, toward) {
  const g = group(at);
  g.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(axis.clone().normalize(), toward.clone().normalize(), axis.clone().cross(toward).normalize()));
  part(cyl(0.18, 1.2), MAT.rubber(), V3(), { z: Math.PI / 2 }, g);
  part(new THREE.TorusGeometry(0.75, 0.07, 8, 24, Math.PI), MAT.chrome(), V3(0, 0.05, 0), null, g);
  return g;
}

// ---------- benches and stands ----------
const post = (at, h, parent, r = 0.22) => part(cyl(r, h, 14), MAT.frame(), at.clone().add(V3(0, h / 2, 0)), null, parent);
const foot = (at, w, parent) => box(w, 0.3, 0.6, at.clone().add(V3(0, 0.15, 0)), MAT.frame(), parent);
/** A flat bench along z from z0 to z1. */
export function flatBench(top = 4.4, z0 = -6, z1 = 6, at = V3(), yaw = 0) {
  const g = group(at, yaw);
  pad(2.7, 0.9, z1 - z0, V3(0, top - 0.45, (z0 + z1) / 2), g);
  box(0.6, 0.35, z1 - z0 - 1, V3(0, top - 1.05, (z0 + z1) / 2), MAT.frame(), g);
  [z0 + 1.2, z1 - 1.2].forEach((z) => { post(V3(0, 0, z), top - 1.1, g); foot(V3(0, 0, z), 3.4, g); });
  return g;
}
/** An adjustable bench: seat around z = 0, back rising behind at `angle` from the floor. */
export function inclineBench(angle, seatTop = 4.6, at = V3(), yaw = 0, backLen = 8) {
  const g = group(at, yaw);
  pad(2.7, 0.85, 3.2, V3(0, seatTop - 0.42, 0.6), g);
  // The back hinges at the rear of the seat and rises behind it.
  const b = group(V3(0, seatTop - 0.45, -1.0), 0, g); b.rotation.x = angle;
  pad(2.6, 0.85, backLen, V3(0, 0.0, -backLen / 2), b);
  post(V3(0, 0, 0.6), seatTop - 0.9, g); foot(V3(0, 0, 0.6), 3.4, g);
  post(V3(0, 0, -3.6), Math.max(1.5, seatTop - 1), g); foot(V3(0, 0, -3.6), 3.4, g);
  rod(V3(0, 1.2, -3.6), V3(0, seatTop - 0.9, -0.2), 0.2, MAT.frame(), g);
  return g;
}
/** A bench with an upright back (shoulder press). */
export function uprightBench(seat = 4.6, at = V3(), yaw = 0) { return inclineBench(deg(84), seat, at, yaw, 7.4); }
/** A preacher bench: seat, and an angled arm pad in front. */
export function preacherBench(seat = 5.0, padTop = 9.6, padZ = 2.6, at = V3()) {
  const g = group(at);
  pad(3.0, 0.8, 2.8, V3(0, seat - 0.4, -0.6), g);
  post(V3(0, 0, -0.6), seat - 0.8, g); foot(V3(0, 0, -0.6), 3.6, g);
  const p = pad(4.2, 0.7, 3.4, V3(0, padTop - 1.4, padZ), g); p.rotation.x = deg(-48);
  post(V3(0, 0, padZ + 0.3), padTop - 2.6, g); foot(V3(0, 0, padZ + 0.3), 4.0, g);
  return g;
}
/** A plyo box or step. */
export function step(h = 4.5, at = V3(), w = 5, d = 4) { return box(w, h, d, at.clone().add(V3(0, h / 2, 0)), MAT.wood()); }
export function floorMat(at = V3(), w = 6.5, d = 19, yaw = 0) { const m = box(w, 0.1, d, at.clone().add(V3(0, 0.05, 0)), MAT.mat()); m.rotation.y = yaw; m.castShadow = false; return m; }
export function wall(z = -2, h = 22, w = 16) { const m = box(w, h, 0.6, V3(0, h / 2, z - 0.3), MAT.wall()); m.castShadow = false; return m; }
/** A pull-up bar at height y over z, held by two posts. */
export function pullUpBar(y = 22.5, z = 0, w = 13) {
  const g = group(V3(0, 0, z));
  part(cyl(0.17, w), MAT.chrome(), V3(0, y, 0), { z: Math.PI / 2 }, g);
  [-1, 1].forEach((s) => { box(0.6, y + 0.4, 0.6, V3(s * w / 2, (y + 0.4) / 2, 0), MAT.frame(), g); foot(V3(s * w / 2, 0, 0), 0.8, g).scale.set(1, 1, 6); });
  return g;
}
/** Parallel dip bars at height y, the hands' bars along z. */
export function dipBars(y = 12, half = 2.6, at = V3()) {
  const g = group(at);
  [-1, 1].forEach((s) => {
    part(cyl(0.2, 6.5), MAT.rubber(), V3(s * half, y, 0.5), { x: Math.PI / 2 }, g);
    [-2, 3].forEach((z) => { post(V3(s * half, 0, z), y, g); foot(V3(s * half, 0, z), 0.8, g); });
  });
  return g;
}

// ---------- cables ----------
/** A cable tower behind/beside the figure: a column and a pulley at `pulley`, cable to `to`. */
export function cableTower(pulley, to, { column = true, wide = false } = {}) {
  if (column) {
    const base = V3(pulley.x + (wide ? 0 : 0), 0, pulley.z - 1.2);
    box(1.0, 22, 1.0, base.clone().add(V3(0, 11, 0)), MAT.frame());
    box(2.8, 7, 1.6, base.clone().add(V3(0, 3.7, -1.3)), MAT.plate());
    box(3.4, 0.3, 4.2, base.clone().add(V3(0, 0.15, -0.6)), MAT.frame());
  }
  part(cyl(0.55, 0.32, 24), MAT.dark(), pulley, { z: Math.PI / 2 });
  rod(pulley, to, 0.05, MAT.cable());
}
/** A rope attachment: two tails ending in the hands, hanging from `top`. */
export function rope(top, hands) {
  hands.forEach((h) => band([top, top.clone().lerp(h, 0.5).add(V3(0, -0.15, 0)), h], 0.16).material = MAT.rubber());
  part(new THREE.SphereGeometry(0.25, 12, 8), MAT.chrome(), top);
}

// ---------- machines ----------
/** A seat with a back pad, seat top at y; back at `backZ`, leaning back by `lean`. */
export function seat(y = 4.6, at = V3(), { back = true, backZ = -1.9, lean = deg(8), backH = 7, w = 3.0 } = {}) {
  const g = group(at);
  pad(w, 0.8, 3.4, V3(0, y - 0.4, -0.2), g);
  post(V3(0, 0, -0.2), y - 0.8, g, 0.3); foot(V3(0, 0, -0.2), 3.6, g);
  if (back) { const b = pad(w * 0.9, 0.8, backH, V3(0, y + backH / 2 - 0.2, backZ), g); b.rotation.x = -Math.PI / 2 + lean; }
  return g;
}
/** A weight stack with its frame, centred on x, standing at z. */
export function stack(z, x = 0, h = 20) {
  [-1.7, 1.7].forEach((dx) => box(0.5, h, 0.5, V3(x + dx, h / 2, z), MAT.frame()));
  box(3.9, 0.5, 0.6, V3(x, h, z), MAT.frame());
  for (let k = 0; k < 10; k++) box(2.8, 0.5, 1.2, V3(x, 0.6 + k * 0.62, z), MAT.plate());
  box(4.4, 0.3, 2.4, V3(x, 0.15, z), MAT.frame());
}
/** A lever arm that pivots at `pivot`, reaching to `end` where a handle or pad is. */
export function lever(pivot, end, r = 0.3) { rod(pivot, end, r, MAT.dark()); part(cyl(0.45, 0.6, 20), MAT.dark(), pivot, { z: Math.PI / 2 }); }
/** A roller pad (leg machines) along x at `at`. */
export function roller(at, w = 3.2) { part(cyl(0.55, w, 24), MAT.pad(), at, { z: Math.PI / 2 }); }

// ---------- cardio ----------
export function treadmill(phase, at = V3()) {
  const g = group(at);
  box(6.2, 0.9, 17, V3(0, 0.75, 0), MAT.dark(), g);
  box(5.0, 0.12, 16.2, V3(0, 1.26, 0), MAT.belt(), g);
  for (let k = 0; k < 8; k++) box(5.0, 0.13, 0.08, V3(0, 1.27, -8 + ((k * 2 + phase * 2 * 1.9) % 16)), MAT.rubber(), g);
  [-2.9, 2.9].forEach((x) => { rod(V3(x, 1.2, 7.4), V3(x, 11.5, 8.6), 0.25, MAT.frame(), g); rod(V3(x, 10.5, 8.5), V3(x, 10.5, 4.5), 0.2, MAT.frame(), g); });
  box(6.4, 2.4, 1.2, V3(0, 12.2, 8.7), MAT.dark(), g).rotation.x = deg(-25);
  return g;
}
export function bike(crank, at = V3(), seatY = 9.6) {
  const g = group(at);
  box(1.0, 0.6, 9, V3(0, 0.3, 0), MAT.frame(), g);
  rod(V3(0, 0.5, -2.6), V3(0, seatY - 0.5, -3.4), 0.35, MAT.frame(), g);
  rod(V3(0, 0.5, 2.6), V3(0, 10.8, 3.6), 0.35, MAT.frame(), g);
  pad(2.2, 0.7, 2.8, V3(0, seatY, -3.4), g);
  part(cyl(0.2, 5.5), MAT.rubber(), V3(0, 11.0, 4.4), { z: Math.PI / 2 }, g);
  part(cyl(2.2, 1.0, 40), MAT.dark(), V3(0, 3.6, 1.6), { z: Math.PI / 2 }, g);
  return g;
}
export function elliptical(at = V3()) {
  const g = group(at);
  box(4.0, 0.8, 16, V3(0, 0.4, -1), MAT.dark(), g);
  part(cyl(2.4, 1.6, 40), MAT.dark(), V3(0, 3.0, -7.6), { z: Math.PI / 2 }, g);
  rod(V3(0, 0.6, 6.4), V3(0, 11, 7.0), 0.35, MAT.frame(), g);
  box(4.4, 1.6, 1.0, V3(0, 11.6, 7.2), MAT.dark(), g).rotation.x = deg(-20);
  return g;
}
export function rower(at = V3()) {
  const g = group(at);
  box(1.2, 0.5, 20, V3(0, 1.6, -2), MAT.chrome(), g);
  [[-11, 1.4], [7.4, 1.4]].forEach(([z, h]) => box(3.6, h, 1.0, V3(0, h / 2, z), MAT.frame(), g));
  part(cyl(2.4, 2.0, 40), MAT.dark(), V3(0, 3.2, 9.5), { z: Math.PI / 2 }, g);
  return g;
}
