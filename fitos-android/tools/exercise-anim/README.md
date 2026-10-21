# Exercise animations

This tool renders the looping 3D animation of every built-in exercise. The app bundles the result as
`app/src/main/assets/exercise_anim/<key>_light.mp4` and `<key>_dark.mp4`, one pair per key of
`ExerciseLibrary`.

Rendering happens here, ahead of time. The app only plays short videos, so it needs no 3D engine,
no internet and no extra permission.

## What it is made of

- **Figure:** the MakeHuman base mesh, shaped into a fit young man with MakeHuman's own targets
  and skinned to its default skeleton. It wears MakeHuman's skin, eyes, eyebrows, eyelashes and
  short hair, the T-shirt of `male_casualsuit06` (its jeans cut down to shorts) and `shoes05`.
  - Every one of these assets is released by MakeHuman under **CC0** (public domain), so no
    attribution or licence travels with the app.
  - The assets are downloaded at build time and are not stored in this repository.
- **Renderer:** [three.js](https://threejs.org) (MIT) in headless Chromium through Playwright.
  - Lighting: soft studio light from a room environment, one key light with soft shadows, and
    ambient occlusion.
  - Each frame is drawn with a transparent background and then laid on the stage colour of each
    app theme (`surfaceContainer`).
- **Motion:** each exercise is a short function in `web/exercises/`.
  - It poses the figure for a progress value `p`, with the start position at 0 and the other end
    of the movement at 1.
  - Shared tools in `web/figure.js` do the rest: two-bone IK for arms and legs, the trunk, hand
    grips, and small signs of life (breathing, a slight sway, a blink each loop).
  - Equipment is built from simple solids in `web/props.js`.
  - Timing follows `web/main.js`: a 3 s repetition with a slow lowering and a quicker lifting part.
  - One-sided movements alternate sides over 6 s, holds stay still, and cardio movements loop
    continuously.

## Regenerating

Run these from `tools/exercise-anim`. You need Node 18+, Python 3 with Pillow, and ffmpeg built
with libx264.

```sh
./fetch_assets.sh             # MakeHuman repository and the CC0 system asset pack into build/assets
python3 build_model.py        # build/model.json and build/tex/
npm install                   # three.js and Playwright
npx playwright install chromium
./render_all.sh               # build/frames/<key>/, then the videos into the app's assets
```

Useful while working on one movement:

```sh
node render.mjs --sheet back_squat lunge          # start, middle and end stills in build/sheets/
python3 contact.py build/sheets/review.png back_squat lunge
node render.mjs back_squat && python3 encode.py back_squat
```

Settings for `render.mjs` and `render_all.sh`:
- `--size` (default 480), `--fps` (default 30) and `--ao` (ambient occlusion samples, 0 = off).
- `CHROMIUM=/path/to/chrome` uses an installed browser.
- `JOBS=n` sets how many browsers `render_all.sh` runs in parallel (default 2), and `SIZE` its
  frame size (default 512, the size the app ships).
- `SKIP_DONE=1` skips exercises whose frames are already complete, so a stopped run can resume.
- `encode.py` skips videos that are newer than their frames.

## Adding an exercise

1. Add it to `ExerciseLibrary` in the app.
2. Add a function under the same key in the file of its group in `web/exercises/`.
3. Check it with `--sheet`, then render and encode it.

The app shows the animation for any built-in exercise whose video files exist.
