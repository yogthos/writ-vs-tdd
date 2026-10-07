(ns picking.core-test
  "Tests for picking.core: how a plan chooses and consumes lots, and what a
  pick reports."
  (:require [clojure.test :refer [deftest testing is are]]
            [picking.core :as picking]))

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

;; --- plan ------------------------------------------------------------------

(deftest plan-of-nothing
  (testing "n 0 is the empty plan, even with no stock"
    (is (= [] (picking/plan (stock-of [] 0) 1 0 0))))
  (testing "n 0 is the empty plan, even with stock"
    (is (= [] (picking/plan (stock-of [(lot 1 1 5 10)] 2) 1 0 0)))))

(deftest plan-single-lot
  (let [s (stock-of [(lot 1 1 10 10)] 2)]
    (testing "the last pick takes only what is needed"
      (is (= [[1 3]] (picking/plan s 1 3 0))))
    (testing "taking the whole lot is one full pick"
      (is (= [[1 10]] (picking/plan s 1 10 0))))
    (testing "more than the lot holds is nil"
      (is (nil? (picking/plan s 1 11 0))))))

(deftest plan-is-first-expiring-first
  (let [s (stock-of [(lot 1 1 3 10)
                     (lot 2 1 5 20)] 3)]
    (testing "the earlier lot is used up before the later one is touched"
      (is (= [[1 3] [2 2]] (picking/plan s 1 5 0))))
    (testing "the entire later lot when needed"
      (is (= [[1 3] [2 5]] (picking/plan s 1 8 0))))
    (testing "only part of the later lot"
      (is (= [[1 3] [2 1]] (picking/plan s 1 4 0))))))

(deftest plan-orders-by-expiry-not-by-id
  (let [s (stock-of [(lot 5 1 3 1)
                     (lot 2 1 3 3)] 6)]
    (is (= [[5 3] [2 3]] (picking/plan s 1 6 0)))))

(deftest plan-ties-break-on-lowest-id
  (let [s (stock-of [(lot 9 1 2 5)
                     (lot 3 1 2 5)
                     (lot 7 1 2 5)] 10)]
    (is (= [[3 2] [7 2] [9 2]] (picking/plan s 1 6 0)))
    (is (= [[3 2] [7 2]] (picking/plan s 1 4 0)))))

(deftest plan-fills-the-last-lot-only-as-far-as-needed
  (let [s (stock-of [(lot 1 1 3 10)
                     (lot 2 1 5 20)] 3)]
    (is (= [[1 3] [2 2]] (picking/plan s 1 5 0)))
    (is (= [[1 3] [2 3]] (picking/plan s 1 6 0)))))

(deftest plan-skips-unusable-lots
  (let [s (stock-of [(lot 1 1 5 10 :quarantined)
                     (lot 2 1 5 5)      ;; expired at now 5
                     (lot 3 1 0 10)     ;; empty
                     (lot 4 1 5 20)]
                    5)]
    (is (= [[4 5]] (picking/plan s 1 5 5)))
    (is (nil? (picking/plan s 1 6 5)))))

(deftest plan-uses-only-the-asked-sku
  (let [s (stock-of [(lot 1 1 5 10)
                     (lot 2 2 9 10)] 3)]
    (is (= [[1 4]] (picking/plan s 1 4 0)))
    (is (nil? (picking/plan s 1 6 0)))))

(deftest plan-is-nil-when-the-usable-lots-are-short
  (let [s (stock-of [(lot 1 1 2 10)
                     (lot 2 1 3 20)] 3)]
    (is (nil? (picking/plan s 1 6 0)))
    (testing "exactly enough is not short and leaves no empty pick"
      (is (= [[1 2] [2 3]] (picking/plan s 1 5 0))))))

(deftest plan-empty-stock
  (is (nil? (picking/plan (stock-of [] 0) 1 1 0))))

;; --- pick ------------------------------------------------------------------

(deftest pick-empty-order
  (let [s (stock-of [(lot 1 1 5 10)] 2)
        [s' report] (picking/pick s [] 0)]
    (is (= s s') "the stock is unchanged")
    (is (= {:filled [] :short [] :picks []} report))))

(deftest pick-single-line
  (let [s (stock-of [(lot 1 1 5 10)] 2)
        [s' report] (picking/pick s [{:sku 1 :qty 3}] 0)]
    (is (= {:filled [0] :short [] :picks [[0 1 3]]} report))
    (is (= 2 (get-in s' [:lots 1 :qty])))))

(deftest pick-line-across-several-lots
  (let [s (stock-of [(lot 1 1 2 10)
                     (lot 2 1 3 20)] 3)
        [s' report] (picking/pick s [{:sku 1 :qty 4}] 0)]
    (is (= {:filled [0] :short [] :picks [[0 1 2] [0 2 2]]} report))
    (is (not (contains? (:lots s') 1)) "the emptied lot is removed")
    (is (= 1 (get-in s' [:lots 2 :qty])))))

(deftest pick-zero-quantity-line-is-filled-with-no-picks
  (let [s (stock-of [(lot 1 1 5 10)] 2)
        [s' report] (picking/pick s [{:sku 1 :qty 0}] 0)]
    (is (= {:filled [0] :short [] :picks []} report))
    (is (= s s'))))

(deftest pick-zero-quantity-with-no-stock-is-filled
  (let [s (stock-of [] 0)
        [s' report] (picking/pick s [{:sku 7 :qty 0}] 0)]
    (is (= {:filled [0] :short [] :picks []} report))
    (is (= s s'))))

(deftest pick-short-line-takes-nothing
  (let [s (stock-of [(lot 1 1 3 10)] 2)
        [s' report] (picking/pick s [{:sku 1 :qty 5}] 0)]
    (is (= {:filled [] :short [0] :picks []} report))
    (is (= s s') "a short line is never partly filled")))

(deftest pick-lines-see-what-earlier-lines-took
  (let [s (stock-of [(lot 1 1 5 10)] 2)
        [s' report] (picking/pick s [{:sku 1 :qty 3}
                                     {:sku 1 :qty 2}] 0)]
    (is (= {:filled [0 1]
            :short []
            :picks [[0 1 3] [1 1 2]]}
           report))
    (is (not (contains? (:lots s') 1)))))

(deftest pick-moves-to-the-next-lot-once-one-is-emptied
  (let [s (stock-of [(lot 1 1 2 10)
                     (lot 2 1 5 20)] 3)
        [s' report] (picking/pick s [{:sku 1 :qty 2}
                                     {:sku 1 :qty 3}] 0)]
    (is (= {:filled [0 1]
            :short []
            :picks [[0 1 2] [1 2 3]]}
           report))
    (is (= (stock-of [(lot 2 1 2 20)] 3) s'))))

(deftest pick-mixed-order
  (let [s (stock-of [(lot 0 1 5 10)
                     (lot 1 1 10 20)
                     (lot 2 2 4 5)] 3)
        order [{:sku 1 :qty 6}
               {:sku 2 :qty 5}   ;; short: only 4 on hand, takes nothing
               {:sku 1 :qty 2}
               {:sku 99 :qty 1}] ;; short: no such sku
        [s' report] (picking/pick s order 0)]
    (is (= {:filled [0 2]
            :short [1 3]
            :picks [[0 0 5] [0 1 1] [2 1 2]]}
           report))
    (is (= (stock-of [(lot 1 1 7 20)
                      (lot 2 2 4 5)]
                     3)
           s'))))
