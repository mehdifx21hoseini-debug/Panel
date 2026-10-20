# FitOS Personal — Design System "Electric Blue" (آبی پرانرژی)

Reference guideline: **Material 3 (Android)**, used for how tokens and components are divided.
The look: a confident electric-blue frame (actions and the Home header band with a curved edge),
white cards on a cool blue-grey canvas, warm orange for wins (completed days, PRs), and one bright
colour per module for data. Persian, RTL-first. **Light is the default
theme**; dark (deep navy, never pure black) is selectable (in the app: Settings → Appearance → light / dark / follow system).

## Files

| File | Role |
|---|---|
| `styles.css` | Single source of truth: tokens (`:root` light, dark overrides) + every component class |
| `index.html` | Living style guide: foundations, all components with variants/states, Dev Mode inspector, Copy-as-prompt |
| `concept.html` | Two concept screens (Home dashboard + Workout-day detail) built only from the system |
| `icons.html` | The icon sprite showcase (the same sprite is embedded in each page) |
| `theme.js` | Shared light/dark switch; starts light unless a choice was saved |
| `emoji/*.png` | Fluent 3D emoji (Microsoft, MIT licence) used as content markers |

## Layers

1. **Tokens** — colour roles (M3 names), module colours (data only), hero band, spacing (4dp + semantic roles),
   shape (`--shape-xs … --shape-full`), elevation 0–5, state layers, type families, motion, icons.
2. **Atomic / composed components** — the full Material 3 required set: buttons, icon buttons,
   FAB, segmented; badge, progress, snackbar, tooltip; card, carousel, divider, list, bottom sheet,
   dialog; top app bar, navigation bar, rail, drawer, tabs, search, menu; checkbox, chips, radio,
   switch, slider, date picker (Jalali), time picker; text field.
3. **FitOS extensions** — emoji tile, muscle map (front/back, 14 regions), plate meter (barbell),
   day tile, stat tile, weight chart, calorie chart, body journey, number stepper, rest timer,
   set pill, habit row, water cups.

## Rules

- Electric blue (`--primary`) is the only interaction family; reward orange (`--tertiary`) marks
  wins. Module colours (`--plate-*`, mapped through `--cat-*`: training orange, calories red,
  protein yellow, water sky, sleep violet, habits green) are for data only — never buttons or navigation.
- Every fill has its `--on-*` pair; all pairs were checked with the WCAG formula (≥ 4.5:1 for text,
  ≥ 3:1 for borders) in both themes.
- Vazirmatn (400/500/600) is the reading face for all text; Changa (500/600) only for display
  numerals. No letter-spacing on Persian. Minimum size 12.
- Elevation: hairline first, then soft blue-tinted shadow with alpha < .08; dark theme uses tonal surfaces plus
  a 1px top edge light.
- Emoji are content (module/habit identity, quality reactions), never UI icons. UI icons are the
  outlined vector sprite; directional glyphs carry `ico--dir` and mirror in RTL.
- Unlogged data is never shown as done (plate meter, habits, charts).

## How to extend

- **Add a token**: add the variable in the right group of `:root`, and its dark value in both dark
  blocks (`@media (prefers-color-scheme: dark)` and `:root[data-theme="dark"]`). Reference it by name.
- **Add a component**: add a class block in `styles.css` using tokens only, a
  `<section class="sec-wrap" id="<key>">` in `index.html` with every variant/state marked
  `data-insp="<key>"`, and a `SPECS["<key>"]` entry. The Copy-as-prompt button appears by itself.
- **Recolour a module**: change its `--cat-*` mapping (e.g. `--cat-water`), nothing else.
- **New icon**: add a `<symbol id="i-…" viewBox="0 0 24 24">` to the sprite in all three pages.
- **New muscle region**: add its shape to the sprite `<defs>`, a pair of `<use>` lines in the
  symbol (fill + hatch), and an `.mm--<region>` helper class.

## Mapping to Jetpack Compose

- Tokens → `ui/theme/`: `FitColors` (light + dark instances), `FitTypography` (15 M3 styles,
  bundled Vazirmatn + Changa), `FitShapes`, `FitSpacing`, `FitElevation`, exposed through
  `CompositionLocal`s inside `FitTheme`; `CompositionLocalProvider(LocalLayoutDirection provides Rtl)`.
- Components → `ui/components/` (Material 3 set) and `ui/fitos/` (extensions); one composable each.
- Fluent emoji → bundled PNGs in `res/drawable-nodpi/`; muscle map and body figures → `ImageVector`
  or Canvas paths using the same geometry as the sprite.

## Licences

- Fluent Emoji — © Microsoft, MIT licence (https://github.com/microsoft/fluentui-emoji).
- Vazirmatn — © Saber Rastikerdar, SIL Open Font License 1.1.
- Changa — © Eduardo Tunni, SIL Open Font License 1.1.
- Icons, muscle maps and body figures — original to this project.
