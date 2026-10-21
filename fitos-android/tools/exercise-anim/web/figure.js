// The figure: MakeHuman's CC0 body, clothes and hair skinned to one skeleton, a soft studio scene,
// and the posing tools every exercise uses (two-bone IK, torso, legs, arms, hands).
// Model space: y up, z forward (the way the body faces at rest), x toward the body's left.
// Units are decimetres; the floor is y = 0.
import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { GTAOPass } from 'three/addons/postprocessing/GTAOPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';

export { THREE };
export const V3 = (x = 0, y = 0, z = 0) => new THREE.Vector3(x, y, z);
export const X = V3(1, 0, 0), Y = V3(0, 1, 0), Z = V3(0, 0, 1);
export const deg = (d) => (d * Math.PI) / 180;
export const lerp = (a, b, t) => a + (b - a) * t;
export const lerpV = (a, b, t) => a.clone().lerp(b, t);
export const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
export const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
export const easeInOut = (t) => 0.5 - 0.5 * Math.cos(Math.PI * t);
export const easeOut = (t) => 1 - Math.pow(1 - t, 2.2);

const data = await (await fetch('../build/model.json')).json();
const FLOOR = 8.1867 + 0.25; // the soles of the trainers rest on the floor

// ---------- skeleton ----------
export const bones = [], B = {};
data.bones.forEach((b) => {
  const bone = new THREE.Bone(); bone.name = b.name;
  const head = V3(b.head[0], b.head[1] + FLOOR, b.head[2]);
  bone.userData = { head, tail: V3(b.tail[0], b.tail[1] + FLOOR, b.tail[2]) };
  if (b.parent) { B[b.parent].add(bone); bone.position.copy(head).sub(B[b.parent].userData.head); } else bone.position.copy(head);
  bones.push(bone); B[b.name] = bone;
});
const skeleton = new THREE.Skeleton(bones);
export const body = new THREE.Group(); body.add(bones[0]);
/** Where a joint is in the rest pose. */
export const head = (n) => B[n].userData.head;

// ---------- meshes ----------
const loader = new THREE.TextureLoader();
async function texture(path, srgb = true) {
  const t = await loader.loadAsync('../build/' + path);
  t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace; t.anisotropy = 4;
  return t;
}
/** Normals averaged over vertices split on UV seams, so seams do not show in the shading. */
function seamlessNormals(geo, orig) {
  const pos = geo.attributes.position, idx = geo.index.array;
  const acc = new Map(); const a = V3(), b = V3(), c = V3(), e1 = V3(), e2 = V3();
  for (let i = 0; i < idx.length; i += 3) {
    a.fromBufferAttribute(pos, idx[i]); b.fromBufferAttribute(pos, idx[i + 1]); c.fromBufferAttribute(pos, idx[i + 2]);
    const fn = e1.subVectors(b, a).cross(e2.subVectors(c, a));
    for (let k = 0; k < 3; k++) { const o = orig[idx[i + k]]; const s = acc.get(o) || V3(); s.add(fn); acc.set(o, s); }
  }
  const nor = new Float32Array(pos.count * 3);
  for (let i = 0; i < pos.count; i++) acc.get(orig[i]).clone().normalize().toArray(nor, i * 3);
  geo.setAttribute('normal', new THREE.BufferAttribute(nor, 3));
}
function geometry(p) {
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(p.positions, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(p.uvs, 2));
  g.setIndex(p.indices);
  g.setAttribute('skinIndex', new THREE.Uint16BufferAttribute(p.skinIndex, 4));
  g.setAttribute('skinWeight', new THREE.Float32BufferAttribute(p.skinWeight, 4));
  g.translate(0, FLOOR, 0);
  seamlessNormals(g, p.orig);
  return g;
}

