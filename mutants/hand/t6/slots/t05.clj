(ns slots.t05
  "Mutant: book-all looks for slots in the calendar it started with"
  (:require [rooms.core :as rooms]))

(defn earliest [cal len from until]
  (when (pos? len)
    (let [starts (sort (distinct (cons from (filter #(<= from %) (map :end (vals (:bookings cal)))))))]
      (first (for [s starts
                   :when (<= (+ s len) until)
                   r (sort (:rooms cal))
                   :when (rooms/free? cal r s (+ s len))]
               [r s])))))

(defn book-earliest [cal len from until who]
  (if-let [[r s] (earliest cal len from until)]
    [(first (rooms/book cal r s (+ s len) who)) [r s]]
    [cal nil]))

(defn book-all [cal requests from until]
  (let [cal0 cal]
    (reduce (fn [[cal placed] {:keys [who len]}]
              (if-let [[r s] (earliest cal0 len from until)]
                [(first (rooms/book cal r s (+ s len) who)) (conj placed (when (rooms/free? cal r s (+ s len)) [r s]))]
                [cal (conj placed nil)]))
            [cal []]
            requests)))
