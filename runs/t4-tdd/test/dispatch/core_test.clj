(ns dispatch.core-test
  (:require [clojure.test :refer [deftest is testing are]]
            [queue.core :as qc]
            [dispatch.core :as d]))

;; ---------------------------------------------------------------------------
;; helpers (same literal shapes as queue.core)
;; ---------------------------------------------------------------------------

(defn job
  [id priority attempts status ready-at lease]
  {:id id :priority priority :attempts attempts :status status
   :ready-at ready-at :lease lease})

(defn make-q
  [next-id & jobs]
  {:jobs (reduce (fn [m jb] (assoc m (:id jb) jb)) {} jobs)
   :next-id next-id})

(def empty-queue {:jobs {} :next-id 0})

;; ---------------------------------------------------------------------------
;; tick
;; ---------------------------------------------------------------------------

(deftest tick-empty-queue
  (let [[q' a] (d/tick (make-q 0) [] 5)]
    (is (= (make-q 0) q'))
    (is (= [] a))))

(deftest tick-leases-the-next-job
  (let [qq (make-q 0 (job 0 1 0 :ready 2 nil))
        [q' a] (d/tick qq [7] 5)]
    (is (= [[7 0]] a))
    (is (= (job 0 1 1 :leased 2 {:worker 7 :until 15}) (get-in q' [:jobs 0])))))

(deftest tick-processes-workers-in-order
  (testing "the first worker takes the best job, the next worker the next best"
    (let [qq (make-q 0 (job 0 1 0 :ready 0 nil)
                       (job 1 2 0 :ready 0 nil)
                       (job 2 3 0 :ready 0 nil))
          [q' a] (d/tick qq [10 20] 0)]
      (is (= [[10 2] [20 1]] a))
      (is (= 10 (get-in q' [:jobs 2 :lease :worker])))
      (is (= 20 (get-in q' [:jobs 1 :lease :worker])))
      (is (= :ready (get-in q' [:jobs 0 :status]))))))

(deftest tick-assignments-follow-lease-order
  (let [qq (make-q 0 (job 0 1 0 :ready 0 nil)
                     (job 1 2 0 :ready 0 nil))
        [q' a] (d/tick qq [5 6] 0)]
    (is (= [[5 1] [6 0]] a))))

(deftest tick-one-job-goes-to-the-first-worker
  (let [qq (make-q 0 (job 0 1 0 :ready 0 nil))
        [q' a] (d/tick qq [1 2] 0)]
    (is (= [[1 0]] a))
    (is (= 1 (get-in q' [:jobs 0 :lease :worker])))))

(deftest tick-skips-worker-holding-a-live-lease
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 1 :until 50})
                     (job 1 2 0 :ready 0 nil))
        [q' a] (d/tick qq [1] 5)]
    (is (= [] a))
    (is (= {:worker 1 :until 50} (get-in q' [:jobs 0 :lease])))
    (is (= :ready (get-in q' [:jobs 1 :status])))
    (testing "the live lease is not renewed"
      (is (= 1 (get-in q' [:jobs 0 :attempts]))))))

(deftest tick-others-lease-while-a-worker-holds-a-live-lease
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 1 :until 50})
                     (job 1 2 0 :ready 0 nil))
        [q' a] (d/tick qq [1 2] 5)]
    (is (= [[2 1]] a))
    (is (= :leased (get-in q' [:jobs 1 :status])))
    (is (= 2 (get-in q' [:jobs 1 :lease :worker])))))

(deftest tick-expires-before-leasing
  (testing "an expired lease is failed at now, and the backoff is not yet due"
    (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 1 :until 5}))
          [q' a] (d/tick qq [1 2] 5)]
      (is (= (job 0 1 1 :ready 7 nil) (get-in q' [:jobs 0])))
      (is (= [] a)))))

(deftest tick-expired-worker-may-lease-another-job
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 1 :until 5})
                     (job 1 2 0 :ready 0 nil))
        [q' a] (d/tick qq [1] 5)]
    (is (= [[1 1]] a))
    (is (= :ready (get-in q' [:jobs 0 :status])))
    (is (= 1 (get-in q' [:jobs 1 :lease :worker])))))

(deftest tick-duplicate-worker-leases-at-most-one
  (let [qq (make-q 0 (job 0 1 0 :ready 0 nil)
                     (job 1 2 0 :ready 0 nil))
        [q' a] (d/tick qq [1 1] 0)]
    (testing "the second occurrence now sees the live lease just taken"
      (is (= [[1 1]] a))
      (is (= :ready (get-in q' [:jobs 0 :status])))
      (is (= 1 (get-in q' [:jobs 1 :lease :worker]))))))

(deftest tick-no-workers
  (let [qq (make-q 0 (job 0 1 0 :ready 0 nil))
        [q' a] (d/tick qq [] 0)]
    (is (= qq q'))
    (is (= [] a))))

(deftest tick-skips-not-yet-ready-jobs
  (let [qq (make-q 0 (job 0 1 0 :ready 9 nil))
        [q' a] (d/tick qq [1] 5)]
    (is (= [] a))
    (is (= :ready (get-in q' [:jobs 0 :status])))))

(deftest tick-lease-window-and-attempts
  (let [qq (make-q 0 (job 0 1 2 :ready 0 nil))
        [q' a] (d/tick qq [3] 7)]
    (is (= [[3 0]] a))
    (is (= {:worker 3 :until 17} (get-in q' [:jobs 0 :lease])))
    (is (= 3 (get-in q' [:jobs 0 :attempts])))))

(deftest tick-does-not-renew-a-live-lease
  (let [qq (make-q 0 (job 0 1 5 :leased 0 {:worker 3 :until 40}))
        [q' a] (d/tick qq [3] 10)]
    (is (= {:worker 3 :until 40} (get-in q' [:jobs 0 :lease])))
    (is (= [] a))))

(deftest tick-expires-only-stale-leases-not-the-live-ones
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 1 :until 5})
                     (job 1 2 1 :leased 0 {:worker 2 :until 50})
                     (job 2 3 0 :ready 0 nil))
        [q' a] (d/tick qq [1 2 3] 10)]
    (testing "stale lease (job 0) freed and its backoff not due yet"
      (is (= (job 0 1 1 :ready 12 nil) (get-in q' [:jobs 0]))))
    (testing "worker 2 keeps its live lease and is skipped"
      (is (= {:worker 2 :until 50} (get-in q' [:jobs 1 :lease]))))
    (testing "worker 1 (freed) leases job 2; worker 3 finds nothing due"
      (is (= [[1 2]] a)))))

;; ---------------------------------------------------------------------------
;; backlog
;; ---------------------------------------------------------------------------

(deftest backlog-empty
  (is (= 0 (d/backlog (make-q 0) 0))))

(deftest backlog-counts-due-ready-only
  (let [qq (make-q 0 (job 0 1 0 :ready 0 nil)
                     (job 1 1 0 :ready 3 nil)
                     (job 2 1 0 :ready 9 nil)
                     (job 3 1 0 :leased 0 {:worker 1 :until 100})
                     (job 4 1 0 :done 0 nil)
                     (job 5 1 0 :dead 0 nil))]
    (is (= 2 (d/backlog qq 5)))))

(deftest backlog-is-inclusive-at-now
  (let [qq (make-q 0 (job 0 1 0 :ready 5 nil))]
    (is (= 0 (d/backlog qq 4)))
    (is (= 1 (d/backlog qq 5)))
    (is (= 1 (d/backlog qq 6)))))

(deftest backlog-ignores-priority
  (let [qq (make-q 0 (job 0 100 0 :ready 0 nil)
                     (job 1 1 0 :ready 0 nil))]
    (is (= 2 (d/backlog qq 0)))))

;; ---------------------------------------------------------------------------
;; integration across queue.core and dispatch.core
;; ---------------------------------------------------------------------------

(deftest full-happy-lifecycle
  (let [[qq id] (qc/enqueue empty-queue 5 0)]
    (is (= 0 id))
    (is (= 1 (d/backlog qq 0)))
    (let [[q1 a1] (d/tick qq [9] 0)]
      (is (= [[9 0]] a1))
      (is (= 0 (d/backlog q1 0)))
      (let [q2 (qc/complete q1 0 9 1)]
        (is (= :done (get-in q2 [:jobs 0 :status])))
        (is (= 0 (d/backlog q2 1)))))))

(deftest retry-until-dead-lifecycle
  (let [[q0 _] (qc/enqueue empty-queue 1 0)
        [q1 id1] (qc/lease q0 1 0)
        q2 (qc/fail q1 0 1 0)
        [q3 id3] (qc/lease q2 2 2)
        q4 (qc/fail q3 0 2 2)
        [q5 id5] (qc/lease q4 3 6)
        q6 (qc/fail q5 0 3 6)]
    (testing "first lease succeeds; first failure backs off 2"
      (is (= 0 id1))
      (is (= 1 (get-in q2 [:jobs 0 :attempts])))
      (is (= 2 (get-in q2 [:jobs 0 :ready-at])))
      (is (= 0 (d/backlog q2 1)))
      (is (= 1 (d/backlog q2 2))))
    (testing "second lease/fail backs off 4"
      (is (= 0 id3))
      (is (= 2 (get-in q4 [:jobs 0 :attempts])))
      (is (= 6 (get-in q4 [:jobs 0 :ready-at]))))
    (testing "third lease/fail dies and leaves the backlog"
      (is (= 0 id5))
      (is (= :dead (get-in q6 [:jobs 0 :status])))
      (is (nil? (get-in q6 [:jobs 0 :lease])))
      (is (= 0 (d/backlog q6 100))))))

(deftest tick-expiry-uses-tick-now-for-backoff
  (let [[q0 _] (qc/enqueue empty-queue 1 0)
        [q1 _] (qc/lease q0 1 0)
        [q2 a] (d/tick q1 [] 50)]
    (is (= [] a))
    (is (= :ready (get-in q2 [:jobs 0 :status])))
    (is (= 52 (get-in q2 [:jobs 0 :ready-at])))
    (is (nil? (get-in q2 [:jobs 0 :lease])))))

(deftest no-double-lease-across-ticks
  (let [[q0 _] (qc/enqueue empty-queue 1 0)
        [q1 a1] (d/tick q0 [1 2] 0)
        [q2 a2] (d/tick q1 [1 2] 1)]
    (is (= [[1 0]] a1))
    (testing "both workers already hold the lease, so nothing new"
      (is (= [] a2)))))
