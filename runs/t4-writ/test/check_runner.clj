(ns check-runner
  "jolt -M:check SPEC-NS... [--seed N]: run writ's check of each spec and print its report."
  (:require [writ.spec :as spec]))

(defn -main [& args]
  (let [[nses [_ seed]] (split-with #(not= "--seed" %) args)
        rs (doall (for [n nses]
                    (let [sym (symbol n)
                          _ (require sym)
                          r (spec/check sym (cond-> {} seed (assoc :seed (parse-long seed))))]
                      (println (:message r))
                      (println)
                      r)))]
    (System/exit (if (every? :ok rs) 0 1))))
