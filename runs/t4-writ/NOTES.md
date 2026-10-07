# Notes on the spec

Two namespaces, two writ specs. `bin/check` exits 0. Both specs are
checked with `{:require :proved}`; where the prover cannot reach a law it
is marked `{:require :tested :because "…"}`, and the two graph steps whose
obligations are only tested carry `:tested {[state f] "…"}`.

## The one spec change after the `specs` commit

The specs were committed before any implementation, as asked. Once the
code existed and the check passed, I strengthened the spec-level default
from "may be proved or tested" to `{:require :proved}` and named, with a
`:because`, each law the prover cannot prove. No law's statement, no
`ann`, no refinement and no `:require` on the component changed; the
contract is the one that was committed. The change only *asks* for proof
where the prover can give it and records honestly where it cannot:

- queue: `next-job-is-nil-exactly-when-nothing-is-due` (the `filter` over
  the map's values), `the-next-job-is-due` and
  `nothing-due-outranks-the-next-job` (the `sort-by` ranking),
  `lease-takes-the-next-job` (the whole-queue comparison behind that
  ranking), `expire-reaps-any-lease-that-has-run-out` (its `for`/`into`
  model), and the two graph steps `queue:q:lease.first` and
  `queue:q:expire` (the same two reasons).
- dispatch: the tick model, the assignment-vector laws and the
  live-lease law (all read through the `reduce` the prover leaves
  opaque), and the graph step `dispatch:q:tick.first`.

Before settling on it I checked the spec against twelve deliberately
broken implementations (wrong tie-break, `now+9` lease, `<=` on lease
liveness, `>3` death threshold, `now+8` backoff, a non-advancing
`next-id`, a status-blind `next-job`, a worker-blind `complete`, a tick
that forgets to expire, walks workers backwards, never skips a live-lease
holder, or counts backlog with `<`). Every one failed the check, most on
the law that states that very rule.

## What was hard to say in the spec

**Well-formed queues are built, not found.** TASK gives a queue as
`{:jobs {id job}, :next-id Nat}`. For the laws to be about jobs that
really are the queue's jobs, the spec's `Queue` refinement asks that each
job sits under its own `:id` (`(Index :id Job)`) and that `:next-id` is
above every id. A random record almost never meets that, so `Queue` has
`:build settled`, which renumbers a generated queue's jobs `0,1,2,…` and
sets `:next-id` above them. That builder is generation only; it is not
part of the contract, and it is why the same queue a law sees may have
different ids from the one drawn.

**TASK never says key = job's `:id`; the spec decides it does.** The
`(Index :id Job)` field is a reading of `{id job}`: the map's key *is* the
job's identity. A queue whose keys and `:id`s disagree is not a value of
`Queue` here. `enqueue`'s law even relies on it: `(= (:jobs q) (dissoc
(:jobs q') id))` says the new job is added, not swapped for an old one,
which is only true when `:next-id` was fresh.

**"fails, as `fail` says" cannot call `fail`.** `fail` fires only on a
*live* lease, and `expire` fires exactly on leases that are *no longer*
live. So the spec restates the transition once as `reaped` and uses it in
both `fail` and `expire`; the two share one definition of dying and
retrying (`2^attempts`, dead at 3). That paraphrase is the one place the
contract is not passed straight to the code.

**A workflow cannot be its own model, so the dispatcher is modelled in
the spec's own words.** The dispatch spec builds on the queue spec, but a
spec helper may not call the component — otherwise the workflow would be
judged by the very code it builds on. `model-tick` therefore repeats, in
dispatch.core-spec's own names, what expire and the lease-selection do,
and the tick law equates the code with it. The repetition is the price of
stating tick's whole behaviour (`expire` first, workers in order, skip a
live-lease holder, at most one lease per worker, assignments in lease
order) in one law; the smaller tick laws restate the pieces and are the
ones a reader should look at.

**The tie-break laws need examples to be sure.** `nothing-due-outranks-
the-next-job` is a strict total order — priority down, `:ready-at` up,
`:id` up — and it pins `next-job` completely, but random queues rarely
make two due jobs tie on all three keys. The three `example`s that make
each tie are what actually exercise it; mutation testing shows the tie
law catches a reversed tie-break anyway, because its `every?` checks
every pair on every trial.

**The dispatch `calls` is exact, so it is also a contract on structure.**
`(calls tick [queue.core/expire queue.core/lease])` says a tick goes
*through* the queue, and inlining the lease selection fails it even if
every law holds. It does not constrain how tick checks for a worker's
live lease: it may read the queue's jobs directly.

## What the report's notes mean

- `` `backoff`: (clojure.core/= n 2) was never false `` (and the same
  note for `queue.core/backoff` in the dispatch report) is about dead
  code, not a gap: `reaped` calls `backoff` only under `(< attempts 3)`,
  so the `case`'s test for `2` is only ever reached when `n` is 2, and its
  default arm is never taken. No law can try the other way because the
  code cannot reach it.
- `` `a-worker-holding-a-live-lease-gets-nothing` is thinly tested ``
  names a real rarity — a randomly generated queue seldom holds a *live*
  lease for the very worker being considered. The two tick `example`s
  where a worker does hold one pin the case, and the tick model law covers
  it too. The same law, and its two companion assignment laws, "tell none
  of tick's stand-ins" on their own; together with the model law they
  reject every impostor `tick` is tested against.

## What I could not express

- That `expire`'s reaping and `fail`'s reaping are the *same* operation
  is said by sharing `reaped`, but TASK's phrase "fails, as fail says" is
  not itself checkable; only the resulting values are.
- That "the same worker listed twice leases at most one job" is a
  *consequence* of a worker holding a live lease after its first lease is
  not expressed; it is stated as its own law (`one-assignment-per-worker`)
  and by the model.
- The prover has no model of `sort-by`, of a `filter` over a map's values,
  of `for`/`into`, or of a `reduce` over the workers, so those laws are
  tested (hundreds of inputs each, plus named examples) rather than
  proved. Their `:because` strings say so in every report.
- Nothing reads a clock and nothing effects anything, so there was nothing
  to say about time beyond passing `now` in; the specs sign no clock.

## Files

- `test/queue/core_spec.clj` constrains `src/queue/core.clj`.
- `test/dispatch/core_spec.clj` constrains `src/dispatch/core.clj` and
  builds on the queue spec with `:uses [queue.core-spec]`.
