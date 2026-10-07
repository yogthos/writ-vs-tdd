(ns grade6
  (:require [ref.rooms :as rr] [ref.slots :as rs] [rooms.core :as ir] [slots.core :as is]))

(def fails (atom {}))
(defn try* [f] (try (f) (catch Throwable e (list :threw))))
(defn cmp [nm a b args]
  (let [x (try* a) y (try* b)]
    (when (not= x y) (swap! fails update nm (fnil conj []) {:args args :want x :got y}))
    x))

(defn rand-cal []
  (let [rooms (set (repeatedly (rand-int 4) #(rand-int 4)))
        bs (reduce (fn [cal _]
                     (let [s (rand-int 20)]
                       (first (rr/book cal (rand-int 4) s (+ s 1 (rand-int 6)) (rand-int 3)))))
                   {:rooms rooms :bookings {} :next-id 0}
                   (range (rand-int 8)))]
    (if (zero? (rand-int 5))
      ;; ids out of insertion order now and then
      (let [ks (shuffle (vec (keys (:bookings bs))))]
        (assoc bs :bookings (into {} (map (fn [k] [k (get (:bookings bs) k)]) ks))))
      bs)))

(defn -main [& _]
  (dotimes [_ 2000]
    (let [cal (rand-cal) room (rand-int 5) s (rand-int 22) e (rand-int 26) who (rand-int 3) id (rand-int 9)
          len (rand-int 7) from (rand-int 15) until (+ from (rand-int 20))
          reqs (vec (repeatedly (rand-int 4) (fn [] {:who (rand-int 3) :len (rand-int 7)})))]
      (cmp 'free? #(rr/free? cal room s e) #(ir/free? cal room s e) [cal room s e])
      (cmp 'book #(rr/book cal room s e who) #(ir/book cal room s e who) [cal room s e who])
      (cmp 'cancel #(rr/cancel cal id who) #(ir/cancel cal id who) [cal id who])
      (cmp 'busy #(rr/busy cal room) #(ir/busy cal room) [cal room])
      (cmp 'usage #(rr/usage cal who) #(ir/usage cal who) [cal who])
      (cmp 'earliest #(rs/earliest cal len from until) #(is/earliest cal len from until) [cal len from until])
      (cmp 'book-earliest #(rs/book-earliest cal len from until who) #(is/book-earliest cal len from until who) [cal len from until who])
      (cmp 'book-all #(rs/book-all cal reqs from until) #(is/book-all cal reqs from until) [cal reqs from until])))
  (if (empty? @fails)
    (println "GRADE: every function agrees with the reference")
    (doseq [[f xs] @fails]
      (println "GRADE:" f "disagrees on" (count xs) "inputs, e.g.")
      (prn (first (sort-by #(count (pr-str %)) xs))))))
