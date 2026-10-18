# CLAUDE.md

Read `AGENTS.md` first; it has the build command and the conventions for `tool/`.

## Installing on Will's Light Phone

- Install with `adb install -r` only. Never uninstall the tool (that wipes the save file).
- After every install, lock the phone to portrait:
  ```
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 0
  ```
- Keep every activity `screenOrientation="portrait"`. For this tool that's `orientation = "portrait"`
  in `tool/lighttool.toml`, which the Light plugin writes onto the activity in the manifest; don't
  remove it, and don't call `setRequestedOrientation` with anything else.
