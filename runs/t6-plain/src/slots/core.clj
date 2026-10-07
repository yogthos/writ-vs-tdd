(ns slots.core
  (:require [rooms.core :as rooms]))

(defn earliest
  "Earliest free slot of len ticks in any room: [room start] such that
  (rooms/free? cal room start (+ start len)), from <= start and
  start + len <= until. Smallest start wins; among rooms free at that start
  the lowest room id wins. With len 0, or no such slot, returns nil."
  [cal len from until]
  (when (pos? len)
    (let [limit (- until len)]
      (when (<= from limit)
        (let [candidates (->> (concat [from] (map :end (vals (:bookings cal))))
                              (filter #(and (<= from %) (<= % limit)))
                              distinct
                              sort)
              room-ids (sort (:rooms cal))]
          (first (for [start candidates
                       room room-ids
                       :when (rooms/free? cal room start (+ start len))]
                   [room start])))))))

(defn book-earliest
  "Books (earliest cal len from until) for who through rooms.core/book.
  Returns [cal' [room start]], or [cal nil] when there is no slot."
  [cal len from until who]
  (if-let [slot (earliest cal len from until)]
    (let [[room start] slot
          [cal' id] (rooms/book cal room start (+ start len) who)]
      (if id
        [cal' slot]
        [cal' nil]))
    [cal nil]))

(defn book-all
  "Books requests {:who Nat, :len Nat} in order, each at its earliest slot
  as of the time it comes (after the bookings made for earlier requests).
  Returns [cal' placed], placed with one entry per request: [room start] for
  a booked request, nil for one that found no slot."
  [cal requests from until]
  (reduce (fn [[c placed] {:keys [who len]}]
            (if-let [slot (earliest c len from until)]
              (let [[room start] slot
                    [c' _] (rooms/book c room start (+ start len) who)]
                [c' (conj placed slot)])
              [c (conj placed nil)]))
          [cal []]
          requests))
