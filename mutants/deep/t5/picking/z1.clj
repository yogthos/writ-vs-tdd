(ns picking.z1
  "Deep mutant: plan orders lots by expiry only, leaving equal expiries in the lots map order"
  (:require [stock.core :as stock]))

(defn- lot-usable? [lot now]
  (and lot (= :active (:status lot)) (pos? (:qty lot)) (< now (:expires lot))))

(defn plan [s sku n now]
  (let [lots (sort-by :expires
                      (filter #(and (= sku (:sku %)) (lot-usable? % now)) (vals (:lots s))))]
    (loop [lots lots need n out []]
      (cond
        (zero? need) out
        (empty? lots) nil
        :else (let [l (first lots) k (min need (:qty l))]
                (recur (rest lots) (- need k) (conj out [(:id l) k])))))))

(defn pick [s order now]
  (reduce (fn [[s rep] [i {:keys [sku qty]}]]
            (if-let [p (plan s sku qty now)]
              [(reduce (fn [s [id k]] (first (stock/take-from s id k now))) s p)
               (-> rep (update :filled conj i)
                   (update :picks into (map (fn [[id k]] [i id k]) p)))]
              [s (update rep :short conj i)]))
          [s {:filled [] :short [] :picks []}]
          (map-indexed vector order)))
