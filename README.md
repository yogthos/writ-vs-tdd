# writ-vs-tdd

An experiment comparing three ways of having an LLM (deepseek-flash, run headless
through opencode) solve the same four programming tasks: from a plain-English spec
alone, test-first with clojure.test, and spec-first with
[writ](https://github.com/jlt-commons/writ). Each result is scored against a hidden
reference and hundreds of mutants.

Findings, method and how to rerun it: **[writ-vs-tdd.md](writ-vs-tdd.md)**.
