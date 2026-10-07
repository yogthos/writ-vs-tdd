#!/bin/bash
# cross.sh [RUNS] [OUT] : run each task's TDD suite on the plain and writ implementations, and
# each task's writ laws on the plain and TDD implementations (scripts/clj/behav.clj: writ's static
# coding rules are switched off, since code not written for writ does not follow them).
# RUNS defaults to runs/, OUT to results-new/cross.
. "$(dirname "$0")/common.sh"
RUNS=$(cd "${1:-$ROOT/runs}" && pwd); OUT=${2:-$ROOT/results-new/cross}; mkdir -p "$OUT"; OUT=$(cd "$OUT" && pwd)
for T in $TASKS; do A=$(ns-a $T); B=$(ns-b $T)
  for arm in plain writ; do
    D=$(mktemp -d "$ROOT/.work-XXXXXX")
    echo "{:paths [\"$RUNS/$T-$arm/src\" \"$RUNS/$T-tdd/test\" \"$ROOT/scripts/clj\"]}" > "$D/deps.edn"
    (cd "$D" && timeout 600 jolt -m pertest $A.core-test $B.core-test > "$OUT/tdd-tests-on-$T-$arm.txt" 2>&1); rm -rf "$D"
  done
  for arm in plain tdd; do
    ( D=$(mktemp -d "$ROOT/.work-XXXXXX")
      echo "{:paths [\"$ROOT/scripts/clj\" \"$RUNS/$T-$arm/src\" \"$RUNS/$T-writ/test\"] :deps {$WRIT_DEP}}" > "$D/deps.edn"
      (cd "$D" && for ns in $A $B; do timeout 3600 jolt -m behav $ns.core-spec $ns.core 2>&1 | grep -v '^warning'; done > "$OUT/writ-laws-on-$T-$arm.txt"); rm -rf "$D" ) &
    throttle ${JOBS:-2}
  done
done
wait
