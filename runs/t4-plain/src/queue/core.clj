(ns queue.core)

;; A queue is {:jobs {id job}, :next-id Nat}.
;; A job is
;;   {:id Nat, :priority Nat, :attempts Nat, :status S, :ready-at Nat, :lease L}
;; where S is one of :ready, :leased, :done, :dead, and L is
;; {:worker Nat, :until Nat} for a :leased job and nil for any other.
;; A lease is live while now < :until.

(defn- live? [lease now]
  (and (some? lease) (< now (:until lease))))

(defn- ready-now? [job now]
  (and (= :ready (:status job)) (<= (:ready-at job) now)))

(defn enqueue
  "Adds a new :ready job under :next-id, ready at now, no attempts, no
  lease. Returns [q' id]."
  [q priority now]
  (let [id (:next-id q)
        job {:id id
             :priority priority
             :attempts 0
             :status :ready
             :ready-at now
             :lease nil}]
    [(-> q
         (assoc-in [:jobs id] job)
         (update :next-id inc))
     id]))

(defn next-job
  "Id of the :ready job a worker should take next, or nil. Among the
  :ready jobs whose :ready-at is at most now: highest priority, then
  earliest :ready-at, then lowest id."
  [q now]
  (->> (vals (:jobs q))
       (filter #(ready-now? % now))
       (sort-by (juxt (comp - :priority) :ready-at :id))
       first
       :id))

(defn lease
  "Takes (next-job q now), makes it :leased with lease
  {:worker worker, :until (+ now 10)} and increments its attempts.
  Returns [q' id]; with no job to take the queue is unchanged and id is
  nil."
  [q worker now]
  (if-let [id (next-job q now)]
    [(-> q
         (update-in [:jobs id :attempts] inc)
         (assoc-in [:jobs id :status] :leased)
         (assoc-in [:jobs id :lease] {:worker worker :until (+ now 10)}))
     id]
    [q nil]))

(defn- held?
  "True when job id is :leased to worker with a live lease at now."
  [q id worker now]
  (let [job (get-in q [:jobs id])]
    (and job
         (= :leased (:status job))
         (= worker (get-in job [:lease :worker]))
         (live? (:lease job) now))))

(defn complete
  "When job id is :leased, its lease is worker's and is live, the job
  becomes :done with no lease. Anything else changes nothing."
  [q id worker now]
  (if (held? q id worker now)
    (assoc-in q [:jobs id] (assoc (get-in q [:jobs id]) :status :done :lease nil))
    q))

(defn- failed-job
  "The job as it stands after a failure at now: :dead with 3 or more
  attempts, otherwise :ready again at now + 2^attempts. Loses its lease."
  [job now]
  (if (>= (:attempts job) 3)
    (assoc job :status :dead :lease nil)
    (assoc job :status :ready
           :ready-at (+ now (bit-shift-left 1 (:attempts job)))
           :lease nil)))

(defn fail
  "On the same condition as complete, the job fails: with 3 or more
  attempts it becomes :dead, otherwise :ready again, ready at
  now + 2^attempts. Either way it loses its lease. Anything else changes
  nothing."
  [q id worker now]
  (if (held? q id worker now)
    (assoc-in q [:jobs id] (failed-job (get-in q [:jobs id]) now))
    q))

(defn expire
  "Every :leased job whose lease is no longer live fails, as fail says,
  at now."
  [q now]
  (reduce-kv
   (fn [acc id job]
     (if (and (= :leased (:status job))
              (not (live? (:lease job) now)))
       (assoc-in acc [:jobs id] (failed-job job now))
       acc))
   q
   (:jobs q)))
