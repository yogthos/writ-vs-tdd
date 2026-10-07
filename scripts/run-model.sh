#!/bin/bash
# run-model.sh [-j N] [-m MODEL] [TASK-ARM ...] : make fresh workspaces (runs-new/) for the given
# runs (default: all twelve) and run the model headless in each through opencode, N at a time
# (default 4). While the model runs, everything that gives answers away -- hidden/, mutants/,
# results/, runs/, logs/ and the write-up -- is made unreadable (chmod 000) and restored
# afterwards. Transcripts go to runs-new/logs/.
. "$(dirname "$0")/common.sh"
J=4; MODEL=deepseek/deepseek-flash
while getopts "j:m:" o; do case $o in j) J=$OPTARG;; m) MODEL=$OPTARG;; esac; done; shift $((OPTIND-1))
NAMES=${*:-$(for t in $TASKS; do for a in plain tdd writ; do echo $t-$a; done; done)}
mkdir -p "$ROOT/runs-new/logs"
for n in $NAMES; do "$ROOT/scripts/new-run.sh" "${n%-*}" "${n#*-}" > /dev/null || exit 1; done
LOCK="hidden mutants results runs logs"
restore() { for d in $LOCK; do chmod 755 "$ROOT/$d"; done; chmod 644 "$ROOT"/*.md; }
trap restore EXIT INT TERM
for d in $LOCK; do chmod 000 "$ROOT/$d"; done; chmod 000 "$ROOT"/*.md
for n in $NAMES; do
  ( cd "$ROOT/runs-new/$n" && start=$(date +%s) &&
    timeout 7200 opencode run --standalone --auto --format json -m "$MODEL" --title "$n" \
      "$(cat "$ROOT/prompts/$n.prompt")" > "$ROOT/runs-new/logs/$n.jsonl" 2> "$ROOT/runs-new/logs/$n.err"
    echo "$n exit $? after $(( ($(date +%s) - start) / 60 )) min" | tee -a "$ROOT/runs-new/logs/runs.txt" ) &
  throttle "$J"
done
wait
