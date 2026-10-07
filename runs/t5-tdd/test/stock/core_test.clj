(ns stock.core-test
  "Tests for stock.core: lots, usability, receiving, quarantine/release,
  taking and purging."
  (:require [clojure.test :refer [deftest testing is are]]
            [stock.core :as stock]))

;; --- helpers ---------------------------------------------------------------

(defn lot
  "A lot map, :active unless a status is given."
  ([id sku qty expires] (lot id sku qty expires :active))
  ([id sku qty expires status]
   {:id id :sku sku :qty qty :expires expires :status status}))

(defn stock-of
  "A stock map built from a collection of lots and a next id."
  [lots next-id]
  {:lots (into {} (map (fn [l] [(:id l) l]) lots))
   :next-id next-id})

;; --- receive ---------------------------------------------------------------

(deftest receive-adds-an-active-lot
  (testing "a positive quantity adds an :active lot under :next-id"
    (let [[s id] (stock/receive (stock-of [] 7) 3 5 100)]
      (is (= 7 id))
      (is (= (stock-of [(lot 7 3 5 100)] 8) s)))))

(deftest receive-ids-increment
  (let [[s0 id0] (stock/receive (stock-of [] 0) 1 2 10)
        [s1 id1] (stock/receive s0 1 3 20)
        [s2 id2] (stock/receive s1 2 4 30)]
    (is (= [0 1 2] [id0 id1 id2]))
    (is (= 3 (:next-id s2)))
    (is (= (lot 0 1 2 10) (get-in s2 [:lots 0])))
    (is (= (lot 1 1 3 20) (get-in s2 [:lots 1])))
    (is (= (lot 2 2 4 30) (get-in s2 [:lots 2])))))

(deftest receive-keeps-existing-lots
  (let [before (stock-of [(lot 0 1 4 10)] 1)
        [s id] (stock/receive before 2 6 20)]
    (is (= 1 id))
    (is (= (lot 0 1 4 10) (get-in s [:lots 0])))
    (is (= (lot 1 2 6 20) (get-in s [:lots 1])))))

(deftest receive-zero-quantity-is-a-no-op
  (testing "empty stock"
    (let [before (stock-of [] 4)
          [s id] (stock/receive before 1 0 9)]
      (is (nil? id))
      (is (= before s))))
  (testing "stock with lots"
    (let [before (stock-of [(lot 0 1 4 10)] 1)
          [s id] (stock/receive before 1 0 9)]
      (is (nil? id))
      (is (= before s)))))

;; --- usable / available ----------------------------------------------------

(deftest available-sums-usable-lots
  (let [s (stock-of [(lot 1 1 5 10)
                     (lot 2 1 3 20)
                     (lot 3 1 4 5)]
                    4)]
    (testing "all usable at now 0"
      (is (= 12 (stock/available s 1 0))))
    (testing "the lot expiring at 5 drops out at now 5"
      (is (= 8 (stock/available s 1 5))))
    (testing "a sku with no lots"
      (is (= 0 (stock/available s 9 0))))
    (testing "empty stock"
      (is (= 0 (stock/available (stock-of [] 0) 1 0))))))

(deftest available-excludes-unusable-lots
  (let [s (stock-of [(lot 1 1 5 10)                 ;; usable
                     (lot 2 1 6 10 :quarantined)     ;; quarantined
                     (lot 3 1 0 10)                  ;; empty
                     (lot 4 2 7 10)]
                    5)]
    (is (= 5 (stock/available s 1 0)))
    (is (= 7 (stock/available s 2 0)))))

(deftest available-expiry-boundary
  (let [s (stock-of [(lot 1 1 3 10)] 2)]
    (is (= 3 (stock/available s 1 9)) "now just before expiry")
    (is (= 0 (stock/available s 1 10)) "now at expiry")
    (is (= 0 (stock/available s 1 11)) "now past expiry")))

(deftest available-quantity-boundary
  (let [s (stock-of [(lot 1 1 1 10) (lot 2 1 0 10)] 3)]
    (is (= 1 (stock/available s 1 0)) "qty 1 is usable, qty 0 is not")))

;; --- quarantine / release --------------------------------------------------

(deftest quarantine-marks-a-lot
  (let [s (stock-of [(lot 1 1 5 10) (lot 2 1 4 10)] 3)
        q (stock/quarantine s 1)]
    (is (= :quarantined (get-in q [:lots 1 :status])))
    (is (= :active (get-in q [:lots 2 :status])) "other lots untouched")
    (is (= 4 (stock/available q 1 0)) "quarantined lot is unusable")))

