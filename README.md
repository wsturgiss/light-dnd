# light-dnd

A D&D 5e companion for the Light Phone III: dice, one character sheet, a bag,
spell slots, and the SRD 5.2 for looking things up at the table. Everything is
on the phone; it never touches the network.

Typeset in MEK Sans, IBM VGA 8x16 and Jacquard 24, after Script Wizards'
typewriter sheets and their athanor desktop.

## What's in it

| Screen | Does |
|---|---|
| **Home** | Name, HP, last roll; Roll / Sheet / Bag / Spells / Look up |
| **Roll** | Tap dice and modifiers to build `2d6+3`, then Roll. ADV/DIS for a d20. Log of the last 50 |
| **Sheet** | Scores, saves, skills, AC, initiative, conditions. Tap anything with a bonus to roll it. HP opens a Damage / Heal / Temp pad |
| **Edit** | Every sheet field, proficiency toggles, spell slots (or fill from the full-caster table) |
| **Rest** | Long rest restores HP and slots; short rest rolls a Hit Die |
| **Bag** | Coins (add / spend / set), items with quantity, weight carried. Add from SRD gear or type a name |
| **Spells** | Save DC and attack, slots to spend and regain, your starred spells by level |
| **Look up** | Spells (filter by class and level), monsters (by CR), conditions, magic items, equipment, rules |

Dice in rules text are tappable: `8d6` in Fireball rolls 8d6; `Attack Roll: +4`
on a stat block rolls d20+4. Every roll lands in the log.

## Layout

- `tool/` — the tool (Kotlin, Compose, light-sdk).
  - `model/` — `Dice` (parser + roller, unit-tested), `Character` (5e math),
    `Store` (one JSON file), `Srd` (bundled lists).
  - `ui/` — type (`Type.kt`), page chrome (`Chrome.kt`), rules markup with
    tappable dice (`Markup.kt`).
- `data/build_srd.py` — converts the SRD JSON into `tool/src/main/assets/srd/`.
  The output is checked in: Light's builder only copies `src/main`.
- `sdk/` and the rest of the root — vendored `light-sdk`. Read-only.

## Build

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew -DlightSdk.toolOnly=true :tool:testDebugUnitTest :tool:assembleDebug
adb install -r tool/build/outputs/apk/debug/tool-debug.apk
```

`serverPackage` in `tool/lighttool.toml` is `com.lightos` for a real LP3; use
`com.thelightphone.sdk.emulator` for the LightOS emulator.

Refresh the SRD data (only needed if 5e-bits changes it):

```bash
./data/build_srd.py
```

## Type

All the fonts are pixel fonts, so `Type.kt` sizes them in whole font pixels
(scale 3, 4, 5…) rather than sp, which keeps every stroke on the screen's pixel
grid. MEK Sans for text, IBM VGA 8x16 for numbers, columns and roll totals,
Jacquard 24 for headings and names (never numbers: its digits are old-style),
MEK Dings for the landscape strip. Only IBM VGA has box-drawing characters, so rules and boxes are drawn in Compose.

## Saved data

`filesDir/character.json`: the character, the bag and the roll log, written
through a temp file and a rename. A fresh install starts with a sample rogue;
**Edit › Start over** clears it.

## Credits and licences

- Rules text: SRD 5.2 by Wizards of the Coast, CC-BY-4.0 (attribution is on
  the tool's About screen), via [5e-bits/5e-database](https://github.com/5e-bits/5e-database) (MIT).
- Fonts: MEK Sans, MEK Dings by [MEK.txt](https://www.mek.gallery/mektype).
  MEK Sans is stated CC0 (MEK's letter with it). PxPlus IBM VGA 8x16 from VileR's
  [Oldschool PC Font Pack](https://int10h.org/oldschool-pc-fonts/), CC BY-SA 4.0,
  unmodified (modifying it would bring in share-alike). Jacquard 24 by Sarah Cadigan-Fried is SIL OFL 1.1; its licence ships
  in `assets/licenses/`, as does the font pack's. MEK Dings ships without a licence file; MEK's letter says
  "the other ones" are a gift too, but **confirm with MEK before submitting to
  Light's Tool Library**.
