# Notes on TASK.md

Ambiguities I noticed while implementing, and how I resolved them.

- **"applies the first ... rule ..., once."** I read this as: simplify both
  children recursively, then perform exactly one rewrite of the node. No
  second pass over the rewritten node is needed, because every rule's result
  is itself already simplified: rule 1 yields a `[:num ...]`, rules 2/3/5
  yield a child that was simplified on the way up, and rule 4 yields
  `[:num 0]`. A quick randomized check confirmed `simplify` preserves value.

- **Rule ordering across operators.** Rules 1–5 apply to different expression
  operators, so "first matching rule" only has to be resolved within a single
  operator. The ordering that actually matters is: for `:add`, rule 1 before
  rule 2; for `:mul`, rule 1 before 3 before 4; for `:neg`, rule 1 before 5.
  I kept the global 1..5 order per operator. This matters for e.g.
  `[:mul [:num 0] [:num 1]]`, where rule 1 wins and gives `[:num 0]` rather
  than rule 4.

- **Rule 3 vs rule 4.** Both could in principle match a `:mul` with a numeric
  operand; they are disambiguated by the number (1 vs 0), and rule 1 already
  handles the both-numeric case, so there is no real conflict. Rule 3 is
  listed before rule 4 and implemented that way.

- **`variables` "sorted".** I used the default `sort`/`compare` ordering of
  keywords (namespace first, then name) and returned a vector, e.g.
  `[:a :b]`. Distinctness is applied before sorting.

- **`run` underflow.** "nil when an instruction finds too few values" — I stop
  and return nil immediately at the offending instruction rather than trying
  to continue with a partial stack.

- **`run` end condition.** The result is the single remaining value, else nil.
  A program ending with zero values (including the empty program), or two or
  more, yields nil. `:push`/`:load` never underflow; `:add`/`:mul` need two
  values and `:neg` one.

- **Missing env variable.** Everywhere a variable is read (`evaluate` and
  `:load`), a keyword absent from `env` is treated as `0`, via a defaulted
  lookup.

- **`compile-expr` shape.** Each program is a vector of instruction vectors.
  For `[:add a b]` I concatenate a's program, then b's, then append `[:add]`;
  `[:neg a]` is a's program with `[:neg]` appended. This reproduces the
  stated postorder exactly (verified `[:add [:num 1] [:var :x]]` compiles to
  `[[:push 1] [:load :x] [:add]]`).
