(ns queue.core-spec
  "The contract for queue.core: jobs with priorities, leases and retries.

  Time is a Nat tick the caller passes; nothing reads a clock.  A lease is
  live while `now < :until`.  The job taken next is the due `:ready` job of
  the highest priority, then the earliest `:ready-at`, then the lowest id.
  A job that fails while leased comes back `2^attempts` ticks later, and a
  job of 3 or more attempts dies instead."
  (:require [writ.spec :refer [spec ann law graph refine example]]))

(spec queue.core {:require :proved})

;; --- the data --------------------------------------------------------------

(defn job-shape?
  "Leases go with `:leased`, and only with `:leased`."
  [j]
  (case (:status j)
    :leased (some? (:lease j))
    (:ready :done :dead) (nil? (:lease j))
    false))

(defn settled-job [j]
  (cond
    (= :leased (:status j)) (assoc j :lease (if (map? (:lease j)) (:lease j) {:worker 0 :until 0}))
    (contains? #{:ready :done :dead} (:status j)) (assoc j :lease nil)
    :else (assoc j :status :ready :lease nil)))

(refine Job [j {:id Nat, :priority Nat, :attempts Nat, :status Keyword,
                :ready-at Nat, :lease (Opt {:worker Nat, :until Nat})}]
  (job-shape? j)
  {:build settled-job})

(defn keyed?
  "Every job sits under its own id, below the next id to hand out."
  [q]
  (every? (fn [k] (< k (:next-id q))) (keys (:jobs q))))

(defn settled [q]
  (let [jobs (into {} (map-indexed (fn [i j] [i (assoc j :id i)]) (vals (:jobs q))))]
    {:jobs jobs :next-id (+ (count jobs) (:next-id q))}))

(refine Queue [q {:jobs (Index :id Job), :next-id Nat}]
  (keyed? q)
  {:build settled})

;; --- the order -------------------------------------------------------------

(defn due? [j now] (and (= :ready (:status j)) (<= (:ready-at j) now)))

(defn outranks? [a b]
  (or (> (:priority a) (:priority b))
      (and (= (:priority a) (:priority b))
           (or (< (:ready-at a) (:ready-at b))
               (and (= (:ready-at a) (:ready-at b)) (< (:id a) (:id b)))))))

(defn live? [j now] (and (= :leased (:status j)) (< now (:until (:lease j)))))

(defn backoff [n] (case n 0 1 1 2 2 4 0))

(defn reaped
  "The job after it fails: dead at 3 attempts, else ready again, 2^attempts
  ticks on, in either case with no lease."
  [j now]
  (if (>= (:attempts j) 3)
    (assoc j :status :dead :lease nil)
    (assoc j :status :ready :ready-at (+ now (backoff (:attempts j))) :lease nil)))

(defn may-act?
  "The job is leased, to this worker, on a live lease."
  [j worker now]
  (and (some? j) (= :leased (:status j)) (= worker (:worker (:lease j)))
       (< now (:until (:lease j)))))

;; --- signatures ------------------------------------------------------------

(ann enqueue  [Queue Nat Nat -> (Tuple Queue Nat)])
(ann next-job [Queue Nat -> (Opt Nat)])
(ann lease    [Queue Nat Nat -> (Tuple Queue (Opt Nat))])
(ann complete [Queue Nat Nat Nat -> Queue])
(ann fail     [Queue Nat Nat Nat -> Queue])
(ann expire   [Queue Nat -> Queue])

;; --- the state graph -------------------------------------------------------

(graph queue
  {:start  [:q {:jobs {} :next-id 0}]
   :states {:q Queue
            :enq (Tuple Queue Nat)
            :lea (Tuple Queue (Opt Nat))
            :id (Opt Nat)}
   :edges  {:q {[enqueue Nat Nat] #{:enq}
                [next-job Nat] #{:id}
                [lease Nat Nat] #{:lea}
                [complete Nat Nat Nat] #{:q}
                [fail Nat Nat Nat] #{:q}
                [expire Nat] #{:q}}
            :enq {[first] #{:q}}
            :lea {[first] #{:q}}}
   :tested {[:q lease] "the leased queue is built by a sort-by the prover leaves opaque"
            [:q expire] "the reaped jobs map is built by for/into, beyond the prover's model"}
   :final  [:id]})

;; --- what each step means --------------------------------------------------

(law enqueue-adds-the-next-job
  (forall [q Queue, priority Nat, now Nat]
    (let [[q' id] (enqueue q priority now)
          j (get (:jobs q') id)]
      (and (= id (:next-id q))
           (= (inc (:next-id q)) (:next-id q'))
           (= (:jobs q) (dissoc (:jobs q') id))
           (= priority (:priority j))
           (= 0 (:attempts j))
           (= :ready (:status j))
           (= now (:ready-at j))
           (= nil (:lease j))))))

(law next-job-is-nil-exactly-when-nothing-is-due
  {:require :tested
   :because "it reads the code's filter over the map's values, a sequence the prover leaves opaque"}
  (forall [q Queue, now Nat]
    (= (nil? (next-job q now))
       (not (some (fn [j] (due? j now)) (vals (:jobs q)))))))

(law the-next-job-is-due
  {:require :tested
   :because "the pick is a sort-by, which the prover does not interpret"}
  (forall [q Queue, now Nat]
    (let [id (next-job q now)]
      (=> (some? id) (due? (get (:jobs q) id) now)))))

(law nothing-due-outranks-the-next-job
  {:require :tested
   :because "the ranking is a sort-by, which the prover does not interpret"}
  (forall [q Queue, now Nat]
    (let [id (next-job q now)]
      (=> (some? id)
          (every? (fn [k] (=> (due? (get (:jobs q) k) now)
                              (not (outranks? (get (:jobs q) k) (get (:jobs q) id)))))
                  (keys (:jobs q)))))))

(defn leased
  "What job j becomes when leased to worker until now + 10."
  [j worker now]
  (assoc j :status :leased
         :attempts (inc (:attempts j))
         :lease {:worker worker :until (+ now 10)}))

(law lease-takes-the-next-job
  {:require :tested
   :because "it compares the result with a whole queue the code builds, behind a sort-by the prover leaves opaque"}
  (forall [q Queue, worker Nat, now Nat]
    (let [[q' id] (lease q worker now)
          want (next-job q now)]
      (and (= want id)
           (if (nil? want)
             (= q' q)
             (= q' (assoc q :jobs
                          (assoc (:jobs q) want
                                 (leased (get (:jobs q) want) worker now)))))))))

(law complete-finishes-a-live-lease
  (forall [q Queue, id Nat, worker Nat, now Nat]
    (let [j (get (:jobs q) id)]
      (= (complete q id worker now)
         (if (may-act? j worker now)
           (assoc q :jobs (assoc (:jobs q) id (assoc j :status :done :lease nil)))
           q)))))

(law fail-retries-and-then-kills
  (forall [q Queue, id Nat, worker Nat, now Nat]
    (let [j (get (:jobs q) id)]
      (= (fail q id worker now)
         (if (may-act? j worker now)
           (assoc q :jobs (assoc (:jobs q) id (reaped j now)))
           q)))))

(law expire-reaps-any-lease-that-has-run-out
  {:require :tested
   :because "its model builds a new jobs map with for/into, beyond the prover's model"}
  (forall [q Queue, now Nat]
    (= (expire q now)
       (assoc q :jobs
              (into {} (for [[k j] (:jobs q)]
                         [k (if (and (= :leased (:status j))
                                     (<= (:until (:lease j)) now))
                              (reaped j now)
                              j)]))))))

;; --- the boundaries and ties, named ----------------------------------------

(example enqueue
  [{:jobs {} :next-id 0} 7 3]
  [{:jobs {0 {:id 0 :priority 7 :attempts 0 :status :ready :ready-at 3 :lease nil}}
    :next-id 1}
   0])

(example next-job
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 1 :lease nil}
           1 {:id 1 :priority 5 :attempts 0 :status :ready :ready-at 1 :lease nil}
           2 {:id 2 :priority 9 :attempts 0 :status :ready :ready-at 4 :lease nil}}
    :next-id 3}
   5]
  2)

(example next-job
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 3 :lease nil}
           1 {:id 1 :priority 5 :attempts 0 :status :ready :ready-at 1 :lease nil}}
    :next-id 2}
   5]
  1)

(example next-job
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 0 :lease nil}
           1 {:id 1 :priority 5 :attempts 0 :status :ready :ready-at 0 :lease nil}}
    :next-id 2}
   0]
  0)

(example next-job
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 7 :lease nil}}
    :next-id 1}
   7]
  0)

