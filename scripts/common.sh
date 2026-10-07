# Sourced by the other scripts: paths, the tasks' namespaces, and the writ release used.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WRIT_DEP='io.github.jlt-commons/writ {:git/url "https://github.com/jlt-commons/writ" :git/sha "bf28f364717636cf3e41c1f6918671cc136dc1b9"}'
TASKS="t4 t5 t6 t7"
# ns-a / ns-b TASK: the task's two namespaces (the second builds on the first)
ns-a() { case $1 in t4) echo queue;; t5) echo stock;; t6) echo rooms;; t7) echo expr;; esac; }
ns-b() { case $1 in t4) echo dispatch;; t5) echo picking;; t6) echo slots;; t7) echo stack;; esac; }
# grader namespace of a task
grader-ns() { echo "grade${1#t}"; }
# throttle N: wait until fewer than N background jobs run
throttle() { while [ "$(jobs -r | wc -l)" -ge "$1" ]; do sleep 1; done; }
