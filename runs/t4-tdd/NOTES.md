# Notes

## What the tests pin down

The suite (46 forms in `queue.core-test`, 27 in `dispatch.core-test`; 65
`deftest`s / 145 assertions) treats TASK.md as a complete contract:

- **queue.core**: the exact job map shape; `enqueue` id/`:next-id` accounting;
  `next-job`'s full ordering (priority desc, then `ready-at` asc, then id asc)
  including each tie-break in isolation and ready-at beating id; the
  `ready-at <= now` boundary; `lease`'s window (`now+10`), attempt increment,
  unchanged `ready-at`, and nil/unchanged result; `complete`/`fail` requiring a
  leased job *owned by this worker* AND a *live* lease (`now < :until`),
  including the `now == :until` non-live boundary; fail's 2/4 backoff and death
  at 3+ attempts; `expire` failing every stale lease with the same transition.
- **dispatch.core**: `tick` expiring before leasing, worker order, the live-lease
  skip, duplicate workers leasing at most one job, assignment vector order, and
  the 10-tick lease window versus attempts; `backlog` counting only due ready
  jobs and its inclusive `now` boundary. Integration tests drive a full
  enqueue -> lease -> complete and a lease/fail -> backoff -> dead lifecycle,
  plus tick-driven expiry using the tick's `now` as the backoff base.

## Hard to pin down

- **A dead job's `:ready-at`.** TASK says a failed job with 3+ attempts becomes
  `:dead` and "Either way it loses its lease"; it never says whether `:ready-at`
  is touched. I read the omission literally (only status and lease change) and
  assert `:ready-at` is left as-is. An implementation that reset `ready-at` to
  `now` on death would be defensible too, so this is the one genuinely
  under-specified point in the spec.
- **"expire ... fails, as `fail` says".** `expire` cannot literally call `fail`:
  `fail` only acts on a *live* lease, and `expire` targets leases that are *not*
  live. I interpreted "as `fail` says" as "the same failure transition" — dead at
  3+ attempts, otherwise ready with the 2^attempts backoff measured from `now`,
  losing the lease — and tested the outcomes rather than any call structure.
- **Tick ordering semantics.** Whether a duplicate worker's second entry should
  see the lease just granted to it requires reading tick as a fold over the
  evolving queue. TASK only states the conclusion ("the same worker listed twice
  leases at most one job"), so the tests assert that conclusion rather than the
  intermediate check.
- **Same-tick expiry + leasing.** Because an expired job backs off to `now+2`, it
  is not re-leasable in the tick that expired it; a test pins this so a
  "re-lease the just-expired job" bug fails.

## Could not express

- **Purity / no clock.** TASK says "nothing reads a clock", but a test can only
  observe that `now` is honoured; it cannot prove no clock was consulted.
- **Nat-ness.** That ids, priorities, attempts and times are Nats is assumed for
  well-formed inputs; the tests never feed negatives or non-integers.
- **The `workers` container type.** TASK says a vector; tests pass vectors and
  index nothing, so a list would behave identically here.

## Test changes made in stage 2

Two `expire` tests (`expire-second-attempt-backoff-four`,
`expire-third-attempt-dies`) called `(get-in (qc/expire qq 8))` without the
`[:jobs 0]` key path, which threw an ArityException. That was a genuine mistake
in the test code, not a specification question, so the path was added. No other
test was changed; the only implementation bug the suite caught was `enqueue`
failing to increment `:next-id`.