export const SHIRT = 0x2f6fe4, SHORTS = 0x24272d;
for (const [name, p] of Object.entries(data.parts)) {
  const g = geometry(p);
  const std = (o) => new THREE.MeshStandardMaterial(o);
  let m;
  if (name === 'body') m = std({ map: await texture(p.map), color: 0xf0d9c6, roughness: 0.58 });
  else if (name === 'shirt' || name === 'shorts') {
    // A few millimetres of air between cloth and skin so skin never shows through.
    const P = g.attributes.position, N = g.attributes.normal, off = name === 'shirt' ? 0.05 : 0.035;
    for (let i = 0; i < P.count; i++) P.setXYZ(i, P.getX(i) + N.getX(i) * off, P.getY(i) + N.getY(i) * off, P.getZ(i) + N.getZ(i) * off);
    m = std({ color: name === 'shirt' ? SHIRT : SHORTS, normalMap: await texture(p.normalMap, false), roughness: 0.9, side: THREE.DoubleSide });
    m.normalScale.set(0.9, 0.9);
    if (name === 'shorts') {
      // The hem is cut on a level line of the rest pose rather than along the mesh's faces.
      const rest = new Float32Array(p.positions.length / 3); for (let i = 0; i < rest.length; i++) rest[i] = p.positions[i * 3 + 1];
      g.setAttribute('aRestY', new THREE.BufferAttribute(rest, 1));
      m.onBeforeCompile = (sh) => {
        sh.uniforms.uCut = { value: p.cutY };
        sh.vertexShader = sh.vertexShader.replace('#include <common>', '#include <common>\nattribute float aRestY;\nvarying float vRestY;').replace('#include <begin_vertex>', '#include <begin_vertex>\nvRestY = aRestY;');
        sh.fragmentShader = sh.fragmentShader.replace('#include <common>', '#include <common>\nvarying float vRestY;\nuniform float uCut;').replace('#include <clipping_planes_fragment>', '#include <clipping_planes_fragment>\nif (vRestY < uCut) discard;');
      };
    }
  } else if (name === 'shoes') m = std({ map: await texture(p.map), roughness: 0.62 });
  else if (name === 'hair') m = std({ map: await texture(p.map), color: 0x6b5446, normalMap: await texture(p.normalMap, false), roughness: 0.6, alphaTest: 0.45, side: THREE.DoubleSide });
  else if (name === 'brows' || name === 'lashes') m = std({ map: await texture(p.map), roughness: 0.8, transparent: true, alphaTest: 0.08, depthWrite: false, side: THREE.DoubleSide });
  else if (name === 'eyes') m = std({ map: await texture(p.map), roughness: 0.15, transparent: true, alphaTest: 0.02 });
  const mesh = new THREE.SkinnedMesh(g, m);
  mesh.castShadow = !['brows', 'lashes', 'eyes'].includes(name); mesh.receiveShadow = true; mesh.frustumCulled = false;
  if (name === 'brows' || name === 'lashes') mesh.renderOrder = 2;
  body.add(mesh);
}
body.updateMatrixWorld(true);
skeleton.calculateInverses();
body.children.filter((o) => o.isSkinnedMesh).forEach((m) => m.bind(skeleton, m.matrixWorld));

// ---------- scene ----------
export const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true, alpha: true });
renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.AgXToneMapping; renderer.toneMappingExposure = 1.15;
renderer.setClearColor(0x000000, 0);
document.body.appendChild(renderer.domElement);

export const scene = new THREE.Scene();
scene.add(body);
scene.environment = new THREE.PMREMGenerator(renderer).fromScene(new RoomEnvironment(renderer), 0.04).texture;
scene.add(new THREE.HemisphereLight(0xffffff, 0x8b8178, 0.35));
const key = new THREE.DirectionalLight(0xfff4ea, 1.9); key.position.set(-12, 38, 20); key.castShadow = true;
key.shadow.mapSize.set(2048, 2048); key.shadow.radius = 7; key.shadow.bias = -0.0004; key.shadow.normalBias = 0.03;
Object.assign(key.shadow.camera, { left: -26, right: 26, top: 30, bottom: -8, near: 1, far: 100 });
scene.add(key); scene.add(key.target);
const rim = new THREE.DirectionalLight(0xe6eeff, 0.7); rim.position.set(14, 16, -24); scene.add(rim);
const floor = new THREE.Mesh(new THREE.PlaneGeometry(400, 400), new THREE.ShadowMaterial({ opacity: 0.16 }));
floor.rotation.x = -Math.PI / 2; floor.receiveShadow = true; scene.add(floor);
export const camera = new THREE.PerspectiveCamera(22, 1, 1, 400);
/** Lights follow the camera's side so the figure is always lit from the front-left of the view. */
export function aimLights(az) {
  const r = (v, a) => V3(v.x * Math.cos(a) + v.z * Math.sin(a), v.y, -v.x * Math.sin(a) + v.z * Math.cos(a));
  key.position.copy(r(V3(-12, 38, 20), az + deg(55))); rim.position.copy(r(V3(14, 16, -24), az + deg(55)));
}
let composer = null, composerSize = 0;
/** Ambient occlusion samples; 0 draws without it. */
export const QUALITY = { ao: 16 };
export function draw(size) {
  if (!QUALITY.ao) { renderer.setSize(size, size, false); renderer.render(scene, camera); return; }
  if (composerSize !== size) {
    renderer.setSize(size, size, false);
    composer = new EffectComposer(renderer);
    composer.addPass(new RenderPass(scene, camera));
    const gtao = new GTAOPass(scene, camera, size, size);
    gtao.updateGtaoMaterial({ radius: 1.6, distanceExponent: 1.5, thickness: 1.0, scale: 1.0, samples: QUALITY.ao });
    gtao.blendIntensity = 0.85;
    composer.addPass(gtao);
    composer.addPass(new OutputPass());
    composer.setSize(size, size); composerSize = size;
  }
  composer.render();
}

