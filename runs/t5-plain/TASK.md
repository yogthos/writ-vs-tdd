# A warehouse's stock, and an order picker on top of it

Two pure namespaces. `stock.core` keeps lots of goods with expiry dates;
`picking.core` fills orders from the stock, through `stock.core`. Time is
a Nat tick passed in by the caller (`now`); nothing reads a clock. Lot ids,
SKUs and quantities are Nats.

## stock.core

A stock is

    {:lots {id lot}, :next-id Nat}

and a lot is

    {:id Nat, :sku Nat, :qty Nat, :expires Nat, :status S}

where `S` is `:active` or `:quarantined`. A lot is **usable** at `now`
when it is `:active`, its `:qty` is at least 1, and `now < :expires`.

- `(receive stock sku qty expires)` returns `[stock' id]`. When `qty` is at
  least 1 it adds an `:active` lot under `:next-id` and `:next-id` goes up
  by one; the id is the new lot's. With `qty` 0 the stock is unchanged and
  the id is nil.
- `(available stock sku now)` is the total `:qty` of the usable lots of
  `sku` at `now`.
- `(quarantine stock id)` makes lot `id` `:quarantined`; with no such lot,
  nothing changes. `(release stock id)` makes a `:quarantined` lot `:active`
  again; anything else changes nothing.
- `(take-from stock id n now)` returns `[stock' ok?]`. When lot `id` is
  usable at `now` and `1 <= n <= :qty`, its `:qty` drops by `n` and `ok?` is
  true; a lot whose `:qty` reaches 0 is removed from the stock. Otherwise
  the stock is unchanged and `ok?` is false.
- `(purge stock now)` removes every lot that has expired (`:expires <=
  now`), whatever its status.

## picking.core

- `(plan stock sku n now)` is the picks that would take `n` of `sku`: a
  vector of `[id k]` pairs, each taking `k >= 1` from lot `id`. Lots are
  used first-expiring-first: the usable lot of `sku` with the earliest
  `:expires` first, on equal `:expires` the lowest id; each lot is used up
  before the next is touched, and the last pick takes only what is still
  needed. With `n` 0 the plan is `[]`. When the usable lots of `sku` hold
  less than `n` in all, the plan is nil.
- `(pick stock order now)` fills an order, a vector of lines `{:sku Nat,
  :qty Nat}`, in order. A line is filled by taking its plan's picks, each
  through `stock.core/take-from`; a line whose plan is nil is short and
  takes nothing (it is never partly filled), and the next lines go on.
  It returns `[stock' report]` where the report is

      {:filled [index ...], :short [index ...], :picks [[index id k] ...]}

  `:filled` and `:short` hold the lines' indexes in order (a line of qty 0
  is filled, with no picks); `:picks` holds every pick made, in the order
  made, tagged with its line's index.
