(ns stock.core-spec
  "The contract for stock.core: lots of goods with expiry dates, and the
  few things that can happen to them.

  A lot is usable at a tick `now` when it is :active, holds at least one
  unit, and has not reached its :expires. Every law is written against the
  spec's own vocabulary (`usable?`, `live`), never against the code.

  Boundaries the laws anchor: `now` at :expires is expired (`<`, not
  `<=`), a lot of qty 0 is not usable, `take-from` may take exactly the
  whole lot (which removes it) but not one more, and `purge` takes a lot
  whose :expires equals `now` while `available` does not count it."
  (:require [writ.spec :refer [spec ann law graph refine example]]))

(spec stock.core {:require :proved})

;; --- types ----------------------------------------------------------------------

(refine Status [s Keyword] (contains? #{:active :quarantined} s))

(refine Lot [l {:id Nat, :sku Nat, :qty Nat, :expires Nat, :status Status}] true)

(refine Stock [s {:lots (Index :id Lot), :next-id Nat}] true)

(ann receive    [Stock Nat Nat Nat -> (Tuple Stock (Opt Nat))])
(ann available  [Stock Nat Nat -> Nat])
(ann quarantine [Stock Nat -> Stock])
(ann release    [Stock Nat -> Stock])
(ann take-from  [Stock Nat Nat Nat -> (Tuple Stock Bool)])
(ann purge      [Stock Nat -> Stock])

;; --- the spec's own vocabulary ---------------------------------------------------

(defn usable?
  "Is lot usable at `now`?  :active, at least one unit, not yet expired."
  [lot now]
  (and (= :active (:status lot))
       (pos? (:qty lot))
       (< now (:expires lot))))

(defn live
  "The usable lots of `sku` in `stock` at `now`."
  [stock sku now]
  (filter #(and (= sku (:sku %)) (usable? % now)) (vals (:lots stock))))

;; --- the state graph -------------------------------------------------------------

;; receive hands back a stock and the new id; take-from hands back a stock
;; and its verdict.  Taking the stock back out of either lands in :stock.
(graph stock
  {:states {:stock     Stock
            :available Nat
            :received  (Tuple Stock (Opt Nat))
            :taken     (Tuple Stock Bool)}
   :edges  {:stock     {[receive Nat Nat Nat]  #{:received}
                        [available Nat Nat]   #{:available}
                        [quarantine Nat]      #{:stock}
                        [release Nat]         #{:stock}
                        [take-from Nat Nat Nat] #{:taken}
                        [purge Nat]           #{:stock}}
            :received  {[first] #{:stock}}
            :taken     {[first] #{:stock}}}})

;; --- receiving -------------------------------------------------------------------

(law receive-adds-an-active-lot
  (forall [s Stock, sku Nat, qty Nat, expires Nat]
    (=> (pos? qty)
        (let [[s2 id] (receive s sku qty expires)]
          (and (= id (:next-id s))
               (= (inc (:next-id s)) (:next-id s2))
               (= (assoc (:lots s) id
                         {:id id :sku sku :qty qty :expires expires :status :active})
                  (:lots s2)))))))

(law receive-of-nothing-is-nothing
  (forall [s Stock, sku Nat, expires Nat]
    (= [s nil] (receive s sku 0 expires))))

;; --- counting what is available --------------------------------------------------

(law available-counts-the-usable-lots
  (forall [s Stock, sku Nat, now Nat]
    (= (available s sku now)
       (reduce + 0 (map :qty (live s sku now))))))

;; --- holding a lot back and letting it go ----------------------------------------

(law quarantine-marks-the-lot
  (forall [s Stock, id Nat]
    (let [l (get (:lots s) id)]
      (= (quarantine s id)
         (if (some? l)
           (assoc s :lots (assoc (:lots s) id (assoc l :status :quarantined)))
           s)))))

(law release-reactivates-a-quarantined-lot
  (forall [s Stock, id Nat]
    (let [l (get (:lots s) id)
          held (and (some? l) (= :quarantined (:status l)))]
      (= (release s id)
         (if held
           (assoc s :lots (assoc (:lots s) id (assoc l :status :active)))
           s)))))

;; --- taking units out ------------------------------------------------------------

(law take-from-takes-when-it-may
  (forall [s Stock, id Nat, n Nat, now Nat]
    (let [l (get (:lots s) id)
          can (and (some? l) (usable? l now) (<= 1 n) (<= n (:qty l)))]
      (= (take-from s id n now)
         (if can
           (let [q (- (:qty l) n)
                 lots (if (zero? q)
                        (dissoc (:lots s) id)
                        (assoc (:lots s) id (assoc l :qty q)))]
             [(assoc s :lots lots) true])
           [s false])))))

;; --- clearing the shelf ----------------------------------------------------------

(law purge-drops-the-expired
  {:require :tested
   :because "purge folds a map of unknown size; the prover does not model the fold and found no proof"}
  (forall [s Stock, now Nat]
    (= (purge s now)
       (assoc s :lots
              (into {} (remove (fn [[_ l]] (<= (:expires l) now)) (:lots s)))))))

;; --- examples at the boundaries --------------------------------------------------

(def empty-stock {:lots {} :next-id 0})

(def one-lot
  {:lots {1 {:id 1 :sku 4 :qty 3 :expires 10 :status :active}}
   :next-id 2})

;; qty 0 leaves the stock and the id alone
(example receive [empty-stock 7 0 20] [empty-stock nil])

;; qty 1 adds the lot under :next-id and moves :next-id on
(example receive [empty-stock 7 5 20]
         [{:lots {0 {:id 0 :sku 7 :qty 5 :expires 20 :status :active}} :next-id 1}
          0])

;; a lot is usable just before it expires ...
(example available [one-lot 4 9] 3)
;; ... and not at :expires
(example available [one-lot 4 10] 0)
;; a quarantined lot is not usable
(example available [{:lots {1 {:id 1 :sku 4 :qty 3 :expires 10 :status :quarantined}}
                     :next-id 2} 4 9] 0)
;; an empty lot is not usable
(example available [{:lots {1 {:id 1 :sku 4 :qty 0 :expires 10 :status :active}}
                     :next-id 2} 4 9] 0)
;; another sku's lot is not counted
(example available [one-lot 5 9] 0)

;; taking exactly the lot removes it
(example take-from [one-lot 1 3 9] [{:lots {} :next-id 2} true])
;; taking one more than it holds is refused
(example take-from [one-lot 1 4 9] [one-lot false])
;; taking none is refused
(example take-from [one-lot 1 0 9] [one-lot false])
;; taking at :expires is refused
(example take-from [one-lot 1 1 10] [one-lot false])
;; a partial take leaves the rest
(example take-from [one-lot 1 2 9]
         [{:lots {1 {:id 1 :sku 4 :qty 1 :expires 10 :status :active}} :next-id 2} true])

;; purge takes a lot at its expiry, whatever its status
(example purge [one-lot 10] {:lots {} :next-id 2})
(example purge [{:lots {1 {:id 1 :sku 4 :qty 3 :expires 10 :status :quarantined}}
                 :next-id 2} 10]
         {:lots {} :next-id 2})
(example purge [one-lot 9] one-lot)