// ---------- posing ----------
export const qWorld = (o) => o.getWorldQuaternion(new THREE.Quaternion());
export const pWorld = (o) => o.getWorldPosition(new THREE.Vector3());
export const tailWorld = (n) => B[n].localToWorld(B[n].userData.tail.clone().sub(B[n].userData.head));
export function setWorld(bone, q) { bone.quaternion.copy(qWorld(bone.parent).invert().multiply(q)); bone.updateMatrixWorld(true); }
export const qAxis = (axis, a) => new THREE.Quaternion().setFromAxisAngle(axis.clone().normalize(), a);
function frameQ(a, n) {
  const x = a.clone().normalize(); const y = n.clone().sub(x.clone().multiplyScalar(x.dot(n))).normalize(); const z = x.clone().cross(y);
  return new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, z));
}
/** Rotation taking rest direction a0 (with a second direction n0) to a (with n). */
export const mapQ = (a0, n0, a, n) => frameQ(a, n).multiply(frameQ(a0, n0).invert());
/** Rotation about y: the way a figure turned by yaw faces. */
export const yawQ = (a) => qAxis(Y, a);
export const turn = (v, yaw) => v.clone().applyQuaternion(yawQ(yaw));

export function rest() {
  bones.forEach((b) => { b.quaternion.identity(); b.position.copy(b.parent && b.parent.isBone ? b.userData.head.clone().sub(b.parent.userData.head) : b.userData.head); });
  body.updateMatrixWorld(true);
}

/**
 * Two-bone IK: bends upper (bones) and lower (bones) so `end` lands on target, the middle joint
 * pointing toward pole. restPole is the way that joint points in the rest pose.
 */
export function ik(upper, lower, end, target, pole, restPole) {
  const h0 = upper[0].userData.head, k0 = lower[0].userData.head, e0 = end.userData.head;
  const l1 = h0.distanceTo(k0), l2 = k0.distanceTo(e0);
  const a0 = k0.clone().sub(h0).normalize(), b0 = e0.clone().sub(k0).normalize();
  const n0 = a0.clone().cross(restPole.clone().negate()).normalize();
  const root = pWorld(upper[0]);
  const toT = target.clone().sub(root);
  const d = clamp(toT.length(), Math.abs(l1 - l2) + 0.01, l1 + l2 - 0.005);
  const u = toT.normalize();
  let v = pole.clone().sub(u.clone().multiplyScalar(u.dot(pole)));
  if (v.length() < 1e-4) v = restPole.clone().sub(u.clone().multiplyScalar(u.dot(restPole)));
  v.normalize();
  const a = (l1 * l1 - l2 * l2 + d * d) / (2 * d), h = Math.sqrt(Math.max(l1 * l1 - a * a, 0));
  const K = root.clone().add(u.clone().multiplyScalar(a)).add(v.clone().multiplyScalar(h));
  const E = root.clone().add(u.clone().multiplyScalar(d));
  const A = K.clone().sub(root).normalize(), Bd = E.clone().sub(K).normalize();
  let n = A.clone().cross(Bd); n = n.length() > 0.02 ? n.normalize() : A.clone().cross(v.clone().negate()).normalize();
  const RA = mapQ(a0, n0, A, n), RB = mapQ(b0, n0, Bd, n);
  upper.forEach((b) => setWorld(b, RA));
  lower.forEach((b) => setWorld(b, RB));
  return { joint: K, end: E, dir: Bd, bend: n };
}

const LEG = (s) => [[B['upperleg01.' + s], B['upperleg02.' + s]], [B['lowerleg01.' + s], B['lowerleg02.' + s]], B['foot.' + s]];
const ARM = (s) => [[B['upperarm01.' + s], B['upperarm02.' + s]], [B['lowerarm01.' + s], B['lowerarm02.' + s]], B['wrist.' + s]];
const SPINE = ['spine05', 'spine04', 'spine03', 'spine02', 'spine01'];
export const SIDES = ['L', 'R'];
export const ARM_LEN = head('upperarm01.L').distanceTo(head('lowerarm01.L')) + head('lowerarm01.L').distanceTo(head('wrist.L'));
export const LEG_LEN = head('upperleg01.L').distanceTo(head('lowerleg01.L')) + head('lowerleg01.L').distanceTo(head('foot.L'));
export const REST_ROOT = () => head('root').clone();
export const ANKLE = (s) => head('foot.' + s).clone();

