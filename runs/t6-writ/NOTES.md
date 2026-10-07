# Notes

What the two specs say, what was hard to state, and what they cannot say.

## What the specs state

`test/rooms/core_spec.clj` characterises the five fns over a `Calendar`:

- a model of `free?` in the spec's own words (`room-free?`), so the law is
  an equality, not a set of examples;
- the three boundary cases the half-open definition turns on: an unknown
  room, an empty query, and an empty booking are all never in the way,
  and a booking that ends where another starts does not clash;
- `book` in both directions: the added booking's id is the old `:next-id`,
  its fields are exactly the arguments, `:next-id` rises by one, the
  rooms and every other booking are untouched; and when `free?` is false
  the calendar comes back unchanged with a nil id;
- `cancel` as one equality against an `if`: removed only when the id is
  present and its `:who` is the caller's, otherwise nothing;
- `busy` as exactly the room's `[start end]` pairs in sorted order;
- `usage` as the sum of the durations of `who`'s bookings over every room.

`test/slots/core_spec.clj` builds on that spec (`:uses
[rooms.core-spec]`) and restates what a free slot is in its own words
(`free-in?`, `overlap?`), then pins each fn to an independent model:
`earliest` to a search that takes the smallest start and then the lowest
room; `book-earliest` to the model's booking of that slot; `book-all` to
a fold over the requests that threads the calendar request by request and
keeps one entry (a slot or nil) per request. `calls` says the wiring the
task names: `earliest` goes through `rooms.core/free?`, `book-earliest`
through `rooms.core/book`.

## What was hard to say

- **The tie-break in `busy`.** TASK says "sorted by start", which does
  not order two bookings of one room that share a start but have
  different ends (an empty booking and a real one may). A first version
  stated the weaker properties -- right pairs, sorted -- and the report
  named the gap: a stand-in that reversed tied pairs satisfied every law.
  The spec now says `busy` equals `(sort pairs)`, i.e. ties go by the end
  as `sort` orders vectors. This is a choice the task did not make, and it
  rejects an otherwise-sorted implementation that breaks ties the other
  way; it is the smallest tie-break writ will accept, and it is recorded
  in a comment on the law.

- **Which calendars exist.** TASK describes a calendar's shape but not
  its invariants. To make `usage` a `Nat` and to let `book` promise a
  fresh id, `Calendar` is refined to *well-formed* calendars: every
  booking has `:start <= :end`, and every booking id is below `:next-id`.
  A malformed calendar (a backwards booking, an id colliding with
  `:next-id`) is outside the spec, so an implementation that mishandles
  one is not caught. The refinement carries a `:build` (`settle`) so the
  generator makes well-formed calendars instead of filtering for them.

- **Empty ranges.** TASK defines a clash as ranges that *share a tick*,
  so an empty booking (`start = end`) collides with nothing, and a query
  with `start >= end` is never free. The usual half-open overlap test
  `(< s e2) (< s2 e)` gets this wrong; the spec keeps the non-emptiness
  conjunct on both sides. This is the boundary most likely to be missed,
  so it is also pinned by three closed laws.

- **A model the prover cannot read.** The slots model searches `range`,
  sorts and filters over sets, so the prover proves none of the model
  laws. They are marked `{:require :tested :because "..."}` rather than
  weakened; each ran on at least 1000 inputs. The one exception is the
  graph edge that says `book-earliest` returns the component's calendar:
  that is `rooms.core/book`'s proved law to give (`:tested` on that step),
  because this spec is not allowed to unfold the component.

## What could not be expressed

- The order among `busy` pairs that are equal -- it is a multiset
  question and does not need answering; equal pairs are equal.
- That a booking's map key equals its `:id`, or that `:next-id` is
  monotone over time. TASK does not say either, and no law needs them.
- That calendars only ever come from `book`/`cancel` from an empty
  calendar. The refinement is the closest statement, and it is wider.
- That `book-earliest` really calls `rooms.core/book` as a fact; a law
  cannot see the call, so `calls` states it structurally instead.

## Evidence

- `bin/check` exits 0 on seeds 1 through 12; both reports are `ok`.
- No report listed a test of the code that no law took both ways.
- `rooms.core-spec`: 19 of 19 laws proved. `slots.core-spec`: 6 of 12
  proved, the other 6 explicitly tested for the reasons above.
- Twelve hand-made bugs were run against the specs (six in `rooms.core`,
  six in `slots.core`): a missing empty-booking guard in `free?`, a
  touching-ranges clash, `cancel` ignoring `:who`, a doubled `:next-id`,
  an unsorted `busy`, an off-by-one `usage`, the wrong room at a tie, a
  swapped `[room start]`, a dropped last start, `book-all` not threading
  the calendar, dropping nil placements, and booking at the wrong start.
  Every one failed the check.

## Changes made to the specs after the `specs` commit

1. `rooms.core-spec/busy`: the property law (same pairs, sorted) was
   replaced by the sorted-pair model. The property law was not wrong
   about TASK, but it left the tie-break open and the adequacy check
   showed a wrong tie-break passed; the model closes that gap while still
   requiring `busy` to be sorted by start.
2. `slots.core-spec`: added `{:require :tested :because "..."}` to the
   five laws the prover cannot read (no content weakened), and `:tested`
   on the `book-earliest` graph step, whose obligation belongs to the
   component's proved law.
