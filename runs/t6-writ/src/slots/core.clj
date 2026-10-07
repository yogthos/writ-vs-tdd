(ns slots.core
  "Find and book free time, through rooms.core."
  (:require [rooms.core :as rooms]))

(defn earliest [cal len from until]
  (when (pos? len)
    (some (fn [t]
            (let [free-rooms (filter #(rooms/free? cal % t (+ t len)) (:rooms cal))]
              (when (seq free-rooms)
                [(reduce min free-rooms) t])))
          (range from (inc (- until len))))))

(defn book-earliest [cal len from until who]
  (let [slot (earliest cal len from until)]
    (if (nil? slot)
      [cal nil]
      (let [[room start] slot
            [cal2 _] (rooms/book cal room start (+ start len) who)]
        [cal2 slot]))))

(defn book-all [cal requests from until]
  (loop [requests requests, cal cal, placed []]
    (if (empty? requests)
      [cal placed]
      (let [req (first requests)
            got (book-earliest cal (:len req) from until (:who req))]
        (recur (rest requests) (first got) (conj placed (second got)))))))
