(ns dispatch.core-spec
  "The contract for dispatch.core: a tick of the clock hands due jobs to
  workers through the queue.

  A tick first expires the queue at `now`, then walks the workers in order:
  a worker that already holds a live lease gets nothing, any other leases
  the next job if there is one.  The assignments come back in the order
  they were leased, so the same worker listed twice leases at most one job.
  The queue's own laws are taken from queue.core-spec and are not restated
  here."
  (:require [writ.spec :refer [spec ann law graph example calls]]))

(spec dispatch.core {:require :proved :uses [queue.core-spec]})

;; --- the queue, in this spec's own words -----------------------------------

(defn due? [j now] (and (= :ready (:status j)) (<= (:ready-at j) now)))

(defn live-lease? [q w now]
  (some (fn [j] (and (= :leased (:status j))
                     (= w (:worker (:lease j)))
                     (< now (:until (:lease j)))))
        (vals (:jobs q))))

(defn backoff [n] (case n 0 1 1 2 2 4 0))

(defn reaped [j now]
  (if (>= (:attempts j) 3)
    (assoc j :status :dead :lease nil)
    (assoc j :status :ready :ready-at (+ now (backoff (:attempts j))) :lease nil)))

(defn expired [j now]
  (if (and (= :leased (:status j)) (<= (:until (:lease j)) now))
    (reaped j now)
    j))

(defn outranks? [a b]
  (or (> (:priority a) (:priority b))
      (and (= (:priority a) (:priority b))
           (or (< (:ready-at a) (:ready-at b))
               (and (= (:ready-at a) (:ready-at b)) (< (:id a) (:id b)))))))

(defn pick [q now]
  (let [cands (filter (fn [j] (due? j now)) (vals (:jobs q)))]
    (when (seq cands)
      (:id (first (sort-by (fn [j] [(- (:priority j)) (:ready-at j) (:id j)]) cands))))))

(defn lease-one [q w now]
  (if-let [id (pick q now)]
    (let [j (get (:jobs q) id)]
      [(assoc q :jobs
              (assoc (:jobs q) id
                     (assoc j :status :leased
                            :attempts (inc (:attempts j))
                            :lease {:worker w :until (+ now 10)})))
       id])
    [q nil]))

(defn assign [q workers now]
  (reduce (fn [[q as] w]
            (if (live-lease? q w now)
              [q as]
              (let [[q2 id] (lease-one q w now)]
                [q2 (if (some? id) (conj as [w id]) as)])))
          [q []]
          workers))

(defn model-tick
  "The tick, said here: expire, then lease in worker order, skipping any
  worker who already holds a live lease."
  [q workers now]
  (assign (assoc q :jobs (into {} (for [[k j] (:jobs q)] [k (expired j now)])))
          workers now))

;; --- signatures ------------------------------------------------------------

(ann tick    [Queue (Vec Nat) Nat -> (Tuple Queue (Vec (Tuple Nat Nat)))])
(ann backlog [Queue Nat -> Nat])

;; --- the state graph -------------------------------------------------------

(graph dispatch
  {:start  [:q {:jobs {} :next-id 0}]
   :states {:q Queue
            :ticked (Tuple Queue (Vec (Tuple Nat Nat)))
            :n Nat}
   :edges  {:q {[tick (Vec Nat) Nat] #{:ticked}
                [backlog Nat] #{:n}}
            :ticked {[first] #{:q}}}
   :tested {[:q tick] "the ticked queue and assignments are built through the component, which is left folded"}
   :final  [:n]})

;; --- how it is put together ------------------------------------------------

(calls tick [queue.core/expire queue.core/lease])
(calls backlog [])

;; --- what each step means --------------------------------------------------

(law a-tick-expires-first-and-then-hands-out-jobs
  {:require :tested
   :because "it equates tick with a spec model folded by reduce over the workers, which the prover does not unfold"}
  (forall [q Queue, workers (Vec Nat), now Nat]
    (= (tick q workers now) (model-tick q workers now))))

(law backlog-counts-the-due-ready-jobs
  (forall [q Queue, now Nat]
    (= (backlog q now)
       (count (filter (fn [j] (due? j now)) (vals (:jobs q)))))))

(law every-assignment-is-a-lease-to-that-worker
  {:require :tested
   :because "the assignment vector is built by a reduce the prover leaves opaque"}
  (forall [q Queue, workers (Vec Nat), now Nat]
    (let [[q' as] (tick q workers now)]
      (every? (fn [[w id]]
                (let [j (get (:jobs q') id)]
                  (and (= :leased (:status j))
                       (= w (:worker (:lease j)))
                       (= (+ now 10) (:until (:lease j))))))
              as))))

(law one-assignment-per-worker
  {:require :tested
   :because "the assignment vector is built by a reduce the prover leaves opaque"}
  (forall [q Queue, workers (Vec Nat), now Nat]
    (let [[_ as] (tick q workers now)]
      (= (count as) (count (distinct (map first as)))))))

(law a-worker-holding-a-live-lease-gets-nothing
  {:require :tested
   :because "the result is read through a reduce over the workers the prover leaves opaque"}
  (forall [q Queue, w Nat, workers (Vec Nat), now Nat]
    (=> (live-lease? q w now)
        (let [[_ as] (tick q workers now)]
          (not (some (fn [[x _]] (= x w)) as))))))

;; --- the boundaries, named -------------------------------------------------

(example backlog
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 7 :lease nil}}
    :next-id 1}
   7]
  1)

(example backlog
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 8 :lease nil}}
    :next-id 1}
   7]
  0)

(example tick
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 0 :lease nil}
           1 {:id 1 :priority 9 :attempts 0 :status :ready :ready-at 0 :lease nil}}
    :next-id 2}
   [3 4] 0]
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 4 :until 10}}
           1 {:id 1 :priority 9 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 3 :until 10}}}
    :next-id 2}
   [[3 1] [4 0]]])

(example tick
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 0 :lease nil}}
    :next-id 1}
   [3 3 4] 0]
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 3 :until 10}}}
    :next-id 1}
   [[3 0]]])

(example tick
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 3 :until 5}}
           1 {:id 1 :priority 5 :attempts 0 :status :ready :ready-at 0 :lease nil}}
    :next-id 2}
   [3] 5]
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :ready :ready-at 7 :lease nil}
           1 {:id 1 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 3 :until 15}}}
    :next-id 2}
   [[3 1]]])

(example tick
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 3 :until 6}}
           1 {:id 1 :priority 5 :attempts 0 :status :ready :ready-at 0 :lease nil}}
    :next-id 2}
   [3 4] 5]
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 3 :until 6}}
           1 {:id 1 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 4 :until 15}}}
    :next-id 2}
   [[4 1]]])