/** Small signs of life laid over every pose: a sway of the hips, breathing, a nod of the head. */
export const LIFE = { sway: V3(), breath: 0, nod: 0 };

/**
 * The trunk: where the pelvis is and how it is turned (yaw about y, then pitch forward, then
 * roll to the side), a bend spread up the spine (forward flex, side bend, twist) and the head.
 */
export function torso({ root, yaw = 0, pitch = 0, roll = 0, flex = 0, side = 0, twist = 0, headPitch = 0, headYaw = 0, headRoll = 0, shrug = 0, retract = 0 }) {
  B.root.position.copy(root).add(LIFE.sway);
  B.root.quaternion.copy(yawQ(yaw).multiply(qAxis(X, pitch)).multiply(qAxis(Z, roll)));
  SPINE.forEach((s, i) => {
    B[s].quaternion.copy(qAxis(Y, twist / 5).multiply(qAxis(X, flex / 5 - (i >= 3 ? LIFE.breath : 0))).multiply(qAxis(Z, side / 5)));
  });
  const hp = headPitch + LIFE.nod;
  ['neck01', 'neck02', 'neck03', 'head'].forEach((s) => { B[s].quaternion.copy(qAxis(Y, headYaw / 4).multiply(qAxis(X, hp / 4)).multiply(qAxis(Z, headRoll / 4))); });
  // Shoulder blades: shrug lifts the shoulders, retract draws them back.
  B['clavicle.L'].quaternion.copy(qAxis(Z, shrug).multiply(qAxis(Y, retract)));
  B['clavicle.R'].quaternion.copy(qAxis(Z, -shrug).multiply(qAxis(Y, -retract)));
  body.updateMatrixWorld(true);
}

/** World direction of the trunk's axes after torso(): up the spine, forward out of the chest, to its left. */
export function chestFrame() {
  const q = qWorld(B.spine01);
  return { up: Y.clone().applyQuaternion(q), fwd: Z.clone().applyQuaternion(q), left: X.clone().applyQuaternion(q), q };
}
export const shoulder = (s) => pWorld(B['upperarm01.' + s]);
export const hip = (s) => pWorld(B['upperleg01.' + s]);
export const mid = (a, b) => a.clone().add(b).multiplyScalar(0.5);

/** A foot flat on the floor turned by yaw, or pitched (toes down) and rolled. */
export const footQ = (yaw = 0, pitch = 0, roll = 0) => yawQ(yaw).multiply(qAxis(X, pitch)).multiply(qAxis(Z, roll));
/**
 * Legs: IK each ankle to its target, the knee toward its pole. feet are world rotations for the
 * feet (null keeps the foot square to the shin); toes 'flat' lays the toes along the floor.
 */
export function legs({ ankles, poles, feet = [footQ(deg(8)), footQ(deg(-8))], toes = [null, null] }) {
  SIDES.forEach((s, i) => {
    if (!ankles[i]) return;
    const [u, l, f] = LEG(s);
    ik(u, l, f, ankles[i], poles[i], Z);
    if (feet[i]) setWorld(f, feet[i]);
    if (!toes[i]) return;
    const d = Z.clone().applyQuaternion(qWorld(f));
    const q = toes[i] === 'flat' ? yawQ(Math.atan2(d.x, d.z)) : toes[i];
    for (let t = 1; t <= 5; t++) setWorld(B[`toe${t}-1.${s}`], q);
  });
}

// Rest hand frame: h0 from the wrist toward the middle knuckle, p0 the way the palm faces.
const HAND = {};
SIDES.forEach((s) => {
  const W = head('wrist.' + s), M = head('finger3-1.' + s);
  const h0 = M.clone().sub(W).normalize();
  const tip = B['finger3-3.' + s].userData.tail.clone().sub(M);
  const p0 = tip.sub(h0.clone().multiplyScalar(tip.dot(h0))).normalize();
  const t0 = head('finger1-2.' + s).clone().sub(head('finger1-1.' + s)).normalize();
  HAND[s] = { h0, p0, curl: h0.clone().cross(p0).normalize(), thumbCurl: t0.clone().cross(p0).normalize() };
});
export function grip(s, amount = 1, thumb = 0.7) {
  const H = HAND[s];
  for (let f = 2; f <= 5; f++) for (let j = 1; j <= 3; j++) B[`finger${f}-${j}.${s}`].quaternion.copy(qAxis(H.curl, amount * deg([0, 62, 85, 55][j])));
  B[`finger1-2.${s}`].quaternion.copy(qAxis(H.thumbCurl, thumb * deg(30)));
  B[`finger1-3.${s}`].quaternion.copy(qAxis(H.thumbCurl, thumb * deg(40)));
  body.updateMatrixWorld(true);
}
const swingTwist = (q, axis) => { let a = 2 * Math.atan2(V3(q.x, q.y, q.z).dot(axis), q.w); if (a > Math.PI) a -= 2 * Math.PI; if (a < -Math.PI) a += 2 * Math.PI; return a; };
export const FIST = { along: 0.62, palm: 0.32 };

