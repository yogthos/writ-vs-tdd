(ns stock.core)

(defn usable?
  "A lot is usable at `now` when it is :active, holds at least 1, and has
  not expired (`now` before `:expires`)."
  [lot now]
  (and (= :active (:status lot))
       (>= (:qty lot) 1)
       (< now (:expires lot))))

(defn receive
  "Add a lot of `qty` of `sku` expiring at `expires`. With `qty` at least 1
  the new lot is :active and gets the next id; returns [stock' id]. With
  `qty` 0 the stock is unchanged and the id is nil."
  [stock sku qty expires]
  (if (>= qty 1)
    (let [id (:next-id stock)
          lot {:id id :sku sku :qty qty :expires expires :status :active}]
      [(-> stock
           (assoc-in [:lots id] lot)
           (update :next-id inc))
       id])
    [stock nil]))

(defn available
  "Total :qty of the usable lots of `sku` at `now`."
  [stock sku now]
  (reduce (fn [acc lot]
            (if (and (= sku (:sku lot)) (usable? lot now))
              (+ acc (:qty lot))
              acc))
          0
          (vals (:lots stock))))

(defn quarantine
  "Make lot `id` :quarantined; with no such lot nothing changes."
  [stock id]
  (if (contains? (:lots stock) id)
    (assoc-in stock [:lots id :status] :quarantined)
    stock))

(defn release
  "Make a :quarantined lot `id` :active again; anything else changes nothing."
  [stock id]
  (if (= :quarantined (get-in stock [:lots id :status]))
    (assoc-in stock [:lots id :status] :active)
    stock))

(defn take-from
  "When lot `id` is usable at `now` and 1 <= n <= its :qty, drop its :qty by
  n (removing it from the stock if it reaches 0) and return [stock' true].
  Otherwise return [stock false]."
  [stock id n now]
  (let [lot (get (:lots stock) id)]
    (if (and lot (usable? lot now) (>= n 1) (<= n (:qty lot)))
      [(if (= n (:qty lot))
         (update stock :lots dissoc id)
         (assoc-in stock [:lots id :qty] (- (:qty lot) n)))
       true]
      [stock false])))

(defn purge
  "Remove every lot that has expired (`:expires` <= `now`), whatever its
  status."
  [stock now]
  (update stock :lots
          (fn [lots]
            (into {} (remove (fn [[_ lot]] (<= (:expires lot) now)) lots)))))
