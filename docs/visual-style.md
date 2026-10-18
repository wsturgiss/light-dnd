# Visual style

What the app looks like and the rules that keep it that way. The code is `tool/src/main/kotlin/com/wsturgiss/dnd/ui/`:
type in `Type.kt`, page chrome in `Chrome.kt`, rules text in `Markup.kt`, dice in `DiceStage.kt` (see
`dice-animation.md`).

## The idea

A typed character sheet on a monochrome screen. The look comes from Script Wizards' typewriter sheets and their
athanor desktop: pixel type, ruled lines, square brackets for boxes, nothing shaded. It sits inside LightOS
rather than on top of it, so it keeps the system's bars, grid and theme, and the screen stays mostly empty.

- **Type does the work.** There are no icons, cards, shadows or fills in the content. Hierarchy comes from the
  typeface, its size and dimming.
- **Two colours.** Only the theme's `content` and `contentSecondary` ("dim"), on `background`. Nothing is
  hard-coded, so the UI follows the system theme. LightOS greys the whole screen anyway.
- **Every pixel on the grid.** The fonts are pixel fonts, and everything is drawn at whole-number scales, so no
  stroke is ever blurred or uneven.
- **Calm.** Motion only where it means something (the dice), one haptic tick, and every animation can be
  skipped with a tap or turned off.

## Type

Three faces, each with one job, plus an ornament font:

| Face | For | Never for |
|---|---|---|
| **MEK Sans** (MEK.txt) | Text: labels, menu items, rules text | Columns of numbers |
| **IBM VGA 8x16** (VileR's Oldschool PC Font Pack) | Numbers, columns, roll totals, pad keys, marks | Running text |
| **Jacquard 24** (Sarah Cadigan-Fried) | Headings and names: character name, section titles | Numbers: its digits are old-style |
| **MEK Dings** | The landscape strip on Home | Anything that needs reading |

### Sizes are in font pixels, not sp

`Type.kt` sizes each style as a whole-number scale of its font's pixel grid: at scale 4, each font pixel is
4×4 screen pixels. Fractional sizes would put strokes between screen pixels and blur them. The styles:

| Style | Face | Scale | Used for |
|---|---|---|---|
| `Banner` | Jacquard | 2 | Character name, `Section` headings, headings in rules text |
| `Heading` | MEK Sans | 5 | Home menu items, HP label |
| `Body` | MEK Sans | 4 | Most text: skills, spell names, empty states |
| `Small` | MEK Sans | 3 | Captions, column headers, rules text, keys |
| `Total` | IBM VGA | 10 | The roll result, read at a glance |
| `MonoBig` | IBM VGA | 5 | Big stats: HP, AC, save DC, the `+5 =` of a roll |
| `Mono` | IBM VGA | 4 | Values, scores, pad keys, the roll builder |
| `MonoSmall` | IBM VGA | 3 | Details beside a menu item or spell |
| `Ornament` | MEK Dings | 5 | The landscape |

Sizes go through `LocalDensity`, so the system font scale can't knock them off the grid. Add a size by picking
a new whole-number scale, never an sp value.

### Type rules

- **MEK Sans has one weight.** Bold runs in the SRD (trait and action names) are set in capitals instead of a
  fake bold.
- **Rules text is `Small`.** It runs long; the smaller size fits about twice the words per screen.
- **Only IBM VGA has box-drawing characters,** so rules and boxes are drawn in Compose, not typed.
- Monospaced values are padded (`padStart`) so signs and digits line up down a column.

## Layout

- **Every screen is a `Page`**: the LightOS top bar (back on the left, the title, the page's one action on the
  right, as an icon), then the content. Other actions live in the content; only Roll has a bottom bar, for the die. The SDK's bars use LightOS's type scale with MEK Sans in place of Akkurat, so they match the
  content.
- **Grid units, not dp.** Spacing is in LightOS grid units (the screen is 27 wide). Content has a 1.25-unit
  `GUTTER` either side.
- **Rows are tap targets.** `TapRow` is full width and at least 2.4 units tall. If something has a bonus, its
  row rolls it: abilities, saves, skills, initiative, spell attack, dice in rules text.
- **`KeyValueRow` is the sheet's basic shape**: a label on the left in `Body`, a value on the right in `Mono`.
- **Sections** are a Jacquard `Banner` over a 1 dp dim rule. Rules (`Rule`) are the only lines in the content.
- **Columns** are equal `weight`s with numbers right-aligned under right-aligned dim `Small` headers (the
  abilities table).
- **Home is a column of big words**, the way LightOS lays out its own menus: the character, a rule, five
  `Heading` menu items each with a `MonoSmall` dim detail on the right, and the landscape along the bottom.

## Marks and states

| Mark | Means | Where |
|---|---|---|
| `P` | Proficient | Saves and skills on the sheet |
| `E` | Expertise | Skills on the sheet |
| `[x]` / `[ ]` | On / off: a box you tap to change | Edit screen toggles |
| `[*]` | Expertise, as a toggle | Edit screen skills |
| `[x][x][ ][ ]` | Spell slots: spent on the left, what's left as empty boxes | Spells |
| underline | Tappable dice or attack bonus | Rules text |
| `›` after a label | Tap it (rolls or opens) | Stats in the sheet's top row, like `INIT ›` |

- **Dim is the only secondary state**: labels, captions, empty states, the dice not counted, a disabled key.
- **Selected is inverted**: a selected pad key fills with `content` and its label turns `background`.
- **Pad keys** are 1 dp dim boxes with a centred `Mono` label.

## Pixel art

The dice and the landscape follow the type's rules:

- **Flat outlines, no shading.** Dice are a 2 px white outline with dim 1 px inner edges. A shaded, downsampled
  3D render was tried and dropped.
- **Whole-number scales and `FilterQuality.None`**, so pixels stay square. Dice pick the biggest scale up to 5×
  that fits.
- **Pixel-exact.** Outlines are mirror-symmetric and numbers are centred optically, to the pixel.
- **Numbers on dice are IBM VGA**, the same face as every other number.

The details are in `dice-animation.md`.

## Adding to it

- A new screen is a `Page` or `ScrollPage` holding `TapRow`s, `Section`s and `T` text: no new chrome.
- A number goes in IBM VGA, a word in MEK Sans, a name or heading in Jacquard.
- A new size is a new whole-number scale in `Type.kt`.
- Colour comes from `LightThemeTokens.colors`, and only `content`, `contentSecondary` and `background`.
- Anything drawn is drawn on the font-pixel grid at a whole-number scale.
