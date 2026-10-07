(ns slots.core
  "Finds and books free time through rooms.core."
  (:require [rooms.core :as rooms]))

(defn- earliest-in-room
  "The smallest start >= from at which `room` is free for `len` ticks,
  ignoring `until`. `len` is assumed positive."
  [cal room len from]
  (let [bookings (->> (vals (:bookings cal))
                      (filter #(= (:room %) room))
                      (sort-by :start))]
    (loop [candidate from
           bs bookings]
      (if (empty? bs)
        candidate
        (let [b (first bs)]
          (cond
            (<= (:end b) candidate) (recur candidate (rest bs))
            (>= (:start b) (+ candidate len)) candidate
            :else (recur (:end b) (rest bs))))))))

(defn earliest
  "The earliest free slot of `len` ticks in any room, as [room start].
  The slot satisfies (free? cal room start (+ start len)), from <= start
  and start + len <= until. Chooses the smallest start, then the lowest
  room id. Returns nil for len 0 or when no such slot exists."
  [cal len from until]
  (when (pos? len)
    (let [candidates (for [r (sort (:rooms cal))
                           :let [s (earliest-in-room cal r len from)]
                           :when (<= (+ s len) until)]
                       [s r])]
      (when (seq candidates)
        (let [[s r] (first (sort candidates))]
          [r s])))))

(defn book-earliest
  "Book the earliest free slot of `len` ticks for `who`. Returns
  [cal' [room start]] when a slot is found, or [cal nil] otherwise."
  [cal len from until who]
  (if-let [[room start] (earliest cal len from until)]
    (let [[cal'] (rooms/book cal room start (+ start len) who)]
      [cal' [room start]])
    [cal nil]))

(defn book-all
  "Book each request {:who Nat, :len Nat} in `requests`, in order, at its
  earliest slot after the bookings made for earlier requests. Returns
  [cal' placed] where `placed` is a vector with one entry per request:
  [room start] when booked, nil when no slot was found."
  [cal requests from until]
  (loop [cal cal
         reqs requests
         placed []]
    (if (empty? reqs)
      [cal placed]
      (let [{:keys [who len]} (first reqs)
            [cal' slot] (book-earliest cal len from until who)]
        (recur cal' (rest reqs) (conj placed slot))))))
