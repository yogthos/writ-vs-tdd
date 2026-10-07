(ns stock.core
  "Lots of goods with expiry dates, and the few things that happen to them.
  Time is a Nat tick passed in; nothing reads a clock.")

(defn- usable?
  "A lot is usable at `now` when it is :active, holds at least one unit,
  and has not reached its :expires."
  [lot now]
  (and (= :active (:status lot))
       (pos? (:qty lot))
       (< now (:expires lot))))

(defn receive
  "Add an :active lot of `qty` of `sku`, expiring at `expires`.  A qty of 0
  changes nothing and gives no id."
  [stock sku qty expires]
  (if (pos? qty)
    (let [id (:next-id stock)
          lot {:id id :sku sku :qty qty :expires expires :status :active}]
      [(assoc stock
              :lots (assoc (:lots stock) id lot)
              :next-id (inc id))
       id])
    [stock nil]))

(defn available
  "The total qty of the usable lots of `sku` at `now`."
  [stock sku now]
  (reduce + 0
          (map :qty
               (filter #(and (= sku (:sku %)) (usable? % now))
                       (vals (:lots stock))))))

(defn quarantine
  "Hold lot `id` back.  With no such lot, nothing changes."
  [stock id]
  (if (contains? (:lots stock) id)
    (assoc stock :lots
           (assoc (:lots stock) id
                  (assoc (get (:lots stock) id) :status :quarantined)))
    stock))

(defn release
  "Make a quarantined lot `id` active again.  Anything else changes nothing."
  [stock id]
  (let [lot (get (:lots stock) id)]
    (if (and (some? lot) (= :quarantined (:status lot)))
      (assoc stock :lots (assoc (:lots stock) id (assoc lot :status :active)))
      stock)))

(defn take-from
  "Take `n` from usable lot `id`.  Its qty drops, a lot reaching 0 is
  removed, and `ok?` is true; otherwise nothing changes and `ok?` is false."
  [stock id n now]
  (let [lot (get (:lots stock) id)]
    (if (and (some? lot) (usable? lot now) (<= 1 n) (<= n (:qty lot)))
      (let [q (- (:qty lot) n)]
        [(assoc stock :lots
                (if (zero? q)
                  (dissoc (:lots stock) id)
                  (assoc (:lots stock) id (assoc lot :qty q))))
         true])
      [stock false])))

(defn purge
  "Remove every lot that has expired (`:expires <= now`), whatever its status."
  [stock now]
  (assoc stock :lots
         (reduce-kv (fn [m id lot]
                      (if (<= (:expires lot) now) m (assoc m id lot)))
                    {}
                    (:lots stock))))
