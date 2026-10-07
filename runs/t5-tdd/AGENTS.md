# Project notes

- The runtime is jolt (Clojure on Chez Scheme, no JVM). Run code with
  `jolt -e '...'` from this directory; the deps.edn puts `src` on the path.
  `jolt -M:test` also puts `test` on the path.
- Tests use clojure.test (`deftest`, `is`, `testing`, `are`).
  `bin/test` runs the tests in `test/stock/core_test.clj` (namespace
  `stock.core-test`) and `test/picking/core_test.clj` (namespace
  `picking.core-test`) and prints the results. Exit code 0 means every test
  passed.
