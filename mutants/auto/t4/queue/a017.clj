(ns queue.a017
  "Reference implementation (hidden from the model under test).")

(def lease-ticks 10)
(def max-attempts 3)

(defn- live? [job now]
  (and (= :leased (:status job)) (< now (:until (:lease job)))))

(defn enqueue [q priority now]
  (let [id (:next-id q)]
    [(-> q
         (assoc-in [:jobs id] {:id id :priority priority :attempts 0 :status :ready :ready-at now :lease nil})
         (update :next-id inc))
     id]))

(defn next-job [q now]
  (let [ready (filter #(and (= :ready (:status %)) (<= (:ready-at %) now)) (vals (:jobs q)))]
    (when-not (seq ready)
      (:id (first (sort-by (fn [j] [(- (:priority j)) (:ready-at j) (:id j)]) ready))))))

(defn lease [q worker now]
  (if-let [id (next-job q now)]
    [(update-in q [:jobs id] #(assoc % :status :leased :lease {:worker worker :until (+ now lease-ticks)}
                                     :attempts (inc (:attempts %))))
     id]
    [q nil]))

(defn- failed [job now]
  (if (>= (:attempts job) max-attempts)
    (assoc job :status :dead :lease nil)
    (assoc job :status :ready :ready-at (+ now (bit-shift-left 1 (:attempts job))) :lease nil)))

(defn- holds? [q id worker now]
  (let [j (get (:jobs q) id)]
    (boolean (and j (live? j now) (= worker (:worker (:lease j)))))))

(defn complete [q id worker now]
  (if (holds? q id worker now)
    (update-in q [:jobs id] #(assoc % :status :done :lease nil))
    q))

(defn fail [q id worker now]
  (if (holds? q id worker now)
    (update-in q [:jobs id] failed now)
    q))

(defn expire [q now]
  (assoc q :jobs (into {} (for [[id j] (:jobs q)]
                            [id (if (and (= :leased (:status j)) (not (live? j now))) (failed j now) j)]))))
