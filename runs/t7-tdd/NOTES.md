# Notes

## What was hard to pin down in tests

- **Rule order is unobservable.** TASK.md insists the simplification
  rules be tried in order 1-5, but the only overlaps are:
  rule 1 against rules 2/3/4, and those overlap only when *both* operands
  are `[:num ...]`. In every such case the rules agree on the result
  (`[:add [:num 0] [:num 5]]` is `[:num 5]` by rule 1 and by rule 2;
  `[:mul [:num 0] [:num 3]]` is `[:num 0]` by rule 1 and by rule 4). Rule 1
  and rule 5 are disjoint (`[:neg [:num a]]` vs `[:neg [:neg x]]`). So
  no expression can distinguish "first matching rule" from "any matching
  rule"; the order test uses cases whose result is the same either way.
  What *is* testable, and is tested, is that each rule produces the
  specified expression.

- **"Once" vs. repeatedly.** One bottom-up pass already reaches a fixed
  point for these rules: a rule's result is either a number or an
  already-simplified subexpression, and the parent is processed only after
  its children are fully simplified. So "apply the rule once" and "apply
  until no rule matches" are indistinguishable by output. I tested instead
  that `simplify` is a fixed point (`simplify (simplify e)) = simplify e`),
  which any one-pass implementation satisfies.

- **Zero values left without a failed instruction.** TASK.md says `run` is
  nil when the program "ends with other than exactly one value". I could
  test the empty program (zero values) and non-empty programs with two or
  more leftover values. I could *not* write a non-empty program that ends
  with zero values without an instruction failing: `push`/`load`/`neg` never
  decrease the depth by themselves, and `add`/`mul` need two values and
  then leave one, so a stack can never fall to zero. The "other than
  exactly one" failure is therefore, in practice, "more than one", plus the
  empty program.

- **Exhaustiveness of the property checks.** The `evaluate`/`simplify` and
  `compile`/`run` agreement tests enumerate every expression up to three
  nodes over `{0 1 2 -1}` and `{:x :y}`, deterministically, and check a
  handful of environments. That is not an exhaustive check of all
  expressions, but the rule tests give explicit cases with non-leaf
  operands so each rule is exercised beyond the enumerated small shapes.

## What I could not express

- **Keyword sort order.** `variables` must be sorted, but TASK.md does not
  define the order. Tests only use simple keywords (`:a`, `:b`, `:x`, ...)
  and rely on Clojure's default keyword comparison; namespaced keywords
  (`:ns/a`) are not pinned down.
- **Malformed expressions.** Wrong arity, an unknown tag, or a non-Int
  number are undefined by TASK.md, so no test covers them.
- **Int semantics.** The task says Int; I assumed arbitrary-precision
  integers and did not test overflow or any fixed width.
