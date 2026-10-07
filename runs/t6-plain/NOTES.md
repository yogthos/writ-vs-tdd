# Notes on TASK.md

Decisions and places where I had to read between the lines.

## rooms.core

- **`cancel` return value.** TASK.md says "it is removed. Anything else
  changes nothing", but never states what `cancel` returns. I made it return
  the (possibly unchanged) calendar, matching the shape of the other update
  functions (`book` returns the new calendar, so `cancel` does too). It does
  not return a boolean or a wrapper.
- **`cancel` checks only `:who`.** The rule is "booking `id` exists and its
  `:who` is `who`". I do not additionally check the room or any other field,
  even though `who` is not unique across bookings.
- **`busy` tie-breaks.** "sorted by start" says nothing about pairs with the
  same start. `sort-by first` is used, which is stable, but the underlying
  order of `(vals :bookings)` is hash-map order, so equal-start pairs come out
  in an unspecified order. Since ranges never share a tick for one room,
  equal starts cannot both be present for the same room anyway.
- **`free?` and unknown rooms.** The rule explicitly requires `room` to be in
  `:rooms`; a booking whose `:room` is absent from `:rooms` still counts as a
  clash if the interval overlaps, because `free?` scans all bookings of the
  room, not just rooms in the set.
- **`book` failure.** Returns `[cal nil]` with the calendar structurally
  unchanged (same value), as stated.
- **`usage` counts ticks, not bookings.** A `who` with no bookings gives 0.

## slots.core

- **Candidate start times.** Ticks are unbounded, so `earliest` cannot scan
  one tick at a time. I only test starts in `{from} ∪ {booking end times}`,
  for two reasons: occupancy is constant between booking boundaries, and a
  room can only *become* free at an end, never at a start. Any feasible start
  that is not `from` is therefore a booking end. Only candidates with
  `from <= s` and `s + len <= until` are considered.
- **Tie-break order.** The smallest `start` wins globally first; only among
  rooms free at that same start does the lowest room id win. I sort the
  candidate starts ascending and the room ids ascending and take the first
  `[room start]` from that nested iteration.
- **`len` 0 and negative / impossible ranges.** With `len` 0 the result is
  nil, as specified. I also return nil when `from + len > until`, when there
  are no rooms, or when `until < from`. Negative `len` cannot occur (Nat) but
  it would fall under `(pos? len)` and return nil, same as 0.
- **`book-earliest` when `book` would fail.** `earliest` already guarantees
  the range is free, so `book` must succeed. As a defensive measure I still
  return `[cal' nil]` if `book` somehow reports a nil id, rather than
  reporting a placement that was not made.
- **`book-all` placement vector.** One entry per request, in request order;
  `nil` for a request with no slot (including a `:len` of 0). `book-all` uses
  the same `earliest` + `rooms.core/book` path as `book-earliest`, so a
  request that finds a slot is booked before later requests are evaluated.
  An empty `requests` gives `[cal []]`.
