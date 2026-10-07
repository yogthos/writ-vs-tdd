(ns dispatch.core
  (:require [queue.core :as queue]))

(defn- holds-live-lease?
  "True when worker holds a lease that is still live at now."
  [q worker now]
  (boolean
   (some (fn [job]
           (and (= :leased (:status job))
                (:lease job)
                (= worker (:worker (:lease job)))
                (< now (:until (:lease job)))))
         (vals (:jobs q)))))

(defn tick
  "Expire the queue at now, then offer jobs to workers in order.  A worker
  already holding a live lease gets nothing.  Returns [q' assignments] where
  assignments is a vector of [worker id] pairs in the order leased."
  [q workers now]
  (let [q (queue/expire q now)]
    (loop [q q
           ws (seq workers)
           assignments []]
      (if ws
        (let [w (first ws)]
          (if (holds-live-lease? q w now)
            (recur q (next ws) assignments)
            (let [[q' id] (queue/lease q w now)]
              (recur q' (next ws)
                     (if id (conj assignments [w id]) assignments)))))
        [q assignments]))))

(defn backlog
  "How many ready jobs are due at now."
  [q now]
  (count (filter (fn [job]
                   (and (= :ready (:status job))
                        (<= (:ready-at job) now)))
                 (vals (:jobs q)))))
