# Notes

## What I changed in the tests after committing them

One test was genuinely wrong about its own fixture: in
`book-all-mixes-placements-and-failures` each request used a *different*
`:who` (1, 2, 3, 4), so `(usage c' 1)` is 4, not 10. I corrected that
assertion; the placement, `busy` and total-tick expectations were
unchanged. No other test was touched after the `tests` commit.

## What was hard to pin down

- **Half-open boundaries.** "Clash" hinges on the half-open convention, so
  the clash tests deliberately include ranges that *touch* the booking
  (ending at 5, starting at 10) as free, and ranges that overlap by a
  single tick as clashing, plus covering/straddling/contained cases.
- **`book-all` is order-dependent, and a naive implementation hides it.**
  A test where two `len`-10 requests are placed in two rooms only fails if
  the second request reuses the first request's slot — i.e. it catches an
  implementation that computes all earliest slots against the *original*
  calendar instead of the accumulated one. Similarly, a `nil` placement
  must leave the calendar untouched so a later smaller request can still
  fit.
- **"Unchanged" calendars.** The spec says the calendar is unchanged on a
  rejected `book` or `cancel`. Tests assert value equality (`=`), not
  object identity, because a pure function may legitimately rebuild an
  equal map. So `identical?` is not pinned.
- **Return shapes.** For the plain pairs (`[room start]`, `[cal' id]`,
  `[cal' [room start]]`) I could not pin vector-vs-list, because Clojure
  sequential equality treats `[1 0]` and `'(1 0)` as equal. Only
  `book-all`'s `placed` is explicitly a vector, and only that is checked
  with `vector?`.
- **`next-id` after `cancel`.** TASK.md never says whether cancelling
  rewinds `:next-id`. I read "cancelling removes the booking and changes
  nothing else" as *not* rewinding, and tested that; if the intent were to
  reuse ids, that test would be wrong.

## What I could not express

- **Negative lengths.** The spec only defines `len` 0 as nil; negative
  `len` is undefined. The implementation treats it like 0 (nil), but no
  test pins that because the spec does not require it.
- **Algorithm / performance.** The tests use tiny ranges, so a correct but
  tick-by-tick scan over `from..until` passes just as well as the
  boundary-jumping implementation. There is no test for behaviour on very
  large `until` values (e.g. terminating quickly), only for correctness.
- **Overlap accounting in `usage`.** TASK.md defines it as the total ticks
  booked by `who` over every room, which I test as a plain sum of lengths.
  Whether two overlapping-in-time bookings in different rooms should be
  double-counted (sum) or counted once per distinct tick is not stated; I
  assumed the literal "total number of ticks booked", i.e. the sum.
- **`busy` sort stability.** Two bookings of the same room can never share
  a start without clashing, so the ordering of equal starts is unreachable
  and untested.
