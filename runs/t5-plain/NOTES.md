# Notes on TASK.md

Ambiguities I noticed while implementing, and the reading I chose.

## stock.core

- **`receive` and `qty` values other than 0/at least 1.** TASK.md says
  "When `qty` is at least 1 ... With `qty` 0 ...". Quantities are Nats, so
  negatives cannot occur; I implemented the test as `qty >= 1`, which treats
  any non-positive value the same as 0. Only 0 is actually reachable.

- **`quarantine` / `release` on the "wrong" status.** TASK.md spells these
  out awkwardly: `quarantine` makes lot `id` `:quarantined`; `release` makes
  a `:quarantined` lot `:active` "again; anything else changes nothing". I
  read the "anything else" as applying to `release`: it only acts when the
  lot exists *and* is currently `:quarantined`. So `release` of an already
  `:active` lot, or a missing lot, is a no-op. `quarantine` of a missing lot
  is likewise a no-op (explicitly stated), and re-quarantining an already
  quarantined lot is harmless.

- **`take-from` and removal.** "a lot whose `:qty` reaches 0 is removed from
  the stock." I remove it by dissoc'ing the id from `:lots`; the id is not
  reused (`:next-id` is untouched, so it keeps increasing).

- **`take-from` `ok?`.** I return `[stock false]` with the *identical*
  (unchanged) stock whenever the condition `usable && 1 <= n <= :qty` fails.

- **`purge` expiry test.** "`:expires <= now`" is used literally, and it
  applies regardless of status — so quarantined expired lots go too.

## picking.core

- **`plan` result shape.** A vector of `[id k]` pairs. For `n` 0 it is `[]`,
  even if the sku holds nothing. For `n > 0` with insufficient usable stock
  it is `nil`.

- **Tie-breaks / ordering.** Lots are ordered by `:expires` ascending, then
  by `:id` ascending. I only consider usable lots (`stock.core/usable?`:
  `:active`, `:qty >= 1`, `now < :expires`), so quarantined and expired lots
  are skipped entirely.

- **"each lot is used up before the next is touched".** A pick takes
  `min(remaining, lot-qty)`, so interior lots are fully drained and only the
  final pick is partial. A lot with `:qty` 0 is never a pick (it is not
  usable).

- **`pick` and plans recomputed per line.** Each line's plan is computed
  against the stock *as it stands after the previous lines*, so a later line
  sees quantities already consumed. A `nil` plan means the line is short and
  nothing is taken from it (never partly filled); later lines still run
  against the unchanged stock.

- **Returning the picks.** `:picks` entries are `[line-index id k]`, appended
  in the order the picks are made. `take-from`'s `ok?` is not surfaced; a
  plan is guaranteed valid when executed immediately, so it is always true.

- **Line indexes.** Indexes are positions in `order` (0-based). A qty-0 line
  is `:filled` with no picks. `:filled` and `:short` preserve line order; an
  empty order yields `{:filled [] :short [] :picks []}`.

- **Empty order / missing keys.** Not specified beyond the above; I assume
  `order` is a vector of well-formed `{:sku ... :qty ...}` lines and `stock`
  always has `:lots` and `:next-id`.
