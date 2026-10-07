(ns queue.core
  "Jobs with priorities, leases and retries.  Time is a Nat tick the
  caller passes; a lease is live while `now < :until`.")

(defn- due? [j now]
  (and (= :ready (:status j)) (<= (:ready-at j) now)))

(defn next-job [q now]
  (let [cands (filter (fn [j] (due? j now)) (vals (:jobs q)))]
    (when (seq cands)
      (:id (first (sort-by (fn [j] [(- (:priority j)) (:ready-at j) (:id j)]) cands))))))

(defn enqueue [q priority now]
  (let [id (:next-id q)
        job {:id id
             :priority priority
             :attempts 0
             :status :ready
             :ready-at now
             :lease nil}]
    [(assoc q :jobs (assoc (:jobs q) id job) :next-id (inc id)) id]))

(defn- backoff [n]
  (case n 0 1 1 2 2 4 0))

(defn- reaped [j now]
  (if (>= (:attempts j) 3)
    (assoc j :status :dead :lease nil)
    (assoc j :status :ready :ready-at (+ now (backoff (:attempts j))) :lease nil)))

(defn lease [q worker now]
  (if-let [id (next-job q now)]
    (let [j (get (:jobs q) id)]
      [(assoc q :jobs
              (assoc (:jobs q) id
                     (assoc j :status :leased
                            :attempts (inc (:attempts j))
                            :lease {:worker worker :until (+ now 10)})))
       id])
    [q nil]))

(defn complete [q id worker now]
  (let [j (get (:jobs q) id)]
    (if (and (some? j)
             (= :leased (:status j))
             (= worker (:worker (:lease j)))
             (< now (:until (:lease j))))
      (assoc q :jobs (assoc (:jobs q) id (assoc j :status :done :lease nil)))
      q)))

(defn fail [q id worker now]
  (let [j (get (:jobs q) id)]
    (if (and (some? j)
             (= :leased (:status j))
             (= worker (:worker (:lease j)))
             (< now (:until (:lease j))))
      (assoc q :jobs (assoc (:jobs q) id (reaped j now)))
      q)))

(defn expire [q now]
  (assoc q :jobs
         (into {}
               (for [[k j] (:jobs q)]
                 [k (if (and (= :leased (:status j)) (<= (:until (:lease j)) now))
                      (reaped j now)
                      j)]))))