(example next-job
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 8 :lease nil}}
    :next-id 1}
   7]
  nil)

(example next-job
  [{:jobs {0 {:id 0 :priority 99 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 2 :until 100}}
           1 {:id 1 :priority 1 :attempts 0 :status :ready :ready-at 0 :lease nil}}
    :next-id 2}
   5]
  1)

(example next-job
  [{:jobs {0 {:id 0 :priority 99 :attempts 1 :status :done :ready-at 0 :lease nil}
           1 {:id 1 :priority 1 :attempts 0 :status :dead :ready-at 0 :lease nil}}
    :next-id 2}
   5]
  nil)

(example lease
  [{:jobs {0 {:id 0 :priority 5 :attempts 0 :status :ready :ready-at 0 :lease nil}}
    :next-id 1}
   9 4]
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 9 :until 14}}}
    :next-id 1}
   0])

(example lease
  [{:jobs {} :next-id 0} 9 4]
  [{:jobs {} :next-id 0} nil])

(example complete
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 7 :until 5}}}
    :next-id 1}
   0 7 4]
  {:jobs {0 {:id 0 :priority 5 :attempts 1 :status :done :ready-at 0 :lease nil}}
   :next-id 1})

(example complete
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 7 :until 5}}}
    :next-id 1}
   0 7 5]
  {:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
             :lease {:worker 7 :until 5}}}
   :next-id 1})

