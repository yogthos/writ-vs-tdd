#!/bin/bash
# score-writ.sh RUNDIR TASK SET [OUT] : check the reference and every mutant in mutants/SET/TASK
# against RUNDIR's writ specs, behaviour only (scripts/clj/mutrun.clj), a few mutants per jolt
# process, JOBS (default 2) processes at a time. Writes OUT/SPEC.N.txt (default
# results-new/SET/TASK/writ), one line per target: PASSES, or REJECTS and why.
. "$(dirname "$0")/common.sh"
RUN=$(cd "$1" && pwd); T=$2; SET=$3; OUT=${4:-$ROOT/results-new/$SET/$T/writ}; A=$(ns-a $T); B=$(ns-b $T)
mkdir -p "$OUT"; OUT=$(cd "$OUT" && pwd); D=$(mktemp -d "$ROOT/.work-XXXXXX")
echo "{:paths [\"$ROOT/scripts/clj\" \"$ROOT/hidden/$T/ref\" \"$ROOT/mutants/$SET/$T\" \"$RUN/test\"] :deps {$WRIT_DEP}}" > "$D/deps.edn"
for ns in $A $B; do
  targets="$ns.core $(cd "$ROOT/mutants/$SET/$T/$ns" && ls *.clj | sed "s/\.clj$//; s/^/$ns./" | tr '\n' ' ')"
  set -- $targets; i=0
  while [ $# -gt 0 ]; do
    chunk="${*:1:6}"; shift $(( $# < 6 ? $# : 6 )); i=$((i+1))
    (cd "$D" && timeout 7200 jolt -m mutrun $ns.core-spec $chunk 2>&1 | grep -v '^warning' > "$OUT/$ns.core-spec.$i.txt") &
    throttle ${JOBS:-2}
  done
done
wait; rm -rf "$D"
