(ns dispatch.z2
  "Deep mutant: tick decides who is busy once, before it leases anything"
  (:require [queue.core :as queue]))

(defn- busy? [q worker now]
  (boolean (some #(and (= :leased (:status %)) (= worker (:worker (:lease %))) (< now (:until (:lease %))))
                 (vals (:jobs q)))))

(defn tick [q0 workers now]
  (let [q q0]
  (reduce (fn [[q as] w]
            (if (busy? (queue/expire q0 now) w now)
              [q as]
              (let [[q2 id] (queue/lease q w now)]
                (if id [q2 (conj as [w id])] [q as]))))
          [(queue/expire q now) []]
          workers)))

(defn backlog [q now]
  (count (filter #(and (= :ready (:status %)) (<= (:ready-at %) now)) (vals (:jobs q)))))
