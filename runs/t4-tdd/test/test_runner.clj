(ns test-runner
  "jolt -M:test TEST-NS...: run the clojure.test tests in each namespace."
  (:require [clojure.test :as t]))

(defn -main [& nses]
  (let [syms (map symbol nses)]
    (apply require syms)
    (let [r (apply t/run-tests syms)]
      (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
