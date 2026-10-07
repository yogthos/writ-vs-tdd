(ns mutrun
  "jolt -m mutrun SPEC-NS TARGET...: check each target namespace against the spec and
  print one line per target: PASSES, or REJECTS with the laws that failed (or, when
  none did, the first line of the report, e.g. a static rule).  Behaviour only: no
  adequacy pass, no proofs asked for (:require :tested) and the prover off, so a law
  is judged by its tests (random trials, examples, tie witnesses).  Fixed seed 7."
  (:require [writ.spec :as spec] [clojure.string :as str]))

(defn -main [spec-ns & targets]
  (let [s (symbol spec-ns)]
    (require s)
    (doseq [t targets]
      (let [r (try (spec/check s {:target (symbol t) :adequacy false :seed 7 :require :tested :prove false})
                   (catch Throwable e {:ok false :message (str "threw " (ex-message e))}))
            failed (keep #(when (= :failed (:status %)) (:law %)) (:laws r))]
        (println (format "%-12s %s %s" t (if (:ok r) "PASSES" "REJECTS")
                         (if (seq failed) (str "failed: " (str/join ", " failed))
                             (str "| " (first (remove str/blank? (rest (str/split-lines (str (:message r))))))))))
        (flush)))))
