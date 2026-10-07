(ns grade5
  (:require [ref.stock :as rs] [ref.picking :as rp] [stock.core :as is] [picking.core :as ip]))

(def fails (atom {}))
(defn try* [f] (try (f) (catch Throwable e (list :threw))))
(defn cmp [nm a b args]
  (let [x (try* a) y (try* b)]
    (when (not= x y) (swap! fails update nm (fnil conj []) {:args args :want x :got y}))
    x))

(defn rand-lot [i]
  {:id i :sku (rand-int 3) :qty (rand-int 6) :expires (rand-int 12)
   :status (rand-nth [:active :active :active :quarantined])})

(defn rand-stock []
  (let [n (rand-int 7)]
    {:lots (into {} (for [i (range n)] [i (rand-lot i)])) :next-id (+ n (rand-int 2))}))

(defn -main [& _]
  (dotimes [_ 2000]
    (let [s (rand-stock) now (rand-int 12) sku (rand-int 3) id (rand-int 8) n (rand-int 8) e (rand-int 14)
          order (vec (repeatedly (rand-int 4) (fn [] {:sku (rand-int 3) :qty (rand-int 8)})))]
      (cmp 'receive #(rs/receive s sku n e) #(is/receive s sku n e) [s sku n e])
      (cmp 'available #(rs/available s sku now) #(is/available s sku now) [s sku now])
      (cmp 'quarantine #(rs/quarantine s id) #(is/quarantine s id) [s id])
      (cmp 'release #(rs/release s id) #(is/release s id) [s id])
      (cmp 'take-from #(rs/take-from s id n now) #(is/take-from s id n now) [s id n now])
      (cmp 'purge #(rs/purge s now) #(is/purge s now) [s now])
      (cmp 'plan #(rp/plan s sku n now) #(ip/plan s sku n now) [s sku n now])
      (cmp 'pick #(rp/pick s order now) #(ip/pick s order now) [s order now])))
  (if (empty? @fails)
    (println "GRADE: every function agrees with the reference")
    (doseq [[f xs] @fails]
      (println "GRADE:" f "disagrees on" (count xs) "inputs, e.g.")
      (prn (first (sort-by #(count (pr-str %)) xs))))))
