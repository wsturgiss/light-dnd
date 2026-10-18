# light-dnd

A D&D 5e companion for the Light Phone III: dice, character sheets, a bag,
spell slots, and the SRD 5.2 for looking things up at the table. Everything is
on the phone; it never touches the network.

Typeset in MEK Sans, IBM VGA 8x16 and Jacquard 24, after Script Wizards'
typewriter sheets and their athanor desktop ([visual style](docs/visual-style.md)).

## What's in it

| Screen | Does |
|---|---|
| **Home** | Name, HP, last roll; Roll / Sheet / Bag / Spells / Look up. The people icon switches character |
| **Characters** | Everyone you play; tap to switch, + to build a new one |
| **New character** | Everything the 2024 rules ask for: class, species and its lineage, background and its feat, Human's origin feat, fighting style, Magic Initiate spells, scores (standard array, point buy or 4d6), skills, cantrips and spells, languages, subclass past 3rd, starting gear. Each choice can be read in full before picking; HP, AC and slots are worked out as you go |
| **Features** | Species traits, feats, class features to your level, languages and training; each opens its rules |
| **Level up** | From Edit. The level's new features, subclass at 3rd, fighting style, new cantrips and spells, average or rolled hit points (a higher CON counts back to 1st level), Ability Score Improvement or Epic Boon, Expertise, new spell slots |
| **Roll** | Tap dice and modifiers to build `2d6+3`, then Roll; the dice are thrown on screen ([how](docs/dice-animation.md)). ADV/DIS for a d20. Log of the last 50 |
| **Sheet** | Scores, saves, skills, AC, initiative, conditions. Tap anything with a bonus to roll it. HP opens a Damage / Heal / Temp pad |
| **Edit** | Corrections: who, scores, HP/AC/speed; saves, skills and spell slots one tap down; Level up; delete |
| **Rest** | Long rest restores HP and slots; short rest rolls a Hit Die |
| **Bag** | Coins (add / spend / set), items with quantity, weight carried. Add from SRD gear or type a name. Wear armor and a shield here: AC follows |
| **Spells** | Save DC and attack, slots to spend and regain, your starred spells by level |
| **Look up** | Spells (filter by class and level), monsters (by CR), conditions, magic items, equipment, species, feats, class features, rules |

Dice in rules text are tappable: `8d6` in Fireball rolls 8d6; `Attack Roll: +4`
on a stat block rolls d20+4. Every roll lands in the log.

Everything is saved to one JSON file on the phone; the roll log is shared by all
characters. A fresh install starts with a sample rogue; **Edit › Delete** removes it.
Class, species and background rules are SRD 5.2: what to choose from comes from
`data/build_srd.py` (`creation.json`, `starting-gear.json`), and the numbers (hit dice,
slot tables) are in `model/Rules.kt`, tested against it. The SRD has one subclass per
class; others can be typed. Multiclassing isn't modelled.

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
