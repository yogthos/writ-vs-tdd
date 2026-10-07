# Notes on ambiguity in TASK.md

How I read each point that was not fully pinned down by the task text.

## `enqueue` and initial values

- "no attempts" → `:attempts 0`.
- "no lease" → `:lease nil`.
- `:next-id` is assumed present on the incoming queue and is used as the new
  job's id, then incremented by one. Nothing defines the queue's initial
  `:next-id`, so that is the caller's business (e.g. `{:jobs {} :next-id 0}`).

## The retry backoff and when a job dies

- `lease` increments `:attempts` every time a job is taken, so the first
  lease leaves `:attempts 1`.
- `fail` uses the job's *current* `:attempts` as the exponent:
  `:ready-at = now + 2^attempts`. So the first failure yields `now + 2`,
  the second `now + 4`. `2^attempts` is computed with `bit-shift-left`
  (integer) rather than `Math/pow` (double).
- "with 3 or more attempts it becomes `:dead`" is checked *after* the
  incumbent lease already bumped the counter. A job therefore gets exactly
  three tries: it dies on the failure of its third attempt
  (`:attempts 3`), and `:dead` jobs carry no lease.

## `expire` vs. `fail`'s guard

- `fail` only acts when the lease is `worker`'s and **live**. `expire`,
  however, must act on leases that are *not* live. Taken literally, calling
  `fail` from `expire` would always no-op. I read "fails, as `fail` says" as
  referring to the *effect* of failing (the `:dead` / `:ready` transition
  with the `now + 2^attempts` backoff, and dropping the lease), not to
  `fail`'s liveness precondition. The shared effect lives in an internal
  helper used by both `fail` and `expire`; `expire` needs no worker because
  each job's own lease supplies it.

## Lease boundaries

- "live while `now < :until`" is strict. At `now == :until` the lease is
  already dead: `complete` and `fail` do nothing, while `expire` fails it.
  This is the boundary `:until = now + 10` for a lease taken at `now`.

## `next-job` ordering

- Candidates are only `:ready` jobs with `:ready-at <= now`.
- Order: highest `:priority`, then earliest `:ready-at`, then lowest `id`.
  Implemented with a single sort key `[(- priority) ready-at id]`, so the id
  tie-break only applies when priority and ready-at are both equal.

## `tick`

- Expiration happens once, first, at `now`, before any worker is considered.
  After that, every surviving lease is live by construction.
- "a worker that holds a live lease on some job gets nothing" covers both
  leases carried into the tick and leases handed out earlier in the same
  tick. I re-check against the evolving queue on each worker, which is also
  what makes a worker listed twice lease at most one job: its first
  occurrence creates a live lease and the second is skipped.
- `assignments` preserves the order jobs were leased, as `[worker id]` pairs.
- A worker with no job to take leaves the queue untouched and is not added.
- Because `expire` computes `:ready-at = expire-time + 2^attempts` (≥ 2),
  a job that expires during a tick can never also be re-leased in that same
  tick; it becomes available on a later tick.
- The queue is returned even when no assignment happened.

## `backlog`

- Counts `:ready` jobs with `:ready-at <= now`; leased, done and dead jobs
  are not counted, and neither are ready jobs scheduled in the future.

## Namespace layering

- `dispatch.core` mutates only through `queue.core` (`expire` and `lease`);
  it reads the queue map directly only to detect an already-held live lease,
  for which `queue.core` exposes no predicate. I kept that check local rather
  than widening the queue API.
