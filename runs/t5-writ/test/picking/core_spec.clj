(ns picking.core-spec
  "The contract for picking.core, built on stock.core's spec.

  A plan is the picks that would take n of a sku: first-expiring-first,
  lowest id on a tie, each lot used up before the next is touched, the
  last pick only what is still needed, [] for n 0, nil when the usable
  lots hold less than n.  `pick` fills an order line by line through the
  component's take-from.

  What the component means is said here in the spec's own words
  (`model-plan`, `model-pick`), which do not call the component's fns, so
  this spec loads and checks before stock.core is written.  The target's
  own code must reach stock.core/take-from to fill a line."
  (:require [writ.spec :refer [spec ann law graph refine example calls]]))

(spec picking.core {:require :proved :uses [stock.core-spec]})

;; --- types ----------------------------------------------------------------------

(refine Line [l {:sku Nat, :qty Nat}] true)

(refine Report [r {:filled (Vec Nat)
                   :short  (Vec Nat)
                   :picks  (Vec (Tuple Nat Nat Nat))}] true)

(ann plan [Stock Nat Nat Nat -> (Opt (Vec (Tuple Nat Nat)))])
(ann pick [Stock (Vec Line) Nat -> (Tuple Stock Report)])

;; --- the component's meaning in this spec's own words ----------------------------

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

(defn ordered
  "Earliest to expire first; on a tie, the lowest id."
  [lots]
  (sort-by (juxt :expires :id) lots))

(defn model-plan
  "The picks that would take n of sku: [] for n 0, nil when short, else
  each usable lot in order used up, the last taking only what is needed."
  [stock sku n now]
  (if (zero? n)
    []
    (let [[picks left]
          (reduce (fn [[acc need] l]
                    (if (pos? need)
                      (let [k (min need (:qty l))]
                        [(conj acc [(:id l) k]) (- need k)])
                      [acc need]))
                  [[] n]
                  (ordered (live stock sku now)))]
      (if (pos? left) nil picks))))

(defn model-take
  "The component's take-from on a lots map, as a lot map again: the qty
  drops, and a lot reaching 0 is removed."
  [lots id k]
  (let [l (get lots id)
        q (- (:qty l) k)]
    (if (zero? q)
      (dissoc lots id)
      (assoc lots id (assoc l :qty q)))))

(defn model-pick
  "The order filled line by line against the model above.  A line whose
  plan is nil is short and takes nothing; its picks are tagged with its
  index, in the order they were made."
  [stock order now]
  (loop [i 0
         lots (:lots stock)
         filled []
         short []
         picks []]
    (if (< i (count order))
      (let [line (nth order i)
            p (model-plan (assoc stock :lots lots) (:sku line) (:qty line) now)]
        (if (nil? p)
          (recur (inc i) lots filled (conj short i) picks)
          (let [[lots2 picks2]
                (reduce (fn [[ls ps] [id k]]
                          [(model-take ls id k) (conj ps [i id k])])
                        [lots picks]
                        p)]
            (recur (inc i) lots2 (conj filled i) short picks2))))
      [(assoc stock :lots lots)
       {:filled filled :short short :picks picks}])))

;; --- the state graph -------------------------------------------------------------

(graph picking
  {:states {:stock Stock
            :plan  (Opt (Vec (Tuple Nat Nat)))
            :picked (Tuple Stock Report)}
   :edges  {:stock  {[plan Nat Nat Nat]     #{:plan}
                     [pick (Vec Line) Nat]  #{:picked}}
            :picked {[first] #{:stock}}}})

;; --- the target must fill through the component ----------------------------------

(calls pick {:through [stock.core/take-from]})

;; --- the laws --------------------------------------------------------------------

(law plan-is-the-greedy-first-expiring
  {:require :tested
   :because "the plan orders a filtered map of unknown size; the prover does not model that sort"}
  (forall [s Stock, sku Nat, n Nat, now Nat]
    (= (plan s sku n now) (model-plan s sku n now))))

(law pick-fills-every-line-it-can
  {:require :tested
   :because "the fill threads a stock through a fold over the order; the prover found no proof"}
  (forall [s Stock, order (Vec Line), now Nat]
    (= (pick s order now) (model-pick s order now))))

;; --- examples at the boundaries --------------------------------------------------

(def empty-stock {:lots {} :next-id 0})

(def two-lots
  {:lots {2 {:id 2 :sku 4 :qty 5 :expires 10 :status :active}
          1 {:id 1 :sku 4 :qty 3 :expires 10 :status :active}}
   :next-id 3})

;; n 0 is the empty plan, even with nothing in stock
(example plan [empty-stock 4 0 0] [])
;; short: fewer usable units than asked
(example plan [empty-stock 4 1 0] nil)
;; a tie on :expires goes to the lowest id; that lot is used up first
(example plan [two-lots 4 4 9] [[1 3] [2 1]])
;; the whole lot exactly: one pick, used up
(example plan [{:lots {1 {:id 1 :sku 4 :qty 3 :expires 10 :status :active}} :next-id 2} 4 3 9]
         [[1 3]])
;; a quarantined lot is not planned
(example plan [{:lots {1 {:id 1 :sku 4 :qty 3 :expires 10 :status :quarantined}} :next-id 2} 4 1 9]
         nil)
;; an empty order fills to an empty report and leaves the stock
(example pick [two-lots [] 9] [two-lots {:filled [] :short [] :picks []}])
;; a short line takes nothing and the next line goes on
(example pick [{:lots {1 {:id 1 :sku 4 :qty 1 :expires 10 :status :active}
                       2 {:id 2 :sku 5 :qty 2 :expires 10 :status :active}}
                :next-id 3}
               [{:sku 4 :qty 2} {:sku 5 :qty 1}] 9]
         [{:lots {1 {:id 1 :sku 4 :qty 1 :expires 10 :status :active}
                   2 {:id 2 :sku 5 :qty 1 :expires 10 :status :active}}
           :next-id 3}
          {:filled [1] :short [0] :picks [[1 2 1]]}])
;; a line of qty 0 is filled, with no picks
(example pick [two-lots [{:sku 4 :qty 0}] 9]
         [two-lots {:filled [0] :short [] :picks []}])
