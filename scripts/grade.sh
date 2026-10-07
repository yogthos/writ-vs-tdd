#!/bin/bash
# grade.sh RUNDIR TASK : compare RUNDIR's implementation with the hidden reference on 3000
# random inputs, with the task's grader (hidden/TASK/grader). Prints GRADE lines.
. "$(dirname "$0")/common.sh"
RUN=$(cd "$1" && pwd); T=$2; D=$(mktemp -d "$ROOT/.work-XXXXXX")
echo "{:paths [\"$ROOT/hidden/$T/grader\" \"$RUN/src\"]}" > "$D/deps.edn"
(cd "$D" && timeout 1200 jolt -m "$(grader-ns $T)" 2>&1 | grep -v '^warning')
rm -rf "$D"
