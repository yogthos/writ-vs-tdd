# A job queue, and a dispatcher on top of it

Two pure namespaces. `queue.core` keeps jobs with priorities, leases and
retries; `dispatch.core` hands jobs to workers each tick, through the
queue. Time is a Nat tick passed in by the caller (`now`); nothing reads a
clock. Worker ids and job ids are Nats.

## queue.core

A queue is

    {:jobs {id job}, :next-id Nat}

and a job is

    {:id Nat, :priority Nat, :attempts Nat, :status S, :ready-at Nat,
     :lease L}

where `S` is one of `:ready`, `:leased`, `:done`, `:dead`, and `L` is
`{:worker Nat, :until Nat}` for a `:leased` job and nil for any other.
A lease is **live** while `now < :until`.

- `(enqueue q priority now)` returns `[q' id]`: a new job under
  `:next-id`, `:ready`, no attempts, ready at `now`, no lease;
  `:next-id` goes up by one.
- `(next-job q now)` is the id of the job a worker should take next, or
  nil: among the `:ready` jobs whose `:ready-at` is at most `now`, the one
  with the highest priority; on equal priority the earliest `:ready-at`;
  then the lowest id.
- `(lease q worker now)` returns `[q' id]`: it takes `(next-job q now)`,
  makes it `:leased` with lease `{:worker worker, :until (+ now 10)}` and
  adds one to its attempts. With no job to take, the queue is unchanged
  and the id is nil.
- `(complete q id worker now)`: when job `id` is `:leased`, its lease is
  `worker`'s and is live, the job becomes `:done` with no lease. Anything
  else changes nothing.
- `(fail q id worker now)`: on the same condition as `complete`, the job
  fails: with 3 or more attempts it becomes `:dead`, otherwise `:ready`
  again, ready at `now + 2^attempts` (2 after the first attempt, 4 after
  the second). Either way it loses its lease. Anything else changes
  nothing.
- `(expire q now)`: every `:leased` job whose lease is no longer live
  fails, as `fail` says, at `now`.

## dispatch.core

- `(tick q workers now)` returns `[q' assignments]`. It first expires the
  queue at `now`. Then it goes through `workers`, a vector of worker ids,
  in order: a worker that holds a live lease on some job gets nothing; any
  other leases the next job, if there is one. `assignments` is a vector
  of `[worker id]` pairs, one per job leased, in the order leased. The
  same worker listed twice leases at most one job.
- `(backlog q now)` is the number of `:ready` jobs whose `:ready-at` is at
  most `now`.
