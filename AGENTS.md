Build your own changes before handing them over:
`JAVA_HOME=/home/will/.jdks/jbr-21.0.11 ./gradlew -DlightSdk.toolOnly=true :tool:testDebugUnitTest :tool:assembleDebug`.
System Java 26 breaks Android jlink. The unit tests cover `model/` only; UI needs checking on a device.

## Working with `sdk/`

`sdk/` (and `plugin/`, `builder/`, `lint-rules/`, `examples/`) is vendored Light Phone `light-sdk`.
**Do not hand-edit it.** Use its public API from `tool/`; if something is missing, work around it in
`tool/` or raise it.

## Conventions here

- Every screen extends `DndScreen` (loads the save file, holds the roll / entry / number / text jumps).
- Wrap pages in `Page` / `ScrollPage` from `ui/Chrome.kt`. Page-level actions go in the bottom bar,
  max 3 (LightOS convention). Icons: `sdk/ui/.../LightIcons.kt`.
- Text goes through `T(...)` with a `Style`. Don't set font sizes in sp: the MEK fonts are pixel fonts
  and `Type.kt` sizes them in whole font pixels so they stay sharp.
- Three fonts: MEK Sans (text), IBM VGA 8x16 (`Style.Mono*`, `Total`: numbers), Jacquard 24
  (`Banner`: headings and names, never numbers). Only VGA has box-drawing; elsewhere use `[x]`/`[ ]`,
  `*`, `·`, `›`, or draw it in Compose. Keep VGA unmodified (CC BY-SA).
- State lives in `Store` (one JSON file). Change it with `Store.edit { }`; don't hold copies.
- SRD content comes from `data/build_srd.py`. Change the script and regenerate; don't hand-edit
  `assets/srd/*.json`.
