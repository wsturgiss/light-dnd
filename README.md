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
| **Roll** | Tap dice and modifiers to build `2d6+3`, then Roll; the dice are thrown on screen ([how](docs/dice-animation.md)). ADV/DIS for a d20. Log of the last 50 |
| **Sheet** | Scores, saves, skills, AC, initiative, conditions. Tap anything with a bonus to roll it. HP opens a Damage / Heal / Temp pad |
| **Edit** | Every sheet field, proficiency toggles, spell slots (or fill from the full-caster table) |
| **Rest** | Long rest restores HP and slots; short rest rolls a Hit Die |
| **Bag** | Coins (add / spend / set), items with quantity, weight carried. Add from SRD gear or type a name |
| **Spells** | Save DC and attack, slots to spend and regain, your starred spells by level |
| **Look up** | Spells (filter by class and level), monsters (by CR), conditions, magic items, equipment, rules |

Dice in rules text are tappable: `8d6` in Fireball rolls 8d6; `Attack Roll: +4`
on a stat block rolls d20+4. Every roll lands in the log.

Everything is saved to one JSON file on the phone. A fresh install starts with a
sample rogue; **Edit › Start over** clears it.

## Layout

- `tool/` — the tool (Kotlin, Compose, light-sdk).
- `data/` — the pipelines that build the SRD and dice assets into
  `tool/src/main/assets/`. Their output is checked in: Light's builder only
  copies `src/main`.
- `sdk/` and the rest of the root — vendored `light-sdk`. Read-only.

## Build

```bash
JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew -DlightSdk.toolOnly=true :tool:testDebugUnitTest :tool:assembleDebug
adb install -r tool/build/outputs/apk/debug/tool-debug.apk
./data/build_srd.py   # refresh the SRD data, only if 5e-bits changes it
```

`serverPackage` in `tool/lighttool.toml` is `com.lightos` for a real LP3; use
`com.thelightphone.sdk.emulator` for the LightOS emulator.

## Credits and licences

- Rules text: SRD 5.2 by Wizards of the Coast, CC-BY-4.0 (attributed on the
  About screen), via [5e-bits/5e-database](https://github.com/5e-bits/5e-database) (MIT).
- MEK Sans and MEK Dings by [MEK.txt](https://www.mek.gallery/mektype). MEK Sans
  is CC0 (MEK's letter); MEK Dings is unconfirmed, see `TODO.md`.
- PxPlus IBM VGA 8x16 from VileR's [Oldschool PC Font Pack](https://int10h.org/oldschool-pc-fonts/),
  CC BY-SA 4.0, unmodified.
- Jacquard 24 by Sarah Cadigan-Fried, SIL OFL 1.1.

The font pack's and Jacquard's licences ship in `tool/src/main/assets/licenses/`.
