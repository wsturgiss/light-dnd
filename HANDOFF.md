# Handoff: dice roll animation for light-dnd

## Context

`light-dnd` is a D&D 5e companion tool for the Light Phone III (LP3), built on the vendored Light SDK.
Read `AGENTS.md` and `README.md` first. Key facts:

- Kotlin + Compose, under `tool/src/main/kotlin/com/wsturgiss/dnd/`. The dice roller is `RollScreen.kt`;
  the roll model is `model/Dice.kt` (`DiceExpr`, `Roll`, `Edge`); results are logged as `LoggedRoll` in `model/Store.kt`.
- Three pixel fonts, always sized in whole font pixels via `ui/Type.kt`: MEK Sans (text), PxPlus IBM VGA 8x16
  (`Style.Mono*`/`Total`, all numbers), Jacquard 24 (`Style.Banner`, headings and names). Greyscale screen,
  1080×1240, density 3 (480 dpi).
- Build: `JAVA_HOME=/home/will/.jdks/jbr-21.0.11 ./gradlew -DlightSdk.toolOnly=true :tool:testDebugUnitTest :tool:assembleDebug`.
- Device: the LP3 over USB. `adb` is `/home/will/Projects/Android/platform-tools/adb`. Follow
  `~/Projects/AGENTS.md`: on every deploy check and re-lock rotation; launch with
  `adb shell am start -n com.wsturgiss.dnd/com.thelightphone.sdk.LightActivity`, **never `monkey`**.
- Don't commit unless Will asks (the repo has no commits yet). Don't edit `sdk/`.

## The task

Will wants a dice-roll animation in the roller. Decided so far:

- **Pre-rendered frames, not video, not live-drawn.** One short clip (~12–14 frames, ~0.6 s, easing from
  ~40 ms to ~150 ms per frame) per die face: 4+6+8+10+12+20 = 60 clips eventually; d100 = two d10s.
  Combinations (advantage = two d20 clips side by side; pools like 8d6 = several clips) are composed at roll
  time. Store as small greyscale PNG frame strips in `assets/`, drawn in Compose with no filtering
  (`FilterQuality.None`) at a whole-number scale so pixels stay square.
- Tap skips to the result; one haptic tick on landing; probably an on/off setting (Light values calm tools).
- **Right now Will only wants to compare two styles, one d20 each. Do not generate all 60.**

## Where it stands

Demo script: `/tmp/claude-1000/-home-will-Projects/28f76810-3c7d-4f67-a75b-1183b0719559/scratchpad/dice/demo.py`
(run with `uv run demo.py`; it uses Pillow + numpy and the VGA TTF at `../px/PxPlus_IBM_VGA_8x16.ttf`).
Both demos land on 17 with IBM VGA digits. Outputs are in the same folder:

- `A-flat-d20.gif` / `-strip.png`: flat pixel-art d20, 64 px. Geometry taken from a real face-on icosahedron:
  a hexagon silhouette with a 2 px rim, the front triangle at 0.62× its radius, and each triangle corner joined to
  the three nearest hexagon corners. It rocks and bounces while the number flickers, then sits upright on 17.
- `B-3d-d20.gif` / `-strip.png`: a real icosahedron rendered at 256 px and box-downsampled to 64 px. Three face
  greys with white 1 px edges. Only the front face is numbered, in crisp VGA digits with a 1 px black halo, so it
  is white-on-dark like A.
- `compare.html`: both GIFs at LP3 size (360 dp wide, 9 device px per frame pixel), click to replay.

## Decision (2026-10-09)

Will picked **A, the flat outline with upright numbers**: it suits LightOS. B is dropped. He noted that rotating
numbers only matter for a die that rotates convincingly, so A keeps them flat.

Because the number never rotates, it doesn't need to be baked in. The plan is now **one numberless tumble clip per die
type (6 total, not 60)**, with the result (and the flicker numbers) drawn at runtime in VGA over each frame. Each
frame carries the die's bounce offset so the number moves with it. `set.py` draws all six silhouettes, and
`set.html` previews them (d8 face-on shares the d20's hexagon outline; it's geometrically right).
The d10 (pentagonal trapezohedron, planar kites) and d12 are projected from real solids in `solids.py`, tilted
15° and 18° respectively: straight on, the d10 hides its lower faces and the d12 is a near-circular decagon.
`set-blank/` is a variant with no number until the die lands; Will asked to try it (toggle on `set.html`).

