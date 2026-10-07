(ns pertest
  "jolt -m pertest TEST-NS...: run each deftest alone and print one line
  per test: PASS or FAIL and its name, then a SUMMARY line."
  (:require [clojure.test :as t]))

(defn- run-one [v]
  (let [counters (atom {:fail 0 :error 0 :pass 0})]
    (binding [t/report (fn [m] (case (:type m)
                                 :fail (swap! counters update :fail inc)
                                 :error (swap! counters update :error inc)
                                 :pass (swap! counters update :pass inc)
                                 nil))]
      (try (t/test-vars [v]) (catch Throwable _ (swap! counters update :error inc))))
    @counters))

(defn -main [& nses]
  (doseq [n nses]
    (let [s (symbol n)]
      (try (require s)
           (doseq [v (sort-by #(str (.sym %)) (filter #(:test (meta %)) (vals (ns-interns s))))]
             (let [c (run-one v)]
               (println (if (pos? (+ (:fail c) (:error c))) "FAIL" "PASS") (str n "/" (.sym v)))
               (flush)))
           (catch Throwable e (println "LOADFAIL" n (ex-message e))))))
  (println "SUMMARY done"))
