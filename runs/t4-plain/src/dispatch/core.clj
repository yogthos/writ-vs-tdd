(ns dispatch.core
  (:require [queue.core :as queue]))

(defn- holds-live-lease?
  "True when worker holds a live lease on some job in q at now."
  [q worker now]
  (some (fn [[_ job]]
          (and (= :leased (:status job))
               (= worker (get-in job [:lease :worker]))
               (< now (get-in job [:lease :until]))))
        (:jobs q)))

(defn tick
  "Expires the queue at now, then walks workers in order: a worker that
  holds a live lease on some job gets nothing; any other leases the next
  job, if there is one. Returns [q' assignments], where assignments is a
  vector of [worker id] pairs, one per job leased, in the order leased.
  The same worker listed twice leases at most one job."
  [q workers now]
  (loop [q (queue/expire q now)
         pending (seq workers)
         assignments []]
    (if pending
      (let [worker (first pending)]
        (if (holds-live-lease? q worker now)
          (recur q (next pending) assignments)
          (let [[q' id] (queue/lease q worker now)]
            (if id
              (recur q' (next pending) (conj assignments [worker id]))
              (recur q (next pending) assignments)))))
      [q assignments])))

(defn backlog
  "The number of :ready jobs whose :ready-at is at most now."
  [q now]
  (count (filter (fn [job]
                   (and (= :ready (:status job)) (<= (:ready-at job) now)))
                 (vals (:jobs q)))))
