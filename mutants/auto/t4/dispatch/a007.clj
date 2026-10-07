(ns dispatch.a007
  "Reference implementation (hidden from the model under test)."
  (:require [queue.core :as queue]))

(defn- busy? [q worker now]
  (boolean (some #(and (= :leased (:status %)) (= worker (:worker (:lease %))) (< now (:until (:lease %))))
                 (vals (:jobs q)))))

(defn tick [q workers now]
  (reduce (fn [[q as] w]
            (if-not (busy? q w now)
              [q as]
              (let [[q2 id] (queue/lease q w now)]
                (if id [q2 (conj as [w id])] [q as]))))
          [(queue/expire q now) []]
          workers))

(defn backlog [q now]
  (count (filter #(and (= :ready (:status %)) (<= (:ready-at %) now)) (vals (:jobs q)))))