(example complete
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 8 :until 5}}}
    :next-id 1}
   0 7 4]
  {:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
             :lease {:worker 8 :until 5}}}
   :next-id 1})

(example fail
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 7 :until 100}}}
    :next-id 1}
   0 7 10]
  {:jobs {0 {:id 0 :priority 5 :attempts 1 :status :ready :ready-at 12 :lease nil}}
   :next-id 1})

(example fail
  [{:jobs {0 {:id 0 :priority 5 :attempts 2 :status :leased :ready-at 0
              :lease {:worker 7 :until 100}}}
    :next-id 1}
   0 7 10]
  {:jobs {0 {:id 0 :priority 5 :attempts 2 :status :ready :ready-at 14 :lease nil}}
   :next-id 1})

(example fail
  [{:jobs {0 {:id 0 :priority 5 :attempts 3 :status :leased :ready-at 0
              :lease {:worker 7 :until 100}}}
    :next-id 1}
   0 7 10]
  {:jobs {0 {:id 0 :priority 5 :attempts 3 :status :dead :ready-at 0 :lease nil}}
   :next-id 1})

(example expire
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 7 :until 5}}}
    :next-id 1}
   5]
  {:jobs {0 {:id 0 :priority 5 :attempts 1 :status :ready :ready-at 7 :lease nil}}
   :next-id 1})

(example expire
  [{:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
              :lease {:worker 7 :until 6}}}
    :next-id 1}
   5]
  {:jobs {0 {:id 0 :priority 5 :attempts 1 :status :leased :ready-at 0
             :lease {:worker 7 :until 6}}}
   :next-id 1})