/**
 * One arm: IK to the wrist, the elbow toward pole, then the hand turned so the fingers point
 * along dir and the palm faces palm, with the forearm taking most of the turn so the wrist
 * does not wring. Returns the middle of the fist (where a handle sits) and the wrist.
 */
export function arm(s, { wrist, pole, dir, palm, grip: g = 1, fist }) {
  const [u, l, w] = ARM(s);
  if (fist) wrist = fist.clone().sub(dir.clone().normalize().multiplyScalar(FIST.along)).sub(palm.clone().normalize().multiplyScalar(g > 0.3 ? FIST.palm : 0.1));
  const r = ik(u, l, w, wrist, pole, V3(0, 0, -1));
  const H = HAND[s];
  const Rh = mapQ(H.h0, H.p0, dir, palm);
  const RB = qWorld(l[1]);
  const b0 = head('wrist.' + s).clone().sub(head('lowerarm01.' + s)).normalize();
  const tw = swingTwist(RB.clone().invert().multiply(Rh), b0);
  setWorld(l[0], qAxis(r.dir, 0.45 * tw).multiply(RB));
  setWorld(l[1], qAxis(r.dir, 0.9 * tw).multiply(RB));
  setWorld(w, Rh);
  grip(s, g, g > 0.3 ? 0.7 : 0.1);
  const wp = pWorld(w);
  return { fist: wp.clone().add(dir.clone().normalize().multiplyScalar(FIST.along)).add(palm.clone().normalize().multiplyScalar(g > 0.3 ? FIST.palm : 0.1)), wrist: wp, elbow: r.joint };
}

/** An arm left to hang: straight down from the shoulder, elbow soft, palm toward the thigh. */
export function hang(s, { forward = 0.25, out = 0.15, bend = 0.97 } = {}) {
  const sh = shoulder(s), sx = s === 'L' ? 1 : -1;
  const { fwd, left } = chestFrame();
  const f = V3(fwd.x, 0, fwd.z).normalize(), l = V3(left.x, 0, left.z).normalize();
  const wrist = sh.clone().add(V3(0, -ARM_LEN * bend, 0)).add(f.clone().multiplyScalar(forward)).add(l.clone().multiplyScalar(out * sx));
  return arm(s, { wrist, pole: f.clone().negate(), dir: V3(0, -1, 0).add(f.clone().multiplyScalar(0.15)), palm: l.clone().multiplyScalar(-sx), grip: 0.25 });
}

/** Feet flat, the way the figure stands at rest a little wider: the ankles' targets. */
export function stance(width = 0.35, yaw = 0, at = V3()) {
  return SIDES.map((s, i) => turn(ANKLE(s).add(V3((i ? -1 : 1) * width, 0, 0)), yaw).add(at));
}

/** Eyelids: 0 open, 1 closed. */
export function blink(closed) {
  SIDES.forEach((s) => {
    B['orbicularis03.' + s].quaternion.copy(qAxis(X, deg(34) * closed));
    B['orbicularis04.' + s].quaternion.copy(qAxis(X, -deg(9) * closed));
  });
  body.updateMatrixWorld(true);
}

/** Box around everything drawn: the bones and the equipment. */
export function bounds(extra) {
  const box = new THREE.Box3();
  bones.forEach((b) => { if (!/finger|toe|oris|levator|tongue|orbic|oculi|risor|tempor|special|jaw|eye|breast/.test(b.name)) box.expandByPoint(pWorld(b)); });
  box.expandByPoint(pWorld(B.head).add(V3(0, 1.6, 0)));
  box.min.y = Math.min(box.min.y, 0);
  box.expandByScalar(0.7);
  if (extra) { const e = new THREE.Box3().setFromObject(extra); if (!e.isEmpty()) box.union(e); }
  return box;
}
