# Notes on the writ specs, and on proving them

`bin/check` exits 0. The two reports say:

- `expr.core-spec` against `expr.core`: 40 of 53 laws proved (22 for every
  input, 18 on particular values); the other 13 are tested on 1000+ inputs,
  each with a `:because`.
- `stack.core-spec` against `stack.core`: 2 of 24 laws proved (both are the
  `run` examples, particular values); the other 22 are tested on 1000+
  inputs, each with a `:because`.

No report has a "test of the code no law took both ways" note, so no branch
of either implementation is left untried.

## What was hard to say in the spec

**The order of simplify's rules.** The problem statement gives the rules as
an algorithm -- simplify the parts, then apply the first matching rule once.
A law cannot apply "the first matching rule"; it can only say what the result
is. We said it three ways, each catching a different mistake:

1. one law per rule, for both operands (`add-zero-on-the-left` and
   `-on-the-right`, etc.);
2. a *composed* law per node, whose right side is the rule chain applied to
   the node's simplified parts (`simplify-a-sum`, `simplify-a-product`,
   `simplify-a-negation`) -- this is what pins the output for arbitrary
   expressions, and what tells a wrong-but-value-equivalent simplifier apart
   (one that answers `[:mul x [:num 2]]` where `[:add x x]` is meant);
3. closed laws that only hold when an earlier rule wins, e.g.
   `(simplify [:add [:num 0] [:num 5]]) = [:num 5]` (rule 1 beats rule 2) and
   `(simplify [:mul [:num 0] [:num 7]]) = [:num 0]` (rule 1 beats rule 4).

The spec's own `simplified?` says "no rule applies", and the normal-form and
value-preservation laws (`simplify-makes-a-normal-form`,
`simplify-leaves-a-normal-form`, `simplifying-keeps-the-value`) close the
loop. This is the shape WRIT.md suggests for a normal form.

**Bottom-up, not top-down.** The problem statement's "simplifies the parts
first" is not separately implied by the rules; a top-down simplifier can
agree on many nodes and differ on others. Two closed laws force it:
`(simplify [:add [:mul [:num 2] [:num 3]] [:num 0]]) = [:num 6]` and
`(simplify [:mul [:add [:num 1] [:num 2]] [:num 1]]) = [:num 3]`. In each,
the outer rule, applied before the parts were simplified, would give back an
unsimplified expression.

**run's failure model.** "nil when an instruction finds too few values, or
when the program ends with other than exactly one value" is operational. We
wrote the finitely many shapes: `:add`/`:mul` with zero or one value,
`:neg` with none, the empty program, and programs ending with two values
(`[[:push a] [:push b]]`, after a `:neg`, and after an `:add` that leaves
one of three pushed values under another). Worth recording: a program that
never underflows cannot end with zero values -- each successful instruction
leaves at least one, and `:add`/`:mul` only run with two -- so the empty
program is the only zero-value case. The spec says that as a plain law.

**`Instr` shares `add`, `mul` and `neg` with `Expr`.** The problem statement
uses the same keywords for an expression's operators and an instruction's.
WRIT.md allows that when the fields differ, and they do (`(add Expr Expr)`
and `(add)`); the spec relies on it, typing a program as `(Vec Instr)` and
compiling `[:add a b]` to a program that ends in `[:add]`.

**The component boundary.** `stack.core-spec` builds on
`expr.core-spec` with `:uses`, and its central law is the only way to say
the compiler's meaning:

    (= (run (compile-expr e) env) (expr.core/evaluate e env))

The evaluated/simplifier laws are stated about `evaluate` and `simplify`
directly, not restated in the stack spec, so the stack contract rests on the
component's spec and its proved laws are cited as lemmas.

## What could not be expressed (or proved)

- **The prover cannot discharge the recursive `simplify` laws.** The rule
  laws, the composed laws, `simplify-makes-a-normal-form`,
  `simplifying-twice-is-simplifying-once` and `simplifying-keeps-the-value`
  all end "the prover's search over simplify's case/cond runs out of fuel".
  The stuck goal is a case split over `(first (simplify x))` reached while
  proving a law about `(simplify [:add [:num 0] x])`; the same laws are
  proved in `test/writ/spec_demo/arith_spec.clj`, so this is a prover
  limitation that the extra laws and examples aggravate, not a false law.
  They are marked `:require :tested :because ...` rather than weakened.
- **The prover cannot discharge the stack laws at all.** It will not unfold
  `compile-expr` over a vector result (`conj` onto a value not known to be a
  vector) or unroll `run`'s `loop` over a symbolic program. Even the simplest
  law, `(compile-expr [:num n]) = [[:push n]]`, is "no proof found"; the
  `test/writ/spec_demo/calc_spec.clj` spec for the same shape has the same
  result when `:require :proved` is forced on it. So every substantive stack
  law is tested, with the reason recorded on the law.
- **What is not expressed at all.** Nothing in TASK.md is left out: the
  expression shape, evaluation (with the missing-variable-is-zero rule, both
  through `get`'s default and as a separate law), the five simplification
  rules in order, bottom-up application, the no-match case, value
  preservation, `size`, `variables`, the exact postorder program, every run
  failure, `compile-simplified`, and the compiler-correctness law are all
  laws. What is missing is proof, not statement, for the laws above.
- **Examples for compile-expr and compile-simplified were dropped.** With
  the implementation unwritten, writ's spec-alone check (`vet`) runs a law at
  an example's arguments; the compiler-correctness law calls
  `expr.core/evaluate`, which does not exist yet, and the run throws "No such
  namespace". Keeping those examples would make the spec-alone report fail
  its own consistency. The laws pin the programs instead, and the two `run`
  examples remain. The comment in the stack spec records this.

## Spec changes made after the first check

The first `git` commit (`specs`) held the contract before any code. Three
changes were made while implementing; each is a correction or a
strengthening, never a weakening:

1. **A wrong law about double negation.** The spec asserted
   `(simplify [:neg [:neg [:num 5]]]) = [:num -5]`. That is wrong about
   TASK.md: simplify is bottom-up, so the inner `[:neg [:num 5]]` becomes
   `[:num -5]` first, and the outer node then matches rule 1 (negate a
   number) and gives `[:num 5]`. The law now says `[:num 5]` and is named
   `a-double-negation-of-a-number-is-the-number`. (The initial implementation
   was right; the check found the spec wrong.)
2. **Composed simplify laws were re-added and marked tested.** They had been
   dropped while trying to make the prover succeed. Without them the spec did
   not pin the output for arbitrary expressions (a value-equivalent but
   non-canonical normal form could pass), so they were restored as
   `:require :tested`.
3. **Unprovable laws carry `:require :tested :because "..."`.** The spec
   still asks for proof (`(spec ... {:require :proved})`); the marker is
   used only where the prover cannot deliver, as WRIT.md describes.

## Sanity checks with broken code

To be sure the specs are not vacuously passing:

- removing rule 4 from `simplify` (multiply by zero) fails four laws
  (`multiply-by-zero-on-the-left`, `-on-the-right`,
  `simplify-makes-a-normal-form`, `example:simplify:2`);
- compiling the operands of `:add`/`:mul` in the other order fails
  `a-sum-appends-its-operator` and `a-product-appends-its-operator`.

The `run` laws and the compiler-correctness law, run on 1000+ generated
inputs each, catch value mistakes in the interpreter.
