(ns rooms.core
  "Bookings of meeting rooms.

  A calendar is

      {:rooms #{room-id ...}, :bookings {id booking}, :next-id Nat}

  and a booking is {:id Nat, :room Nat, :start Nat, :end Nat, :who Nat}
  covering the ticks start <= t < end.")

;; A booking [a-start, a-end) clashes with [b-start, b-end) when the two
;; tick ranges share a tick. Half-open ranges touch, but do not overlap,
;; when one ends exactly where the other starts.
(defn- clash?
  [a-start a-end b-start b-end]
  (and (< a-start b-end) (< b-start a-end)))

(defn free?
  "True when `room` is one of `cal`'s rooms, `start` < `end`, and no
  booking of `room` clashes with [start, end)."
  [cal room start end]
  (and (contains? (:rooms cal) room)
       (< start end)
       (not-any? (fn [[_ b]]
                   (and (= (:room b) room)
                        (clash? start end (:start b) (:end b))))
                 (:bookings cal))))

(defn book
  "Book `room` from `start` to `end` for `who`. Returns [cal' id] when the
  slot is free, with the new booking stored under the old :next-id and
  :next-id incremented. Otherwise returns [cal nil]."
  [cal room start end who]
  (if (free? cal room start end)
    (let [id (:next-id cal)
          b {:id id :room room :start start :end end :who who}]
      [(-> cal
           (assoc-in [:bookings id] b)
           (update :next-id inc))
       id])
    [cal nil]))

(defn cancel
  "Remove booking `id` when it exists and belongs to `who`; anything else
  leaves the calendar unchanged."
  [cal id who]
  (let [b (get (:bookings cal) id)]
    (if (and b (= (:who b) who))
      (update cal :bookings dissoc id)
      cal)))

(defn busy
  "The bookings of `room` as [start end] pairs, sorted by start."
  [cal room]
  (->> (vals (:bookings cal))
       (filter #(= (:room %) room))
       (sort-by :start)
       (mapv (fn [b] [(:start b) (:end b)]))))

(defn usage
  "Total number of ticks booked by `who`, over every room."
  [cal who]
  (->> (vals (:bookings cal))
       (filter #(= (:who %) who))
       (reduce (fn [total b] (+ total (- (:end b) (:start b)))) 0)))
