(ns picking.core
  (:require [stock.core :as stock]))

(defn plan
  "The picks that would take `n` of `sku` from `stock` at `now`: a vector of
  [id k] pairs, each taking k >= 1 from lot id. Usable lots are consumed
  first-expiring-first, ties broken by lowest id; each lot is used up before
  the next is touched and the last pick takes only what is still needed.
  With `n` 0 the plan is []; when the usable lots hold less than `n` in all
  the plan is nil."
  [stock sku n now]
  (if (= n 0)
    []
    (let [usable (->> (vals (:lots stock))
                      (filter #(and (= sku (:sku %))
                                    (stock/usable? % now)))
                      (sort-by (juxt :expires :id)))
          total (reduce + 0 (map :qty usable))]
      (if (< total n)
        nil
        (loop [remaining n
               lots (seq usable)
               picks []]
          (if (= remaining 0)
            picks
            (let [lot (first lots)
                  k (min remaining (:qty lot))]
              (recur (- remaining k)
                     (next lots)
                     (conj picks [(:id lot) k])))))))))

(defn pick
  "Fill `order`, a vector of lines {:sku Nat :qty Nat}, in order, from
  `stock` at `now`. Each line is filled by taking its plan's picks through
  `stock.core/take-from`; a line whose plan is nil is short and takes
  nothing, and later lines go on. Returns [stock' report] with

      {:filled [index ...], :short [index ...], :picks [[index id k] ...]}

  :filled and :short hold line indexes in order; :picks holds every pick
  made, in the order made, tagged with its line's index."
  [stock order now]
  (loop [stock stock
         idx 0
         lines (seq order)
         filled []
         short []
         picks []]
    (if (nil? lines)
      [stock {:filled filled :short short :picks picks}]
      (let [line (first lines)
            picks-for-line (plan stock (:sku line) (:qty line) now)]
        (if (nil? picks-for-line)
          (recur stock (inc idx) (next lines) filled (conj short idx) picks)
          (let [[stock' made]
                (reduce (fn [[st made] [id k]]
                          (let [[st' _] (stock/take-from st id k now)]
                            [st' (conj made [idx id k])]))
                        [stock []]
                        picks-for-line)]
            (recur stock'
                   (inc idx)
                   (next lines)
                   (conj filled idx)
                   short
                   (into picks made))))))))