Later the same day: Will noticed that with the number hidden, an identical tumble every time is obvious. `vary.py` /
`vary.html` randomise each roll (spin direction and amount, rock-back, bounce count and height, sideways drift, frame
count) on an 80 px canvas so bounces don't clip. The number stays hidden while rolling and **appears all at once on
landing, with the haptic tick**. (A 0.5 s fade-in was tried; Will preferred it instant.) Dice are now R = 33 on an
80 px canvas so two VGA digits plus halo fit inside the d20's front triangle; the d4's edges stop 12 px short of
its number. `overlap.py` checks that no number covers an outline pixel on any face of any die.
Numbers are centred optically, not on their monospace cells: halfway between the ink bounding box and the pixel mass,
on the die's axis at the middle of pixel column cx (x = cx + 0.5). Cell centring had put every number 1 px left.
All six outlines are exactly mirror-symmetric when landed: corners are snapped past float noise, and inner lines use
a small rasteriser that breaks rounding ties away from the axis (Pillow's don't mirror). `centre_check.py` verifies.
Motion is now one continuous spin that eases out to upright (150–300°, 20–26 frames, ~1.3 s, steps under ~30° so
the hexagonal dice don't alias), not the old alternating rock, which read as shaking.

3D roll (`tumble.py`): every die is a real solid (`solids.py`; the d4 and d6 land as a plain front face, Will's
call: the tumble shows they're solids, and the earlier views read as see-through). A roll is thrown in from one
side (it may enter half off the edge), drops, bounces 2–3 times with decaying height, then rolls to rest centred.
Each impact kicks the spin onto a new random axis, biased to turn the way it travels, and slows it. The roll is a
spin in the screen plane matched to the travel (no skidding), and horizontal speed carries through every impact.
Fixed 45 ms frames in the air, slowing to ~90 ms over the roll; about 1.4 s in all, 30-ish frames.
Canvas 176 × 128, positioned by the die's centre. **No ground line**: the die lands facing you, which reads as a
view from above; beside a ground line some dice looked balanced on a point.
Camera modes (`CAMERA=travel|locked|zoom uv run tumble.py`; switch on `vary.html`): Will liked the motion and
bounce but not the die arriving from out of frame, so "locked" keeps the camera on the die's centre (104 × 104
canvas, motion shown only through the spin), and "zoom" is locked plus up to 15% bigger at the top of a bounce, as
if seen from above. The physics is unchanged across modes; only the drawing differs.
"follow" (Will liked the bounce-toward-you): from above, thrown in a random direction; impacts bias the spin to tip
forward the way it travels, and the roll tips it over its edges (about the table axis perpendicular to travel), not
a spin in place. A slightly underdamped spring camera chases it (never more than 14 px off-centre) and smoothsteps
onto it over the roll, so it lands dead centre. 132 × 132 canvas.
**travel-top** (`CAMERA=travel-top`): Will asked what perspective travel is. Its motion is side-on (bounces go up
the screen, the roll spins like a wheel) but it lands face-on, a top view, so it's mixed. travel-top is follow's
top-down motion with a fixed camera: thrown in from the left or right edge (±15°, each die ±15° more), bounces as
size, rolls tipping over the edges, resting centred; canvas 176 × 104 (two rows: 180 × 174).
**Full preview set** (`SETS` in tumble.py): every die at 1–4 of the same die, plus 1d100 and 2d100 (each d100 a
tens + units pair; 2d100 puts one pair per row), 8 rolls each, for all three cameras: 624 GIFs, no mixed sets.
travel-top keeps dice inside the frame vertically (its angled throws used to start above or below it).
**Will likes travel and follow, and wants the camera to be a user setting** (with "off" as well). Suggested
default: follow, which fits a compact square and lays out better for pools; travel needs a wide strip.
`tumble.py` now renders scenes of several dice (`scene([(sides, label), ...])`): each die has its own motion, all
thrown from one direction, resting side by side 72 px apart, each showing its number as it lands; one 45 ms clock
for all, with frames held where nothing moves. **d100 = tens d10 (00–90) + units d10 (0–9); 00 + 0 = 100.**
Multi-die canvases are capped at the LP3 width (180 px at 6 device px each), so follow's sideways slack shrinks
for a pair. The locked and zoom cameras were dropped.
**Opaque dice (Will: "FANTASTIC"), now the default**: each die's silhouette is filled black before its edges, so
overlapping dice hide each other; stacking order is fixed for the whole throw: in follow, by starting height (re-sorting per frame made dice swap layers mid-air, which Will flagged); in travel, back row first.
**Up to 4 dice animate together** (Will's limit); more than that, show the results without animation. Layout at full
size (dice can't shrink: two VGA digits only just fit a d20 face): 1–2 in a row; 3 = two over one; 4 = 2 × 2, rows
70 px apart. Two rows take ~190–198 of the screen's ~206 canvas px of height. Follow keeps every die in shot (each
travels its own distance, so one can run ahead of the group). Follow: each die heads up to 25° off the shared throw direction, and its final roll adds a 15–60° twist about the
vertical, because Will saw d6s all ending with the same motion. Experiment (`SPREAD=1 CAMERA=follow`, files `spread-*`): multi-dice start bunched within ±5 px, like one handful,
and smoothstep out to their layout by 85% of the first die's landing time. Awaiting Will's verdict.
Combination samples: 2d20, d20+d6+d4, 3d6,
d20+d12+d8+d4, 4d6. In the app these are composed live from any list; nothing is pre-rendered per combination. 
The motion is result-independent: every roll ends upright and face-on, and the number (from the app's own RNG,
decided at the tap) is drawn on the front face at landing. The faces are blank while rolling, so nothing contradicts it.
Checks: `landcheck.py` (last tumble pose = static landing drawing, pixel for pixel), `centre_check.py`
(symmetry and number centring), `overlap.py` (no number on any face touches an outline). Pixel rounding
matters: halves round down the screen vertically and away from the die's axis horizontally, because Python's
round-half-to-even depends on position. Outlines are drawn clockwise from the top corner.

## Next steps

1. ~~Polish B; tweak A.~~ Done 2026-10-09.
2. Show Will both GIFs, scaled up with crisp pixels, e.g. a small local HTML page with
   `image-rendering: pixelated` on a black background at LP3 width, or open the GIFs directly. Let him pick.
3. Only after he picks: generate the full set for that style, add a frame-strip player composable to the
   roller (`RollScreen.ResultArea` and `EdgeResult`), wire tap-to-skip and haptics, then build, install on the
   LP3 and check it there.

## In the app (2026-10-09, later)

The scripts live in `data/dice/` (`set.py`, `solids.py`, `tumble.py`, the checks); the copies in the old scratchpad
are stale. The app runs `scene()` itself, ported to Kotlin in `ui/DiceStage.kt` (`SceneShape`, `Scene`): rest
layout, the 45 ms shared clock, each die's position, the follow camera's spring, the clamps, stacking order, numbers
appearing as each die lands. **Change `scene()` and the port together.** What it can't do is draw the dice the way
set.py does, so `export.py` bakes each die's own motion as sprites into `tool/src/main/assets/dice/`:

- `top/d{n}-b{bin}-{v}.png`: from above (follow and travel-top share these), a throw heading at bin × 15°, 2 throws
  per bin. The app snaps each die's direction to the nearest bin.
- `side/d{n}-{r|l}-{v}.png`: travel, thrown right or left, 4 throws each.
- `faces/d{n}.png`, `pct10.png` (00–90), `pct1.png` (0–9): the landed die, transparent outside it.
- `index.json`: per throw, each frame's ms, distance still to travel, and bounce height; the scene constants.

Sprites are 104 px LA strips, the die opaque round the centre; `spritecheck.py` confirms that pasting them reproduces
draw_die / draw_landed exactly, over another die too (0 px). All throws start at most 84 px out (the single-die
limit), so in multi-die travel a die can start further off the edge than tumble.py would put it; the stage crops it.
About 4.6 MB in all. Not ported: `SPREAD` (the bunched-start experiment).

Layout (`RollScreen.Thrown`): the dice + modifier = total, packed and centred as one group; the label (and
"advantage" / "disadvantage" under it) always top centre of the result area, in one fixed place; `= total` only when there's arithmetic (a modifier or more
than one counted die); NATURAL 20/1 below. The dice scale to the landed group (up to 5×: 6× ran off the area mid-throw). Up to 4 dice animate (a
d100 is two, so 2d100 at most); more, or a die with no art (d3), shows the old text result. Advantage dims the dropped
d20. ADV/DIS follow 5e (Will's call): they apply only to a lone d20, plus any modifier. The keys dim whenever the
expression is anything else, and adding a non-d20 die clears a set ADV/DIS. No "roll the set twice" (Savage Attacker). The pad is two rows of six.
Camera setting: `SaveFile.diceCamera` (follow / travel / travel-top / off), cycled by "Dice: …" on the Roll log screen, shown as follow / bounce (side view, the code's Travel) / travel (from above, TravelTop) / off.
