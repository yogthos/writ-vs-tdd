# Room bookings, and a slot finder on top of them

Two pure namespaces. `rooms.core` keeps bookings of meeting rooms;
`slots.core` finds and books free time, through `rooms.core`. Times are
Nat ticks; room ids and booking ids are Nats.

## rooms.core

A calendar is

    {:rooms #{room-id ...}, :bookings {id booking}, :next-id Nat}

and a booking is `{:id Nat, :room Nat, :start Nat, :end Nat, :who Nat}`
covering the ticks `start <= t < end` (so a booking ending at 10 and one
starting at 10 do not overlap). Two bookings **clash** when they are for
the same room and their tick ranges share a tick.

- `(free? cal room start end)` is true when `room` is one of the
  calendar's rooms, `start < end`, and no booking of `room` clashes with
  `[start, end)`.
- `(book cal room start end who)` returns `[cal' id]`. When `(free? cal
  room start end)`, it adds the booking under `:next-id`, `:next-id` goes
  up by one, and the id is the new booking's. Otherwise the calendar is
  unchanged and the id is nil.
- `(cancel cal id who)`: when booking `id` exists and its `:who` is `who`,
  it is removed. Anything else changes nothing.
- `(busy cal room)` is the bookings of `room` as `[start end]` pairs,
  sorted by start.
- `(usage cal who)` is the total number of ticks booked by `who`, over
  every room.

## slots.core

- `(earliest cal len from until)` is the earliest free slot of `len` ticks
  in any room: a pair `[room start]` such that `(free? cal room start (+
  start len))`, `from <= start` and `start + len <= until`, with the
  smallest `start`; among rooms free at that start, the lowest room id.
  With `len` 0, or when there is no such slot, it is nil.
- `(book-earliest cal len from until who)` books `(earliest cal len from
  until)` for `who` through `rooms.core/book` and returns `[cal' [room
  start]]`; with no slot it returns `[cal nil]`.
- `(book-all cal requests from until)` books a vector of requests `{:who
  Nat, :len Nat}` in order, each at its earliest slot at the time it comes
  (after the bookings made for the requests before it). It returns `[cal'
  placed]` where `placed` is a vector with one entry per request: `[room
  start]` for a request booked, nil for one that found no slot.
