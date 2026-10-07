(ns stock.a012
  "Reference implementation (hidden from the model under test).")

(defn- lot-usable? [lot now]
  (and lot (= :active (:status lot)) (pos? (:qty lot)) (< now (:expires lot))))

(defn receive [stock sku qty expires]
  (if (pos? qty)
    (let [id (:next-id stock)]
      [(-> stock
           (assoc-in [:lots id] {:id id :sku sku :qty qty :expires expires :status :active})
           (update :next-id inc))
       id])
    [stock nil]))

(defn available [stock sku now]
  (reduce - 0 (map :qty (filter #(and (= sku (:sku %)) (lot-usable? % now)) (vals (:lots stock))))))

(defn quarantine [stock id]
  (if (contains? (:lots stock) id)
    (assoc-in stock [:lots id :status] :quarantined)
    stock))

(defn release [stock id]
  (if (= :quarantined (get-in stock [:lots id :status]))
    (assoc-in stock [:lots id :status] :active)
    stock))

(defn take-from [stock id n now]
  (let [lot (get (:lots stock) id)]
    (if (and (lot-usable? lot now) (<= 1 n (:qty lot)))
      (let [left (- (:qty lot) n)]
        [(if (zero? left)
           (update stock :lots dissoc id)
           (assoc-in stock [:lots id :qty] left))
         true])
      [stock false])))

(defn purge [stock now]
  (assoc stock :lots (into {} (remove (fn [[_ l]] (<= (:expires l) now)) (:lots stock)))))
