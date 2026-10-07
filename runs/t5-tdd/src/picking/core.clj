(ns picking.core
  "Fill orders from a stock.core stock. Time is a Nat tick passed in by the
  caller."
  (:require [stock.core :as stock]))

(defn- usable-lots
  "The usable lots of sku at now, first-expiring-first, ties on lowest id."
  [s sku now]
  (->> (vals (:lots s))
       (filter (fn [lot]
                 (and (= sku (:sku lot))
                      (= :active (:status lot))
                      (<= 1 (:qty lot))
                      (< now (:expires lot)))))
       (sort-by (juxt :expires :id))))

(defn plan
  "The picks that would take n of sku: a vector of [id k] pairs, each lot
  used up before the next is touched, the last pick taking only what is
  still needed. [] when n is 0; nil when the usable lots of sku hold less
  than n in all."
  [s sku n now]
  (loop [lots (usable-lots s sku now)
         need n
         picks []]
    (cond
      (zero? need) picks
      (empty? lots) nil
      :else
      (let [lot (first lots)
            k (min need (:qty lot))]
        (recur (rest lots) (- need k) (conj picks [(:id lot) k]))))))

(defn pick
  "Fill order, a vector of lines {:sku Nat :qty Nat}, in order, taking each
  line's plan through stock.core/take-from. A line whose plan is nil is
  short and takes nothing, and the next lines go on. Returns
  [stock' report] where report is
  {:filled [index ...] :short [index ...] :picks [[index id k] ...]}."
  [s order now]
  (loop [lines (seq order)
         idx 0
         s s
         filled []
         short []
         picks []]
    (if (empty? lines)
      [s {:filled filled :short short :picks picks}]
      (let [line (first lines)
            p (plan s (:sku line) (:qty line) now)]
        (if (nil? p)
          (recur (rest lines) (inc idx) s filled (conj short idx) picks)
          (let [[s' picks']
                (reduce (fn [[st ps] [id k]]
                          (let [[st' _ok] (stock/take-from st id k now)]
                            [st' (conj ps [idx id k])]))
                        [s picks]
                        p)]
            (recur (rest lines) (inc idx) s' (conj filled idx) short picks')))))))
