# Project notes

- The runtime is jolt (Clojure on Chez Scheme, no JVM). Run code with
  `jolt -e '...'` from this directory; the deps.edn puts `src` on the path.
  `jolt -M:check -e '...'` also puts `test` on the path.
- `bin/check` runs writ's check of `src/rooms/core.clj` against
  `test/rooms/core_spec.clj` and of `src/slots/core.clj` against
  `test/slots/core_spec.clj`, and prints both reports. `bin/check --seed N`
  replays a seed. Exit code 0 means both passed.
- WRIT.md is the guide to writ: how to write a spec, the rules the
  implementation must follow, and how to read its reports. Its section
  "Building on another spec" is how the slots spec builds on the rooms
  spec.
