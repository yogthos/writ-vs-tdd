#!/bin/bash
# score-tdd.sh RUNDIR TASK SET [OUT] : run RUNDIR's clojure.test suite against the reference and
# against every mutant in mutants/SET/TASK (SET is hand, deep or auto), each mutant standing in
# for its namespace with the reference for the other. Also grades each mutant with the hidden
# grader, so equivalent mutants can be told apart. One file per run in OUT (default
# results-new/SET/TASK/tdd): MUTANT.tdd (PASS/FAIL per test) and MUTANT.grade.
. "$(dirname "$0")/common.sh"
RUN=$(cd "$1" && pwd); T=$2; SET=$3; OUT=${4:-$ROOT/results-new/$SET/$T/tdd}; A=$(ns-a $T); B=$(ns-b $T)
mkdir -p "$OUT"; OUT=$(cd "$OUT" && pwd)
one() { # name ns mutant-file (empty for the reference)
  local d; d=$(mktemp -d "$ROOT/.work-XXXXXX"); mkdir -p "$d/src"; cp -r "$ROOT/hidden/$T/ref/." "$d/src/"
  [ -n "$3" ] && sed "1s/(ns $2\.[a-z0-9]*/(ns $2.core/" "$3" > "$d/src/$2/core.clj"
  echo "{:paths [\"src\" \"$RUN/test\" \"$ROOT/scripts/clj\"]}" > "$d/deps.edn"
  (cd "$d" && timeout 300 jolt -m pertest $A.core-test $B.core-test > "$OUT/$1.tdd" 2>&1; echo "exit $?" >> "$OUT/$1.tdd")
  echo "{:paths [\"src\" \"$ROOT/hidden/$T/grader\"]}" > "$d/deps.edn"
  [ -n "$3" ] && (cd "$d" && timeout 600 jolt -m "$(grader-ns $T)" 2>&1 | grep -v '^warning' > "$OUT/$1.grade"; echo "exit ${PIPESTATUS[0]}" >> "$OUT/$1.grade")
  rm -rf "$d"
}
one ref "" ""
for ns in $A $B; do
  for m in "$ROOT/mutants/$SET/$T/$ns"/*.clj; do one "$ns.$(basename "$m" .clj)" $ns "$m" & throttle ${JOBS:-3}; done
done
wait
