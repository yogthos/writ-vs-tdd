# Project notes

- The runtime is jolt (Clojure on Chez Scheme, no JVM). Run code with
  `jolt -e '...'` from this directory; the deps.edn puts `src` on the path.
  `jolt -M:test` also puts `test` on the path.
- Tests use clojure.test (`deftest`, `is`, `testing`, `are`).
  `bin/test` runs the tests in `test/expr/core_test.clj` (namespace
  `expr.core-test`) and `test/stack/core_test.clj` (namespace
  `stack.core-test`) and prints the results. Exit code 0 means every test
  passed.
