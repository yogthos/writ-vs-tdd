(ns behav
  "jolt -m behav SPEC-NS TARGET...: the spec's laws against each target, with writ's
  static coding rules and name-clash check turned off (behaviour only)."
  (:require [writ.spec :as spec] [clojure.string :as str]))

(defn -main [spec-ns & targets]
  (let [s (symbol spec-ns) orig @#'writ.spec/static-check]
    (require s)
    (doseq [t targets]
      (let [r (with-redefs [writ.spec/static-check (fn [e] (assoc (orig e) :ok true))
                            writ.spec/ambiguous-names (fn [& _] nil)]
                (try (spec/check s {:target (symbol t) :adequacy false :seed 7 :require :tested :prove false})
                     (catch Throwable e {:ok false :message (str "threw " (ex-message e))})))
            failed (keep #(when (= :failed (:status %)) (:law %)) (:laws r))]
        (println (format "%-12s %s %s" t (if (seq failed) "FAILS-LAWS" "LAWS-HOLD")
                         (if (seq failed) (str/join ", " failed) (subs (str (:message r)) 0 (min 160 (count (str (:message r))))))))
        (flush)))))
