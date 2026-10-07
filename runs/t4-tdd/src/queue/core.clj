(ns queue.core)

;; A queue is {:jobs {id job}, :next-id Nat} and a job is
;; {:id :priority :attempts :status :ready-at :lease}.  Time is a Nat tick
;; supplied by the caller; nothing here reads a clock.

(defn- live?
  "A lease is live while now < :until."
  [lease now]
  (and (some? lease) (< now (:until lease))))

(defn- due-ready?
  [job now]
  (and (= :ready (:status job))
       (<= (:ready-at job) now)))

(defn- next-candidate
  "The ready job a worker should take at now, or nil.  Highest priority, then
  earliest ready-at, then lowest id."
  [q now]
  (->> (vals (:jobs q))
       (filter #(due-ready? % now))
       (sort-by (juxt (comp - :priority) :ready-at :id))
       first))

(defn enqueue
  "Add a new ready job of the given priority, due at now.  Returns [q' id]."
  [q priority now]
  (let [id (:next-id q)
        job {:id id :priority priority :attempts 0 :status :ready
             :ready-at now :lease nil}]
    [(assoc-in (update q :next-id inc) [:jobs id] job) id]))

(defn next-job
  "The id of the job a worker should take next at now, or nil."
  [q now]
  (:id (next-candidate q now)))

(defn lease
  "Lease the next job to worker at now.  Returns [q' id], id nil if none."
  [q worker now]
  (if-let [job (next-candidate q now)]
    (let [id (:id job)
          job' (-> job
                   (assoc :status :leased)
                   (assoc :lease {:worker worker :until (+ now 10)})
                   (update :attempts inc))]
      [(assoc-in q [:jobs id] job') id])
    [q nil]))

(defn- owned-live-lease?
  "True when job id is leased, its lease belongs to worker, and is live at now."
  [q id worker now]
  (let [job (get-in q [:jobs id])]
    (and (some? job)
         (= :leased (:status job))
         (live? (:lease job) now)
         (= worker (:worker (:lease job))))))

(defn- fail-job
  "The job after a failure at now: dead at 3+ attempts, otherwise ready with
  the 2^attempts backoff.  Either way it loses its lease."
  [job now]
  (if (>= (:attempts job) 3)
    (assoc job :status :dead :lease nil)
    (assoc job :status :ready
           :ready-at (+ now (bit-shift-left 1 (:attempts job)))
           :lease nil)))

(defn complete
  "Mark a live leased job done; anything else is unchanged."
  [q id worker now]
  (if (owned-live-lease? q id worker now)
    (-> q
        (assoc-in [:jobs id :status] :done)
        (assoc-in [:jobs id :lease] nil))
    q))

(defn fail
  "Fail a live leased job; anything else is unchanged."
  [q id worker now]
  (if (owned-live-lease? q id worker now)
    (update-in q [:jobs id] fail-job now)
    q))

(defn expire
  "Fail every leased job whose lease is no longer live at now."
  [q now]
  (reduce (fn [q id]
            (let [job (get-in q [:jobs id])]
              (if (and (= :leased (:status job))
                       (not (live? (:lease job) now)))
                (update-in q [:jobs id] fail-job now)
                q)))
          q
          (keys (:jobs q))))
