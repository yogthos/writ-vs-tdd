#!/bin/bash
# new-run.sh TASK ARM [DIR] : make a fresh workspace for one arm (plain, tdd or writ) of a task
# (t4..t7) in DIR (default runs-new/TASK-ARM), committed to its own git repo, ready for the model.
. "$(dirname "$0")/common.sh"
T=$1; ARM=$2; W=${3:-$ROOT/runs-new/$T-$ARM}; A=$(ns-a $T); B=$(ns-b $T)
[ -n "$A" ] && [ -f "$ROOT/prompts/$T-$ARM.prompt" ] || { echo "usage: new-run.sh t4|t5|t6|t7 plain|tdd|writ [dir]"; exit 1; }
rm -rf "$W"; mkdir -p "$W/src"
cp "$ROOT/tasks/$T.md" "$W/TASK.md" || exit 1
printf '.jolt/\n.writ-cache/\n' > "$W/.gitignore"
case $ARM in
writ)
  mkdir -p "$W/bin" "$W/test"; cp "$ROOT/templates/check_runner.clj" "$W/test/"; cp "$ROOT/templates/WRIT.md" "$W/"
  cat > "$W/deps.edn" <<EOF
{:paths ["src"]
 :deps  {$WRIT_DEP}
 :aliases
 {:check {:extra-paths ["test"]
          :main-opts   ["-m" "check-runner"]}}}
EOF
  cat > "$W/bin/check" <<EOF
#!/bin/sh
# Check both namespaces against their writ specs: bin/check [--seed N]
cd "\$(dirname "\$0")/.."
exec jolt -M:check $A.core-spec $B.core-spec "\$@"
EOF
  cat > "$W/AGENTS.md" <<EOF
# Project notes

- The runtime is jolt (Clojure on Chez Scheme, no JVM). Run code with
  \`jolt -e '...'\` from this directory; the deps.edn puts \`src\` on the path.
  \`jolt -M:check -e '...'\` also puts \`test\` on the path.
- \`bin/check\` runs writ's check of \`src/$A/core.clj\` against
  \`test/$A/core_spec.clj\` and of \`src/$B/core.clj\` against
  \`test/$B/core_spec.clj\`, and prints both reports. \`bin/check --seed N\`
  replays a seed. Exit code 0 means both passed.
- WRIT.md is the guide to writ: how to write a spec, the rules the
  implementation must follow, and how to read its reports. Its section
  "Building on another spec" is how the $B spec builds on the $A
  spec.
EOF
  ;;
tdd)
  mkdir -p "$W/bin" "$W/test"; cp "$ROOT/templates/test_runner.clj" "$W/test/"
  cat > "$W/deps.edn" <<EOF
{:paths ["src"]
 :aliases
 {:test {:extra-paths ["test"]
         :main-opts   ["-m" "test-runner"]}}}
EOF
  cat > "$W/bin/test" <<EOF
#!/bin/sh
# Run the clojure.test tests of both namespaces: bin/test
cd "\$(dirname "\$0")/.."
exec jolt -M:test $A.core-test $B.core-test
EOF
  cat > "$W/AGENTS.md" <<EOF
# Project notes

- The runtime is jolt (Clojure on Chez Scheme, no JVM). Run code with
  \`jolt -e '...'\` from this directory; the deps.edn puts \`src\` on the path.
  \`jolt -M:test\` also puts \`test\` on the path.
- Tests use clojure.test (\`deftest\`, \`is\`, \`testing\`, \`are\`).
  \`bin/test\` runs the tests in \`test/$A/core_test.clj\` (namespace
  \`$A.core-test\`) and \`test/$B/core_test.clj\` (namespace
  \`$B.core-test\`) and prints the results. Exit code 0 means every test
  passed.
EOF
  ;;
plain)
  echo '{:paths ["src"]}' > "$W/deps.edn"
  cat > "$W/AGENTS.md" <<EOF
# Project notes

- The runtime is jolt (Clojure on Chez Scheme, no JVM). Run code with
  \`jolt -e '...'\` from this directory; the deps.edn puts \`src\` on the path.
EOF
  ;;
esac
chmod +x "$W"/bin/* 2>/dev/null
(cd "$W" && git init -q && git add -A && git commit -q -m task)
echo "$W"
