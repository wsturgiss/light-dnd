# Dice roll animation

How the roller's dice animation looks, how it's made, and why. The art pipeline is in `data/dice/`; the app side is
`tool/src/main/kotlin/com/wsturgiss/dnd/ui/DiceStage.kt` and `RollScreen.kt`.

## The look

- **Flat pixel outlines**: a 2 px white silhouette with dim 1 px inner edges, on black. It suits LightOS's greyscale
  screen. A shaded, downsampled 3D render was tried and dropped.
- **Real solids.** Every die is projected from a real polyhedron (`solids.py`), so it tumbles convincingly and lands
  face-on. The d10 (a pentagonal trapezohedron) and d12 land tilted 15° and 18°: straight on, the d10 hides its lower
  faces and the d12 reads as a near-circle. The d4 and d6 land as a plain front face.
- **Opaque dice**: each silhouette is filled black before its edges, so overlapping dice hide each other.
- **Numbers stay upright** and are drawn in IBM VGA 8x16 with a 1 px black halo. Faces are blank while a die is
  moving; each die's number appears all at once as it lands (a fade-in was tried; instant is better).
- Dice are R = 33 px, so two VGA digits plus halo fit inside the d20's front triangle.
- **Pixel-exact.** Numbers are centred optically (halfway between the ink's bounding box and its pixel mass, on the
  middle of the die's axis column), not on their monospace cell, which put every number 1 px left. Landed outlines
  are exactly mirror-symmetric: corners snap past float noise, ties round away from the die's axis, and outlines are
  drawn clockwise from the top corner. Python's round-half-to-even depends on position, so halves are handled
  explicitly.

## The motion

A roll is thrown, drops, bounces two or three times with decaying height, then rolls to rest. Each impact kicks the
spin onto a new random axis, biased to turn the way the die is travelling, and slows it. Frames are 45 ms in the air,
slowing to about 90 ms over the final roll, so about 1.4 s in all. The motion doesn't depend on the result: every
throw ends upright and face-on, and the number (decided by the app's RNG when Roll is tapped) appears on landing.

Up to **4 dice** are thrown together on one clock. They rest side by side 72 px apart: one or two in a row, three as
two over one, four as two over two. A **d100 is two d10s**, tens (00–90) and units (0–9), where 00 + 0 reads as 100,
so 2d100 is the most percentile dice. Any roll with more dice than that, or a die with no art (a d3), shows the
plain text result instead.

### Cameras

A user setting, cycled by "Dice: …" on the Roll log screen (stored as `SaveFile.diceCamera`):

| Shown as | In code | What it is |
|---|---|---|
| follow | `Follow` | Seen from above. Thrown in any direction; bounces show as the die coming closer (up to 15% bigger) and the roll tips it over its edges. A slightly underdamped spring camera chases the middle of the dice, never letting them more than 14 px off-centre, and settles onto them over the last 0.7 s. |
| bounce | `Travel` | A fixed camera from the side: thrown in from the left or right edge, bouncing up the screen, rolling like a wheel. |
| travel | `TravelTop` | A fixed camera from above: follow's motion, thrown in from the left or right edge, give or take 15°. |
| off | `Off` | No animation; the text result. |

There's no ground line: a landed die faces you, which reads as a view from above, and beside a ground line some dice
looked balanced on a point. Stacking order is fixed for the whole throw (from above, the die highest at the start
goes on top; from the side, the back row first): re-sorting each frame made dice swap layers mid-air.

## How it's built

`data/dice/tumble.py` is the reference renderer: `scene([(sides, label), ...])` rolls up to four dice and returns
frames, and running it writes preview GIFs (`CAMERA=follow|travel|travel-top uv run tumble.py`, viewed in
`vary.html`). Pre-rendering every mix of up to four dice would be tens of MB per camera and every roll of a mix would
look the same, so the work is split:

- **The app runs the scene.** `DiceStage.kt` (`SceneShape`, `Scene`) is a port of `scene()`: the rest layout, the
  shared 45 ms clock, each die's position, the follow camera's spring, the clamps that keep dice in shot, the stacking
  order, and numbers appearing as each die lands. **Change `scene()` and the port together.**
- **Python bakes how each die looks.** `uv run export.py` writes `tool/src/main/assets/dice/` (about 4.6 MB):
  - `top/d{n}-b{bin}-{v}.png`: a throw seen from above (follow and travel share these), heading at bin × 15°, two
    throws per bin. The app snaps each die's direction to the nearest bin.
  - `side/d{n}-{r|l}-{v}.png`: a throw seen from the side (bounce), heading right or left, four throws each.
  - `faces/d{n}.png`, `pct10.png`, `pct1.png`: the landed die showing each number, transparent outside the die.
  - `index.json`: per throw, each frame's time, the distance still to travel and the bounce height, plus the scene
    constants.

  Sprites are 104 px greyscale-plus-alpha strips with the die opaque round the centre. All throws start at most 84 px
  out (the single-die limit), so in a multi-die bounce or travel roll a die can start a little further off the edge
  than `tumble.py` would put it; the stage crops it.

The app draws everything at a whole-number scale with `FilterQuality.None`, so pixels stay square.

### Checks (`uv run <script>`)

- `landcheck.py`: the unrotated 3D pose matches the static landing drawing pixel for pixel, and scenes don't shift
  as they land.
- `centre_check.py`: outline symmetry and number centring.
- `overlap.py`: no number, on any face of any die (including percentile labels), covers an outline pixel.
- `spritecheck.py`: exported sprites, pasted with their alpha, reproduce `draw_die` and `draw_landed` exactly, over
  another die too. Run it after `export.py`.

## In the roller

`RollScreen.Thrown` lays a roll out as a sum, read left to right: **dice + modifier = total**.

- The modifier is added once, to all the dice together, as in 5e.
- `= total` appears only when there's arithmetic: a modifier, or more than one die that counts. A lone die, or
  advantage with no modifier, is its own answer.
- The label (what was rolled: `d20+5`, `Stealth`), with "advantage" or "disadvantage" under it, is always top
  centre of the result area. `NATURAL 20` / `NATURAL 1` goes below the sum.
- The dice and the text beside them are packed and centred as one group. The landed dice set the scale (up to 5×; 6×
  ran off the area mid-throw); throws may cross the rest of the area under the hidden text. The total appears when
  the last die lands, with one haptic tick. Tap to skip.
- Advantage and disadvantage follow 5e: they apply only to a lone d20 (plus any modifier). Both d20s are thrown and
  the dropped one is dimmed. ADV/DIS keys dim when the expression is anything else. No "roll the set twice"
  (Savage Attacker).

The pad is two rows of six (`d4 … d20`, then `d100 ADV DIS -1 +1 +5`) to leave room for the dice.

## Not done

- `SPREAD=1` in `tumble.py`, an experiment where multi-dice start bunched like one handful and spread out, isn't in
  the app; it was awaiting a verdict.
- Something special for a natural 20 or 1: see `TODO.md`.
