(ns dispatch.core
  "A dispatcher on the job queue: each tick hands due jobs to workers
  through queue.core."
  (:require [queue.core :as queue]))

(defn tick [q workers now]
  (let [expired (queue/expire q now)
        [q' as] (reduce
                  (fn [[q acc] w]
                    (if (some (fn [j]
                                (and (= :leased (:status j))
                                     (= w (:worker (:lease j)))
                                     (< now (:until (:lease j)))))
                              (vals (:jobs q)))
                      [q acc]
                      (let [[q2 id] (queue/lease q w now)]
                        [q2 (if (some? id) (conj acc [w id]) acc)])))
                  [expired []]
                  workers)]
    [q' as]))

(defn backlog [q now]
  (count (filter (fn [j] (and (= :ready (:status j)) (<= (:ready-at j) now)))
                 (vals (:jobs q)))))
