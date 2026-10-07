(ns picking.core
  "Fill orders from stock.core: plan what to take first-expiring-first, and
  pick it through the component."
  (:require [stock.core :as stock]))

(defn- usable?
  [lot now]
  (and (= :active (:status lot))
       (pos? (:qty lot))
       (< now (:expires lot))))

(defn- live
  "The usable lots of `sku` in `stock` at `now`."
  [stock sku now]
  (filter #(and (= sku (:sku %)) (usable? % now)) (vals (:lots stock))))

(defn- ordered
  "Earliest to expire first; on a tie, the lowest id."
  [lots]
  (sort-by (juxt :expires :id) lots))

(defn plan
  "The picks that would take `n` of `sku`: first-expiring-first, each lot
  used up before the next, the last taking only what is still needed.  []
  for n 0, nil when the usable lots hold less than n in all."
  [stock sku n now]
  (if (zero? n)
    []
    (let [[picks left]
          (reduce (fn [[acc need] lot]
                    (if (pos? need)
                      (let [k (min need (:qty lot))]
                        [(conj acc [(:id lot) k]) (- need k)])
                      [acc need]))
                  [[] n]
                  (ordered (live stock sku now)))]
      (if (pos? left) nil picks))))

(defn- fill-line
  "Fill `line` at index `i` through the component, tagging each pick with
  the index.  A line whose plan is nil is short and takes nothing."
  [stock report i line now]
  (if-let [p (plan stock (:sku line) (:qty line) now)]
    (let [[s picks]
          (reduce (fn [[s ps] [id k]]
                    (let [[s2 _] (stock/take-from s id k now)]
                      [s2 (conj ps [i id k])]))
                  [stock (:picks report)]
                  p)]
      [s (assoc report
                :filled (conj (:filled report) i)
                :picks picks)])
    [stock (assoc report :short (conj (:short report) i))]))

(defn pick
  "Fill `order`, a vector of lines, in order.  Each line takes its plan's
  picks through stock.core/take-from; a short line takes nothing and the
  next lines go on.  Returns [stock' report]."
  [stock order now]
  (reduce (fn [[s report] [i line]]
            (fill-line s report i line now))
          [stock {:filled [] :short [] :picks []}]
          (map-indexed (fn [i line] [i line]) order)))
