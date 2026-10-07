(ns slots.z2
  "Deep mutant: earliest tries starts from, from+len, from+2len, ..."
  (:require [rooms.core :as rooms]))

(defn earliest [cal len from until]
  (when (pos? len)
    (let [starts (take-while #(<= (+ % len) until) (iterate #(+ % len) from))]
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
  (reduce (fn [[cal placed] {:keys [who len]}]
            (let [[cal' slot] (book-earliest cal len from until who)]
              [cal' (conj placed slot)]))
          [cal []]
          requests))
