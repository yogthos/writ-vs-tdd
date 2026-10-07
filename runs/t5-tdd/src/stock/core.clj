(ns stock.core
  "Lots of goods with expiry dates. Time is a Nat tick passed in by the
  caller; lot ids, SKUs and quantities are Nats.")

(defn- usable?
  "A lot is usable at now when it is :active, holds at least 1, and has not
  reached its expiry (now < :expires)."
  [lot now]
  (and (= :active (:status lot))
       (<= 1 (:qty lot))
       (< now (:expires lot))))

(defn receive
  "Add an :active lot of qty of sku expiring at expires. Returns
  [stock' id] where id is the new lot's id. With qty 0 the stock is
  unchanged and the id is nil."
  [{:keys [next-id] :as stock} sku qty expires]
  (if (<= 1 qty)
    [(-> stock
         (assoc-in [:lots next-id]
                   {:id next-id :sku sku :qty qty
                    :expires expires :status :active})
         (update :next-id inc))
     next-id]
    [stock nil]))

(defn available
  "Total :qty of the usable lots of sku at now."
  [stock sku now]
  (reduce (fn [total lot]
            (if (and (= sku (:sku lot)) (usable? lot now))
              (+ total (:qty lot))
              total))
          0
          (vals (:lots stock))))

(defn quarantine
  "Make lot id :quarantined. With no such lot, nothing changes."
  [stock id]
  (if (contains? (:lots stock) id)
    (assoc-in stock [:lots id :status] :quarantined)
    stock))

(defn release
  "Make a :quarantined lot id :active again. Anything else changes nothing."
  [stock id]
  (if (= :quarantined (get-in stock [:lots id :status]))
    (assoc-in stock [:lots id :status] :active)
    stock))

(defn take-from
  "Take n from lot id at now. Returns [stock' ok?]. When lot id is usable
  at now and 1 <= n <= its :qty, the lot's :qty drops by n and ok? is true;
  a lot whose :qty reaches 0 is removed. Otherwise the stock is unchanged
  and ok? is false."
  [stock id n now]
  (let [lot (get-in stock [:lots id])]
    (if (and lot (usable? lot now) (<= 1 n) (<= n (:qty lot)))
      (let [qty (- (:qty lot) n)]
        [(if (zero? qty)
           (update stock :lots dissoc id)
           (assoc-in stock [:lots id :qty] qty))
         true])
      [stock false])))

(defn purge
  "Remove every lot that has expired (:expires <= now), whatever its status."
  [stock now]
  (update stock :lots
          (fn [lots]
            (into {}
                  (remove (fn [[_ lot]] (<= (:expires lot) now)))
                  lots))))
