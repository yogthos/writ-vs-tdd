# Project notes

- The runtime is jolt (Clojure on Chez Scheme, no JVM). Run code with
  `jolt -e '...'` from this directory; the deps.edn puts `src` on the path.
  `jolt -M:test` also puts `test` on the path.
- Tests use clojure.test (`deftest`, `is`, `testing`, `are`).
  `bin/test` runs the tests in `test/queue/core_test.clj` (namespace
  `queue.core-test`) and `test/dispatch/core_test.clj` (namespace
  `dispatch.core-test`) and prints the results. Exit code 0 means every test
  passed.
