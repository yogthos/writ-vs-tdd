(ns queue.core-test
  (:require [clojure.test :refer [deftest is testing are]]
            [queue.core :as qc]))

;; ---------------------------------------------------------------------------
;; helpers
;;
;; A job is {:id :priority :attempts :status :ready-at :lease} and a queue is
;; {:jobs {id job} :next-id Nat}.  Tests build these literally so they pin the
;; data shapes from TASK.md rather than any constructor the implementation
;; might offer.
;; ---------------------------------------------------------------------------

(defn job
  "A job map; lease is nil or {:worker n :until t}."
  [id priority attempts status ready-at lease]
  {:id id :priority priority :attempts attempts :status status
   :ready-at ready-at :lease lease})

(defn make-q
  "A queue containing the given jobs, keyed by their :id."
  [next-id & jobs]
  {:jobs (reduce (fn [m jb] (assoc m (:id jb) jb)) {} jobs)
   :next-id next-id})

(def empty-queue {:jobs {} :next-id 0})

;; ---------------------------------------------------------------------------
;; enqueue
;; ---------------------------------------------------------------------------

(deftest enqueue-creates-a-ready-job
  (testing "id comes from :next-id; job is ready at now with no attempts/lease"
    (let [[q' id] (qc/enqueue empty-queue 7 3)]
      (is (= 0 id))
      (is (= 1 (:next-id q')))
      (is (= {0 (job 0 7 0 :ready 3 nil)} (:jobs q')))
      (is (= {:jobs {0 (job 0 7 0 :ready 3 nil)} :next-id 1} q')))))

(deftest enqueue-increments-next-id
  (testing "second job takes the next id and does not disturb the first"
    (let [[q1 id1] (qc/enqueue empty-queue 1 0)
          [q2 id2] (qc/enqueue q1 2 5)]
      (is (= 0 id1))
      (is (= 1 id2))
      (is (= 2 (:next-id q2)))
      (is (= {0 (job 0 1 0 :ready 0 nil)
              1 (job 1 2 0 :ready 5 nil)}
             (:jobs q2))))))

(deftest enqueue-uses-next-id-not-job-count
  (testing ":next-id is authoritative even when jobs have been removed/gaps"
    (let [[q' id] (qc/enqueue {:jobs {} :next-id 4} 1 9)]
      (is (= 4 id))
      (is (= 5 (:next-id q')))
      (is (= 9 (get-in q' [:jobs 4 :ready-at]))))))

(deftest enqueue-does-not-mutate-input
  (let [before empty-queue
        _ (qc/enqueue before 1 0)]
    (is (= {:jobs {} :next-id 0} before))))

;; ---------------------------------------------------------------------------
;; next-job
;; ---------------------------------------------------------------------------

(deftest next-job-nil-when-empty
  (is (nil? (qc/next-job (make-q 0) 0))))

(deftest next-job-skips-future-ready-at
  (let [qq (make-q 0 (job 0 1 0 :ready 5 nil))]
    (is (nil? (qc/next-job qq 4)))
    (testing "ready-at at most now, so equality is due"
      (is (= 0 (qc/next-job qq 5))))))

(deftest next-job-only-considers-ready
  (let [qq (make-q 0 (job 0 9 0 :leased 0 {:worker 1 :until 100})
                     (job 1 9 0 :done 0 nil)
                     (job 2 9 0 :dead 0 nil))]
    (is (nil? (qc/next-job qq 50)))))

(deftest next-job-ignores-non-ready-even-if-high-priority
  (let [qq (make-q 0 (job 0 100 0 :leased 0 {:worker 1 :until 50})
                     (job 1 1 0 :ready 0 nil))]
    (is (= 1 (qc/next-job qq 0)))))

(deftest next-job-highest-priority-wins
  (let [qq (make-q 0 (job 0 1 0 :ready 0 nil)
                     (job 1 9 0 :ready 0 nil)
                     (job 2 5 0 :ready 0 nil))]
    (is (= 1 (qc/next-job qq 0)))))

(deftest next-job-priority-beats-ready-at
  (testing "a higher priority job with a later ready-at still wins"
    (let [qq (make-q 0 (job 0 1 0 :ready 0 nil)
                       (job 1 2 0 :ready 10 nil))]
      (is (= 1 (qc/next-job qq 10))))))

(deftest next-job-earliest-ready-at-on-priority-tie
  (let [qq (make-q 0 (job 7 3 0 :ready 5 nil)
                     (job 2 3 0 :ready 2 nil)
                     (job 9 3 0 :ready 8 nil))]
    (is (= 2 (qc/next-job qq 10)))))

(deftest next-job-ready-at-beats-id
  (testing "ready-at is the second tie-break, taking precedence over id"
    (let [qq (make-q 0 (job 1 3 0 :ready 9 nil)
                       (job 2 3 0 :ready 1 nil))]
      (is (= 2 (qc/next-job qq 10))))))

(deftest next-job-lowest-id-on-full-tie
  (testing "equal priority and equal ready-at, so lowest id wins"
    (let [qq (make-q 0 (job 5 3 0 :ready 2 nil)
                       (job 2 3 0 :ready 2 nil)
                       (job 9 3 0 :ready 2 nil))]
      (is (= 2 (qc/next-job qq 10))))))

(deftest next-job-returns-id-only
  (let [qq (make-q 0 (job 42 1 0 :ready 0 nil))]
    (is (= 42 (qc/next-job qq 0)))
    (is (integer? (qc/next-job qq 0)))))

;; ---------------------------------------------------------------------------
;; lease
;; ---------------------------------------------------------------------------

(deftest lease-empty-queue-is-unchanged
  (let [qq (make-q 0)
        [q' id] (qc/lease qq 1 0)]
    (is (nil? id))
    (is (= qq q'))))

(deftest lease-takes-next-job
  (let [qq (make-q 0 (job 0 1 0 :ready 3 nil)
                     (job 1 5 0 :ready 3 nil))
        [q' id] (qc/lease qq 7 10)]
    (is (= 1 id))
    (testing "leased with this worker, until now+10, attempts incremented"
      (is (= (job 1 5 1 :leased 3 {:worker 7 :until 20}) (get-in q' [:jobs 1]))))
    (testing "other jobs untouched; queue bookkeeping untouched"
      (is (= (job 0 1 0 :ready 3 nil) (get-in q' [:jobs 0])))
      (is (= 0 (:next-id q'))))))

(deftest lease-picks-by-the-same-next-job-rules
  (let [qq (make-q 0 (job 0 1 0 :ready 0 nil)
                     (job 1 9 0 :ready 0 nil))
        [q' id] (qc/lease qq 1 0)]
    (is (= 1 id))))

(deftest lease-increments-attempts-and-keeps-ready-at
  (testing "a job already attempted twice goes to three, ready-at unchanged"
    (let [qq (make-q 0 (job 0 1 2 :ready 4 nil))
          [q' id] (qc/lease qq 3 10)]
      (is (= 0 id))
      (is (= 3 (get-in q' [:jobs 0 :attempts])))
      (is (= 4 (get-in q' [:jobs 0 :ready-at])))
      (is (= :leased (get-in q' [:jobs 0 :status])))
      (is (= {:worker 3 :until 20} (get-in q' [:jobs 0 :lease]))))))

(deftest lease-nil-when-nothing-due
  (let [qq (make-q 0 (job 0 1 0 :ready 100 nil))
        [q' id] (qc/lease qq 1 0)]
    (is (nil? id))
    (is (= qq q'))))

(deftest lease-nil-when-all-taken
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 1 :until 100}))]
    (let [[q' id] (qc/lease qq 2 5)]
      (is (nil? id))
      (is (= qq q')))))

;; ---------------------------------------------------------------------------
;; complete
;; ---------------------------------------------------------------------------

(deftest complete-live-lease-marks-done
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 20}))
        q' (qc/complete qq 0 4 5)]
    (is (= (job 0 1 1 :done 0 nil) (get-in q' [:jobs 0])))
    (testing "a done job is no longer offered"
      (is (nil? (qc/next-job q' 5))))))

(deftest complete-frame-boundary-not-live
  (testing "a lease is live only while now < until; now == until does nothing"
    (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 20}))]
      (is (= qq (qc/complete qq 0 4 20)))
      (is (= qq (qc/complete qq 0 4 21))))))

(deftest complete-wrong-worker-does-nothing
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 20}))]
    (is (= qq (qc/complete qq 0 9 5)))))

(deftest complete-non-leased-does-nothing
  (doseq [status [:ready :done :dead]]
    (let [qq (make-q 0 (job 0 1 1 status 0 nil))]
      (is (= qq (qc/complete qq 0 1 5))))))

(deftest complete-unknown-id-does-nothing
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 20}))]
    (is (= qq (qc/complete qq 99 4 5)))))

(deftest complete-leaves-other-jobs-alone
  (let [other (job 1 2 3 :ready 0 nil)
        qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 20}) other)
        q' (qc/complete qq 0 4 5)]
    (is (= other (get-in q' [:jobs 1])))
    (is (= :done (get-in q' [:jobs 0 :status])))))

;; ---------------------------------------------------------------------------
;; fail
;; ---------------------------------------------------------------------------

(deftest fail-first-attempt-back-to-ready
  (testing "attempts 1 -> ready at now + 2^1"
    (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 20}))]
      (is (= (job 0 1 1 :ready 7 nil) (get-in (qc/fail qq 0 4 5) [:jobs 0]))))))

(deftest fail-second-attempt-backoff-four
  (testing "attempts 2 -> ready at now + 2^2"
    (let [qq (make-q 0 (job 0 1 2 :leased 0 {:worker 4 :until 20}))]
      (is (= (job 0 1 2 :ready 9 nil) (get-in (qc/fail qq 0 4 5) [:jobs 0]))))))

(deftest fail-third-attempt-dies
  (testing "3 or more attempts -> dead, lease dropped, ready-at kept"
    (let [qq (make-q 0 (job 0 1 3 :leased 0 {:worker 4 :until 20}))]
      (is (= (job 0 1 3 :dead 0 nil) (get-in (qc/fail qq 0 4 5) [:jobs 0]))))))

(deftest fail-many-attempts-stays-dead
  (let [qq (make-q 0 (job 0 1 5 :leased 0 {:worker 4 :until 20}))
        q' (qc/fail qq 0 4 5)]
    (is (= :dead (get-in q' [:jobs 0 :status])))
    (is (nil? (get-in q' [:jobs 0 :lease])))))

(deftest fail-boundary-and-wrong-worker-do-nothing
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 20}))]
    (is (= qq (qc/fail qq 0 4 20)))
    (is (= qq (qc/fail qq 0 4 21)))
    (is (= qq (qc/fail qq 0 9 5)))))

(deftest fail-non-leased-does-nothing
  (doseq [status [:ready :done :dead]]
    (let [qq (make-q 0 (job 0 1 1 status 0 nil))]
      (is (= qq (qc/fail qq 0 1 5))))))

(deftest fail-unknown-id-does-nothing
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 20}))]
    (is (= qq (qc/fail qq 42 4 5)))))

(deftest fail-leaves-other-jobs-alone
  (let [other (job 1 2 3 :ready 0 nil)
        qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 20}) other)
        q' (qc/fail qq 0 4 5)]
    (is (= other (get-in q' [:jobs 1])))))

;; ---------------------------------------------------------------------------
;; expire
;; ---------------------------------------------------------------------------

(deftest expire-fails-a-stale-lease
  (testing "attempts 1 -> ready at now + 2"
    (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 10}))]
      (is (= (job 0 1 1 :ready 12 nil) (get-in (qc/expire qq 10) [:jobs 0]))))))

(deftest expire-boundary
  (testing "live at now < until, dead-at-now exactly when now == until"
    (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 10}))]
      (is (= qq (qc/expire qq 9)))
      (is (= :ready (get-in (qc/expire qq 10) [:jobs 0 :status]))))))

(deftest expire-live-lease-untouched
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 11}))]
    (is (= qq (qc/expire qq 10)))))

(deftest expire-second-attempt-backoff-four
  (let [qq (make-q 0 (job 0 1 2 :leased 0 {:worker 4 :until 3}))]
    (is (= (job 0 1 2 :ready 12 nil) (get-in (qc/expire qq 8) [:jobs 0])))))

(deftest expire-third-attempt-dies
  (let [qq (make-q 0 (job 0 1 3 :leased 0 {:worker 4 :until 3}))]
    (is (= (job 0 1 3 :dead 0 nil) (get-in (qc/expire qq 8) [:jobs 0])))))

(deftest expire-ignores-non-leased
  (let [qq (make-q 0 (job 0 1 1 :ready 0 nil)
                     (job 1 1 1 :done 0 nil)
                     (job 2 1 1 :dead 0 nil))]
    (is (= qq (qc/expire qq 100)))))

(deftest expire-handles-every-stale-lease
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 1 :until 5})
                     (job 1 2 2 :leased 0 {:worker 2 :until 1})
                     (job 2 3 0 :ready 0 nil))
        q' (qc/expire qq 10)]
    (is (= (job 0 1 1 :ready 12 nil) (get-in q' [:jobs 0])))
    (is (= (job 1 2 2 :ready 14 nil) (get-in q' [:jobs 1])))
    (is (= (job 2 3 0 :ready 0 nil) (get-in q' [:jobs 2])))
    (is (= 0 (:next-id q')))))

(deftest expire-uses-the-given-now
  (let [qq (make-q 0 (job 0 1 1 :leased 0 {:worker 4 :until 0}))]
    (is (= (job 0 1 1 :ready 2 nil) (get-in (qc/expire qq 0) [:jobs 0])))
    (is (= (job 0 1 1 :ready 100 nil) (get-in (qc/expire qq 98) [:jobs 0])))))
