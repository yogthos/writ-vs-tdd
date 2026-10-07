# Notes on the stock/picking writ specs

## What was hard to say

**The index base of the report.** TASK says `:filled`, `:short` and `:picks`
hold "the lines' indexes". It never says whether the first line is index 0 or
1. Since an order is a vector and the only index Clojure has is the vector
position, the specs fix **0-based** indexes, and the examples in
`picking.core-spec` (`:filled [0]`, `:picks [[1 2 1]]`) anchor that. If the
intended meaning were 1-based, every `:filled`/`:short`/`:picks` law and
example would move by one; nothing else would change.

**The component's meaning has to be restated, not called.** The picking spec
builds on the stock spec, so `pick` must fill "through `stock.core/take-from`".
But a spec helper may not call the component's fns — a model that did would
judge the workflow by the very code it builds on, and the spec must load and
check before `stock.core` exists. So the component is restated in the picking
spec's own words: `usable?`, `live`, `ordered`, `model-take`, and the models
`model-plan` and `model-pick`. The target's `plan` and `pick` are then pinned by
exact equality with those models. This is a deliberate duplication of the
component's semantics in the spec, and it is the only way to say "what
`take-from` does to a lot" without calling `take-from`.

**"Each through `take-from`" is a call-graph fact, not a value fact.** The
equality to `model-pick` says what `pick` returns, but a semantically equivalent
implementation could inline the lot arithmetic and never call the component. No
law can see the difference, so the structural half is stated separately:
`(calls pick {:through [stock.core/take-from]})`. That is the strongest form
available — it says the target reaches the component, not that every single
pick goes through it. A requirement of "exactly one `take-from` call per pick"
is not expressible in a writ spec.

**"Never partly filled" is expressed by omission.** Nothing in the laws says
"do not partly fill a short line"; the model simply records no pick for a nil
plan, and the exact equality makes any partial fill a mismatch. The intent (a
short line takes *nothing*) only lives in the fact that the model has no branch
that would take part of it.

**`receive` when `:next-id` collides with an existing key.** TASK says a lot is
added "under `:next-id`" and `receive` is then described by `assoc`, so the law
describes the assoc (an overwrite) rather than asserting a fresh key. Generated
stocks do not have to satisfy "every key is below `:next-id`", so the spec does
not smuggle in that invariant.

**The `:lots` type.** `(Index :id Lot)` is used for the lots map, which also
makes the generated stocks well formed (each lot sits under its own `:id`). The
`{:lots {id lot}}` notation suggests exactly this, but it is stronger than the
bare map type; the Index says the key *is* the lot's `:id`, which every law then
relies on.

## What could not be proved (and is only tested)

* `purge-drops-the-expired` — `purge` folds a map of unknown size
  (`reduce-kv`), and the prover does not model that fold (`:require :tested`,
  reason recorded).
* `plan-is-the-greedy-first-expiring` and `pick-fills-every-line-it-can` —
  they order a filtered map of unknown size (`sort-by`) and thread a stock
  through a fold over the order; the prover found no proof (`:require :tested`,
  reasons recorded).

Everything else asks for proof: the stock spec proves 21 of 22 laws (6 for
every input), and the picking spec proves all of its examples. The three tested
laws each ran on 1000+ generated inputs.

## What the reports did not say

Across several runs neither implementation had a test that only ever went one
way: the report's one-sided-branch notes were empty, and the gap check rejected
impostors (constant, pass-through, off-example and perturbed stand-ins) for
every signed fn. To check the specs are not merely strong on paper, deliberately
broken implementations were tried and each was rejected by the expected law:
`receive` allowing qty 0, `purge` using `<` instead of `<=`, `plan` breaking the
lowest-id tie, `plan` returning what it can instead of nil, and `pick` reporting
1-based indexes.