(deftest quarantine-missing-lot-changes-nothing
  (let [s (stock-of [(lot 1 1 5 10)] 2)]
    (is (= s (stock/quarantine s 99)))))

(deftest quarantine-is-idempotent
  (let [s (stock-of [(lot 1 1 5 10)] 2)
        q (stock/quarantine s 1)]
    (is (= q (stock/quarantine q 1)))))

(deftest release-reactivates-a-quarantined-lot
  (let [s (stock-of [(lot 1 1 5 10) (lot 2 1 4 10)] 3)
        q (stock/quarantine s 1)
        r (stock/release q 1)]
    (is (= :active (get-in r [:lots 1 :status])))
    (is (= 9 (stock/available r 1 0)))))

(deftest release-of-active-or-missing-changes-nothing
  (let [s (stock-of [(lot 1 1 5 10)] 2)]
    (testing "an active lot stays active"
      (is (= s (stock/release s 1))))
    (testing "a missing lot changes nothing"
      (is (= s (stock/release s 99))))))

;; --- take-from -------------------------------------------------------------

(deftest take-from-partial-reduces-qty
  (let [s (stock-of [(lot 1 1 5 10)] 2)
        [s' ok] (stock/take-from s 1 2 0)]
    (is (true? ok))
    (is (= 3 (get-in s' [:lots 1 :qty])))
    (is (= 2 (:next-id s')) "next-id is untouched")
    (is (= 5 (get-in s [:lots 1 :qty])) "the original stock is unchanged")))

(deftest take-from-one-is-allowed
  (let [s (stock-of [(lot 1 1 5 10)] 2)
        [s' ok] (stock/take-from s 1 1 0)]
    (is (true? ok))
    (is (= 4 (get-in s' [:lots 1 :qty])))))

(deftest take-from-everything-removes-the-lot
  (let [s (stock-of [(lot 1 1 5 10) (lot 2 1 7 20)] 3)
        [s' ok] (stock/take-from s 1 5 0)]
    (is (true? ok))
    (is (not (contains? (:lots s') 1)) "a lot at qty 0 is removed")
    (is (= 7 (get-in s' [:lots 2 :qty])) "other lots untouched")
    (is (= 7 (stock/available s' 1 0)))))

(deftest take-from-at-last-usable-instant
  (let [s (stock-of [(lot 1 1 5 10)] 2)
        [s' ok] (stock/take-from s 1 1 9)]
    (is (true? ok))
    (is (= 4 (get-in s' [:lots 1 :qty])))))

(deftest take-from-refuses-and-changes-nothing
  (let [s (stock-of [(lot 1 1 5 10)
                     (lot 2 2 5 10 :quarantined)
                     (lot 3 1 0 10)
                     (lot 4 1 5 10)]
                    5)]
    (are [id n now] (= [s false] (stock/take-from s id n now))
      1  0  0    ;; n below 1
      1  6  0    ;; n above qty
      99 1  0    ;; no such lot
      2  1  0    ;; quarantined
      3  1  0    ;; empty lot
      4  1 10))) ;; expired exactly at now

;; --- purge -----------------------------------------------------------------

(deftest purge-removes-expired-lots-whatever-their-status
  (let [s (stock-of [(lot 1 1 5 10)                 ;; expired at 10
                     (lot 2 1 5 5 :quarantined)     ;; expired, quarantined
                     (lot 3 1 5 20)                 ;; still good
                     (lot 4 1 0 5)]                 ;; expired and empty
                    5)
        p (stock/purge s 10)]
    (is (= [3] (sort (keys (:lots p)))))
    (is (= 5 (:next-id p)))
    (is (= (lot 3 1 5 20) (get-in p [:lots 3])))))

(deftest purge-expiry-boundary
  (let [s (stock-of [(lot 1 1 5 10) (lot 2 1 5 11)] 3)]
    (is (= [2] (sort (keys (:lots (stock/purge s 10))))) "expires <= now goes")
    (is (= [1 2] (sort (keys (:lots (stock/purge s 9))))) "expires > now stays")))

(deftest purge-empty-stock
  (is (= (stock-of [] 3) (stock/purge (stock-of [] 3) 0))))
