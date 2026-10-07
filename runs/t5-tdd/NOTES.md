# Notes

Two namespaces, written test-first. The tests were committed (`tests`,
`15e0b4e`) before any implementation existed, and `bin/test` was green on
the first implementation run: **no test had to be changed**. The final
implementation commit is `9448582`; `bin/test` runs 40 tests / 96
assertions, exit 0.

## What was hard to pin down in the tests

- **`nil` vs `[]` is the whole contract.** `plan` returns `[]` for `n = 0`
  and `nil` when the usable lots are short; a qty-0 order line is *filled*
  (because its plan is `[]`), while a line that wants more than is on hand
  is *short* and takes nothing. I test both sides of each: `plan` with
  `n = 0` on empty **and** non-empty stock, `plan` exactly short vs exactly
  enough, a qty-0 line against no stock at all, and a short line followed
  by lines that still get filled. An implementation that conflates the two
  fails immediately.

- **Dispatch that is not stated explicitly.** `TASK.md` describes
  `quarantine`/`release`/`purge` as *doing* something but never says they
  return the new stock, and never says `:next-id` is left alone by
  `take-from`/`quarantine`/`release`/`purge`. I inferred both from "the
  stock is unchanged" and from the `{:lots ... :next-id ...}` shape and
  pinned them, including the full `:next-id` in the expected map. If the
  intended contract allowed these to return something else, or to renumber
  ids, these tests would be wrong — but returning stale/unrenumbered state
  is the only reading consistent with the rest.

- **Ordering must be sorted, not incidental.** The map `{:lots {id lot}}`
  has no defined iteration order, so the tie-break test inserts ids in a
  deliberately non-ascending order and the first-expiring test inserts the
  earlier-expiring lot *after* the later one. A version that leans on map
  order rather than `(sort-by (juxt :expires :id) ...)` fails.

- **Exact report shape.** I assert the report with `=` against a literal
  three-key map (`:filled`, `:short`, `:picks`), so an implementation that
  returns those keys in a different container type, as seqs, in a different
  order, or with extra keys fails. `TASK.md` enumerates exactly three keys,
  so exactness is fair, but it is a choice: it forbids, e.g., a helpful
  extra `:total` field.

- **State threading through `pick`.** The tests only make sense if each
  line is planned against the stock *after* the previous lines ran: one
  test empties a lot in line 0 and requires line 1 to start from the next
  lot; another has a short line in the middle and requires the following
  line to be filled. Planning every line up front against the original
  stock passes a naive reading but fails these.

- **A short line is all-or-nothing.** "It is never partly filled" is easy
  to say and easy to get wrong (take whatever is there). A lot of qty 3 and
  a line of qty 5 lets the test check both that nothing was taken and that
  the stock value is untouched.

## What I could not express in tests

- **Non-`Nat` or malformed input.** `TASK.md` says ids, SKUs and quantities
  are Nats, so I test no negative quantities, non-integers, missing lot
  keys, or lots with `nil` `:expires`. There is no specified behavior to
  pin.

- **Mutation / aliasing.** The API is pure and the tests compare values.
  There is no way to observe from outside whether a call destructively
  modified the map it was handed; "the stock is unchanged" is asserted only
  as value equality.

- **The `take-from` failure path inside `pick`.** `pick` runs plans through
  `stock.core/take-from`, and the plan is *supposed* to guarantee each take
  succeeds. What should happen if it somehow did not (record a pick anyway?
  abort? mark the line short?) is unspecified, so I record the pick and
  ignore `ok?`; no test can reach that branch through the public API.

- **Bounded-ness / laziness / performance.** Nothing in the task constrains
  the size of a stock or order, so no such tests.

- **Whether extra report keys are tolerable.** I had to pick a side
  (forbidden, per the exact-match assertions); a looser reading of the
  report format is not testable without saying what extra keys mean.

- **Test syntax was checked off-repo.** Before committing, the tests were
  loaded against throwaway stubs in a copied tree under `/tmp` to confirm
  they compile and fail as expected, so the `tests` commit contains only
  test files. The real implementation was written only afterwards.
