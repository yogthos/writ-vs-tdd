# writ vs. TDD vs. a plain-English spec

Does writing a [writ](https://github.com/jlt-commons/writ) spec catch mistakes that
test-driven development, or careful work from a plain-English spec, lets through? This
folder holds one experiment on that question: four multi-part programming tasks, each
solved three ways by the same model, and the hidden references, bugs and scripts used
to score the results. Everything needed to rerun or extend it is here.

## Summary

- **All three approaches produced correct code.** For each task, the plain-spec, TDD
  and writ implementations all matched the hidden reference on 3000 random inputs.
- **One real bug got past the plain spec and TDD, and writ caught it.** TASK.md
  defines a booking as covering ticks `start <= t < end` and says two bookings clash
  when they share a tick. So a zero-length booking clashes with nothing. The plain-spec
  implementation, the TDD implementation *and my own hand-written reference* all let a
  zero-length booking block the room. No TDD test and no grader input had one.
  Deepseek's writ spec stated the rule as a law. writ's generated calendars include
  zero-length bookings, so its laws reject all three of those implementations; the
  writ implementation is correct.
- **On bugs that each break one stated rule, TDD and writ tie.** The TDD suites
  caught 62 of 63 hand-written mutants and every non-equivalent one of 304 machine
  mutants. The writ specs caught 63 of 63 and 301 of 304, one of which no test and no
  grader input found.
- **On bugs that only show on uncommon inputs, writ caught all 10 and TDD 7.** TDD
  missed a sum over the *distinct* quantities, a search that steps by the slot length,
  and rooms tried in set order instead of sorted.
- **TDD's kills are thinner.** About a third of TDD's kills (22 of 62 hand-written,
  5 of 7 uncommon-input) rest on one test. writ's rest on a single law less often
  (10 of 63 and 5 of 10).
- **writ costs more time.** The writ runs took 19 to 30 minutes each; the TDD runs
  2 to 3 and the plain runs about 1.

The detail and the caveats follow. The short version: with a prompt that names the
rules and tie-breaks, this model writes thorough example tests, and they catch bugs
that break a stated rule as well as writ does. writ's advantage was on input shapes
the author never thinks to try.

## The model and the harness

- **Model:** `deepseek/deepseek-flash`, run headless through
  [opencode](https://opencode.ai) v2.0.21:

      opencode run --standalone --auto --format json -m deepseek/deepseek-flash --title NAME "PROMPT"

  `--auto` lets it read, write and run shell commands in its workspace without asking.
  Each run had a 2-hour limit; none came near it.
- **Runtime:** [jolt](https://github.com/jolt-lang) v0.8.18 (Clojure on Chez Scheme).
- **writ:** v0.1.4, commit `bf28f364717636cf3e41c1f6918671cc136dc1b9`. The writ
  workspaces pin it as a git dep.
- **Isolation:** each run had its own workspace and git repo. While the models ran,
  the references, mutants, graders and earlier experiments were `chmod 000`. Every
  transcript was then checked for reads outside its own workspace; none read
  anything outside it.
- **Date:** 2026-10-06. All twelve runs used the same model on the same day.

## The tasks

Each task is two pure Clojure namespaces, the second built on the first. These were
the four hardest tasks from earlier rounds of writ testing. They have orderings and
tie-breaks, boundaries, retry rules, state that is threaded along, and a recursive
data type. The full statements are in [`tasks/`](tasks).

| task | namespaces | what it asks for |
|---|---|---|
| [t4](tasks/t4.md) | `queue.core`, `dispatch.core` | A job queue with priorities, leases that expire after 10 ticks, retries with backoff `2^attempts` that kill a job at its third attempt, and an id tie-break. A dispatcher hands jobs to a vector of workers each tick and skips any worker holding a live lease. |
| [t5](tasks/t5.md) | `stock.core`, `picking.core` | Warehouse lots with expiry and quarantine; `take-from` refuses bad takes. A first-expiring-first picker plans and fills multi-line orders, never fills a line partly, and reports filled and short lines. |
| [t6](tasks/t6.md) | `rooms.core`, `slots.core` | Meeting-room bookings on half-open tick ranges: `free?`, `book`, `cancel` by owner only, `busy`, `usage`. A slot finder returns the earliest slot (smallest start, then lowest room) and books a list of requests in order. |
| [t7](tasks/t7.md) | `expr.core`, `stack.core` | Arithmetic expressions as tagged vectors: `evaluate`, a bottom-up `simplify` with five ordered rules, `size`, `variables`. A compiler to a stack machine, and a `run` that returns nil on stack underflow or leftover values. |

## The three arms

The prompts are in [`prompts/`](prompts). All three name the same things to get
right, task by task. For t5, for example: "every rule, ordering and tie-break,
boundary, when a lot is usable, when a take is refused, how a plan is built, and
what pick reports".

- **plain:** "Read TASK.md and AGENTS.md. Write src/… implementing everything TASK.md
  says -- *(the list)* -- exactly. … you may try your code with jolt -e as you go. Do
  not write test files." No test framework is set up.
- **tdd:** "Work test-first. Stage 1, the tests: write clojure.test tests … Together
  they must capture everything TASK.md says -- *(the list)* -- so that an
  implementation that gets any of them wrong fails a test. Write as many tests as that
  takes. Commit the tests … before writing any implementation. Stage 2 … run bin/test
  until it exits 0. Treat your tests as the contract …" The workspace has
  `bin/test` and a clojure.test runner. clojure.test.check isn't available on jolt,
  so these are example-based tests.
- **writ:** the same two stages with writ specs: "write … a writ spec for … that
  builds on the … spec. Together they must capture everything TASK.md says -- *(the
  list)* -- … ask for proof where the prover can give it. Before the code exists,
  bin/check checks the specs alone; use that. Commit the specs … Read the notes the
  report gives …" The workspace has `bin/check` and `WRIT.md` (writ's skill guide,
  [`templates/WRIT.md`](templates/WRIT.md)).

The results of each run are in [`runs/`](runs): the code, the tests or specs, the
model's `NOTES.md`, and `HISTORY.txt` (its git log). The transcripts are in
[`logs/`](logs).

| run | minutes | tool calls | code lines | test/spec lines | what it wrote |
|---|---|---|---|---|---|
| t4-plain | 1 | 18 | 143 | – | – |
| t4-tdd | 3 | 28 | 135 | 521 | 65 tests |
| t4-writ | 19 | 118 | 90 | 522 | 49 checks (36 + 13) |
| t5-plain | 1 | 12 | 134 | – | – |
| t5-tdd | 2 | 18 | 132 | 356 | 40 tests |
| t5-writ | 20 | 97 | 135 | 337 | 32 checks (22 + 10) |
| t6-plain | 1 | 14 | 107 | – | – |
| t6-tdd | 2 | 18 | 124 | 370 | 48 tests |
| t6-writ | 21 | 68 | 65 | 322 | 31 checks (19 + 12) |
| t7-plain | 0.5 | 10 | 121 | – | – |
| t7-tdd | 2 | 26 | 147 | 358 | 26 tests |
| t7-writ | 30 | 90 | 156 | 426 | 77 checks (53 + 24) |

"Checks" are what writ's report counts for each spec: laws, examples, and the
obligations of graphs and components. Two runs, t6-tdd and t7-writ, failed at once
on a transient provider error ("An assistant message with 'tool_calls' must be
followed by tool messages") and were rerun from a fresh workspace. The numbers above
are from the reruns.

## How the results were scored

### 1. Is the code right? (the grader)

Each task has a hidden reference implementation ([`hidden/tN/ref`](hidden)) and a
grader ([`hidden/tN/grader`](hidden)). The grader calls every function of the
implementation and of the reference on 3000 random inputs (random states, built
through the public functions or drawn directly) and reports any disagreement.
`scripts/grade.sh` runs it.

### 2. Do the checks reject wrong code? (mutants)

A mutant is the reference with a bug put in. One namespace is replaced by the mutant
and the other stays the reference. A mutant is **killed** by:

- **TDD** when one of the run's tests fails on it while passing on the reference (or
  the suite doesn't load, or hangs). [`scripts/clj/pertest.clj`](scripts/clj/pertest.clj)
  runs each `deftest` on its own.
- **writ** when one of the run's laws fails on it, or writ rejects it for a reason the
  reference doesn't share, such as a static type error. writ ran behaviour-only (see
  [`scripts/clj/mutrun.clj`](scripts/clj/mutrun.clj)): `{:adequacy false :require
  :tested :prove false :seed 7}`. No proofs are asked for, so every law is judged by
  its tests: random trials, the spec's examples, and writ's tie witnesses. Turning the
  prover on can only add kills, so these writ numbers are a lower bound.

There are three sets of mutants:

- **hand** ([`mutants/hand`](mutants/hand), 63): written in earlier rounds, before
  these runs. Each breaks one stated rule, e.g. "ties go by id before ready time", "a
  lease lives through its last tick", "touching bookings clash", "a negation is
  compiled before its part".
- **deep** ([`mutants/deep`](mutants/deep), 10): bugs that only show on less common
  inputs, the kind that survive a code review. I wrote them after seeing the
  hand-mutant results but before reading any test suite. They show the *kind* of gap;
  they don't measure a rate.

  | mutant | bug |
  |---|---|
  | t4 queue.z1 | `next-job` breaks a full tie (priority and ready-at) by the jobs map's order, not by id |
  | t4 dispatch.z2 | `tick` decides who is busy once, before it leases anything, so a worker listed twice gets two jobs |
  | t5 picking.z1 | `plan` sorts lots by expiry only, leaving equal expiries in the map's order |
  | t5 picking.z2 | `plan` checks "no lots left" before "nothing still needed" |
  | t5 stock.z3 | `available` sums the *distinct* quantities of the usable lots |
  | t6 rooms.z1 | a clash is found only when the new range starts inside a booking (misses enclosure) |
  | t6 slots.z2 | `earliest` tries starts `from`, `from+len`, `from+2len`, … |
  | t6 slots.z3 | `earliest` tries rooms in the set's order instead of sorted |
  | t7 expr.z1 | rule 1 folds only parts that were numbers *before* they were simplified |
  | t7 stack.z2 | `compile-expr` folds the negation of a number into one push |

- **auto** ([`mutants/auto`](mutants/auto), 304): every one-token change that
  [`scripts/mutate.py`](scripts/mutate.py) makes to the references: `<`↔`<=`, `+`↔`-`,
  `inc`↔`dec`, `first`↔`last`, `and`↔`or`, `when`↔`when-not`, integer literals ±1,
  `sort`→`reverse`, and so on. `index.tsv` beside each set lists every change. Each
  auto mutant was also graded, to tell the equivalent ones apart.

### 3. Cross-checks (natural bugs)

Each arm's checks were also run on the other arms' implementations: the TDD suite on
the plain and writ code, and the writ laws on the plain and TDD code
([`scripts/cross.sh`](scripts/cross.sh)). The TDD and plain code doesn't follow writ's
coding rules (termination shapes, taking tagged data apart with `case`, the call graph
a spec names). So for this check only, those static rules are switched off and only
the laws run ([`scripts/clj/behav.clj`](scripts/clj/behav.clj)).

## Results

### The code

The grader found every function of all 12 implementations in agreement with the
reference. It found no bug in the plain-spec code either: on these tasks and with
this prompt, the model wrote code a 3000-input random check couldn't fault. One bug
was outside the grader's reach, as the next section shows.

### The bug only writ caught: a zero-length booking

TASK.md for t6 says a booking covers the ticks `start <= t < end`, and that "two
bookings clash when they are for the same room and their tick ranges share a tick".
A booking with `start = end` covers no ticks, so it clashes with nothing. The usual
overlap test `(and (< s1 e2) (< s2 e1))` gets this wrong: `[5,5)` and `[0,10)` pass
both comparisons. Bookings can't be created empty through `book`, but a calendar is
an input to every function, and the task doesn't forbid an empty booking in it.

| | `(free? {… booking [5,5) in room 1 …} 1 0 10)` | `(earliest … 10 0 20)` |
|---|---|---|
| what TASK.md says | `true` | `[1 0]` |
| my reference (written by hand, before any of this) | `false` | `[1 5]` |
| plain arm | `false` | `[1 5]` |
| TDD arm | `false` | `[1 5]` |
| writ arm | `true` | `[1 0]` |

The grader never generates an empty booking, and none of the 48 TDD tests uses one.
The TDD suite passes the writ implementation and the buggy ones alike.

Deepseek's writ spec has a model law `free?-means-nothing-clashes` and a law
`an-empty-booking-clashes-with-nothing`. Its first draft of the latter didn't call
any `rooms.core` function, and writ reported `law
an-empty-booking-clashes-with-nothing is vacuous: it calls no fn of rooms.core`;
deepseek fixed the law before writing the code. Its notes:

> **Empty ranges.** TASK defines a clash as ranges that *share a tick*, so an empty
> booking (`start = end`) collides with nothing … The usual half-open overlap test
> `(< s e2) (< s2 e)` gets this wrong; the spec keeps the non-emptiness conjunct on
> both sides.

writ's generated calendars contain empty bookings, so the model law alone rejected my
reference with a concrete counterexample. That is how this bug was found. Running the
writ laws over the other arms' code ([`results/cross`](results/cross)) gives:

| checks | on plain code | on TDD code | on writ code |
|---|---|---|---|
| TDD suites (all four tasks) | all pass | – | all pass |
| writ laws, t4, t5, t7 | all hold | all hold | – |
| writ laws, t6 rooms | `free?-means-nothing-clashes`, `an-empty-booking-clashes-with-nothing` fail | the same two fail | – |
| writ laws, t6 slots | `earliest-is-the-model`, `book-all-follows-the-requests` fail | the same two fail | – |

I then corrected the reference and every rooms mutant to match TASK.md, and all the
t6 mutant results here were scored against the corrected versions. The original
reference is kept in [`hidden/t6-original-ref`](hidden/t6-original-ref). After the
fix, one auto mutant (`rooms.a002`, `<`→`<=` in the new guard) puts the bug back. The
grader and the TDD suite pass it; writ fails it on
`an-empty-booking-clashes-with-nothing`.

The writ rooms spec also fails the plain and TDD code on
`busy-is-the-rooms-pairs-sorted`. That one is not a bug: TASK.md says only "sorted by
start". The writ arm settled the tie (equal starts go by end), noting "this is a
choice the task did not make", after writ's adequacy check showed a reversed
tie-break passed every law. Bookings with equal starts in one room can only occur
alongside an empty or clashing booking anyway.

### Mutants

From [`scripts/summarize.py`](scripts/summarize.py) over [`results/`](results):

| set | task | mutants | grader detects | TDD kills | writ kills | probably equivalent |
|---|---|---|---|---|---|---|
| hand | t4 queue/dispatch | 14 | 14 | 14 | 14 | 0 |
| hand | t5 stock/picking | 17 | 16 | 17 | 17 | 0 |
| hand | t6 rooms/slots | 16 | 16 | 15 | 16 | 0 |
| hand | t7 expr/stack | 16 | 16 | 16 | 16 | 0 |
| hand | **all** | **63** | 62 | **62** | **63** | 0 |
| deep | t4 | 2 | 1 | 2 | 2 | 0 |
| deep | t5 | 3 | 2 | 2 | 3 | 0 |
| deep | t6 | 3 | 3 | 1 | 3 | 0 |
| deep | t7 | 2 | 2 | 2 | 2 | 0 |
| deep | **all** | **10** | 8 | **7** | **10** | 0 |
| auto | t4 | 60 | 57 | 59 | 59 | 1 |
| auto | t5 | 58 | 58 | 58 | 58 | 0 |
| auto | t6 | 45 | 42 | 42 | 43 | 2 |
| auto | t7 | 141 | 141 | 141 | 141 | 0 |
| auto | **all** | **304** | 298 | **300** | **301** | 3 |

(t4 has 60 auto mutants but only 57 detected by the grader: two crash the grader before
it compares anything, and both checks kill those two. The grader also misses one hand
mutant and two deep ones; those are tie-break bugs that 3000 random inputs never hit.)

**Caught by one approach and not the other:**

| set | mutant | the bug | TDD | writ |
|---|---|---|---|---|
| hand | t6 slots.t02 | a slot may start before `from` | survives | `earliest-is-the-model`, `book-earliest-books-the-earliest`, `book-all-follows-the-requests` |
| deep | t5 stock.z3 | `available` sums the distinct quantities | survives | `available-counts-the-usable-lots` |
| deep | t6 slots.z2 | `earliest` steps through starts by `len` | survives | `book-all-follows-the-requests` |
| deep | t6 slots.z3 | rooms tried in set order, not sorted | survives | `earliest-is-the-model`, `earliest-prefers-the-lower-room`, … |
| auto | t6 rooms.a002 | an empty booking clashes again | survives | `an-empty-booking-clashes-with-nothing` |

Nothing that TDD caught got past writ.

**Why the TDD suites missed these.** In each case the examples were too regular:

- *stock.z3:* every lot in every `available` test has a different quantity, so the
  sum of the distinct quantities equals the sum.
- *slots.z2:* almost every gap test asks for a slot of length 1, and the one asking
  for length 2 has its gap at an aligned start. So stepping by `len` finds the same
  slots.
- *slots.z3:* the tie-break tests build rooms like `#{3 1 2}`. On jolt a small set of
  small integers iterates in sorted order, so "set order" and "sorted" agree in every
  test. The bug would appear only with other room ids.
- *slots.t02:* no test has a free gap before `from` that is earlier than every
  candidate start after it.

writ's laws state the rule over every input: `available` is the sum over the usable
lots, and `earliest` is the smallest free start and then the lowest room. writ's
generated inputs include repeated quantities, unaligned gaps and arbitrary room ids.

**Probably equivalent mutants:** neither check catches these and the grader sees no
difference. Reading them confirms they can't change any result:
`dispatch.a005` (`busy?` with `<=`, but `tick` expires stale leases first),
`slots.a005` (dropping `distinct` from the candidate starts), and `slots.a007` (`from`
is a candidate start anyway).

**How many tests or laws catch each mutant.** On the hand set, 22 of TDD's 62 kills
rest on a single test, against 10 of writ's 63 on a single law. On the deep set it is
5 of 7 against 5 of 10. So a TDD suite is usually one deleted or weakened test away
from letting a bug through. writ's model laws ("`earliest` is the model",
"`available` counts the usable lots") cover several rules at once.

**writ's static checks catch real bugs too.** 34 auto mutants were rejected by writ
before any law ran. Examples: "`evaluate` expects Expr for argument 1 but is passed
Keyword", "`size` returns Nat but its body has type Int", "`evaluate`: add has 2
field(s), but `e` is read at position 3", and "`recur` in `plan` does not descend"
(`rest`→`identity`, an infinite loop). The grader confirms all of them as wrong, so
none is a false alarm.

## Caveats

- **One model, one run per arm, four tasks.** LLM runs vary. A rerun could produce a
  TDD suite that tests empty bookings, or a writ spec that misses them.
- **The prompts named the rules to capture.** That made the TDD suites unusually
  thorough (26–65 tests per task), and it is why the plain arm got the code right.
  With a vaguer prompt, all three arms would likely do worse, and the gap might
  differ.
- **The deep mutants are mine.** They show what kind of bug example tests miss, but
  10 hand-picked bugs say nothing about how often such bugs occur.
- **The writ mutant scores are behaviour-only with the prover off.** That is a lower
  bound. An early pass that also set `:coverage false` turned off writ's tie
  witnesses, and writ then missed the map-order tie bug (queue.z1). With writ's normal
  options it catches it. Don't score writ with coverage off.
- **writ's structural rules aren't counted as kills when the reference breaks them
  too.** The t4 writ spec says `tick` calls exactly `queue.core/expire` and
  `queue.core/lease`. My reference has a private `busy?` helper, so writ rejects the
  reference on that rule, and every dispatch mutant fails it as well. Those rejections
  aren't counted; each dispatch mutant's kill comes from a failed law.
- **The grader is a random tester.** It missed the empty-booking bug and three
  tie-break mutants. So "all 12 implementations are correct" means correct as far as
  3000 random inputs can tell. The empty-booking bug was found because writ's specs
  rejected my reference.

## Rerunning it

You need jolt v0.8.18 or later, python3, and, to rerun the model,
[opencode](https://opencode.ai) with a DeepSeek key in `DEEPSEEK_API_KEY` (or any model
opencode supports, via `-m`). The writ dependency is fetched from GitHub at the pinned
commit.

```sh
# grade an implementation against the hidden reference
scripts/grade.sh runs/t6-plain t6

# score a TDD suite / a writ spec on a mutant set (hand, deep or auto)
scripts/score-tdd.sh  runs/t6-tdd  t6 deep      # -> results-new/deep/t6/tdd
scripts/score-writ.sh runs/t6-writ t6 deep      # -> results-new/deep/t6/writ
python3 scripts/summarize.py results-new        # the tables above, for what you scored

# cross-checks: each arm's checks on the other arms' code
scripts/cross.sh runs results-new/cross

# rerun the model: fresh workspaces in runs-new/, 4 at a time. While it runs, hidden/,
# mutants/, results/, runs/, logs/ and this file are made unreadable, then restored.
scripts/run-model.sh -j 4                       # all twelve
scripts/run-model.sh t6-tdd t6-writ             # or some
scripts/run-model.sh -m anthropic/claude-sonnet-5-5 t6-writ   # another model
```

Then score the new runs the same way, pointing the scripts at `runs-new/…`. Scoring is
quick for TDD (all 377 mutants of one suite in a few minutes) and slow for writ (a
minute or two per spec plus some seconds per mutant; the auto set takes about an
hour). Set `JOBS` to run more at once; a writ check needs about 1 GB of memory.

To try another task: write `tasks/tN.md`, a reference in `hidden/tN/ref`, a grader
`hidden/tN/grader/gradeN.clj` (see the existing ones: random inputs, compare with
`ref.*`), mutants in `mutants/hand/tN/<ns>/`, and prompts `prompts/tN-{plain,tdd,writ}.prompt`.
Then add the namespaces to `ns-a`/`ns-b` in `scripts/common.sh`.

## What's in this folder

| path | what |
|---|---|
| `writ-vs-tdd.md` | this write-up |
| `tasks/` | the four task statements, as given to the model (TASK.md) |
| `prompts/` | the twelve prompts, one per task and arm |
| `templates/` | files put in each workspace: the clojure.test runner, writ's check runner, WRIT.md |
| `runs/` | the twelve workspaces as the model left them: `src/`, `test/`, `NOTES.md`, `HISTORY.txt` |
| `logs/` | the model's transcripts (opencode JSON events), one per run |
| `hidden/` | the references and graders (`t6` corrected; `t6-original-ref` is the one with the empty-booking bug) |
| `mutants/` | `hand/`, `deep/` and `auto/` mutants per task (`auto/*/index.tsv` lists each change) |
| `results/` | raw scoring output: `SET/TASK/tdd/*.tdd` and `*.grade` per mutant, `SET/TASK/writ/*.txt`, `cross/` |
| `scripts/` | setup, model runs, grading, scoring, mutation and summary scripts |
