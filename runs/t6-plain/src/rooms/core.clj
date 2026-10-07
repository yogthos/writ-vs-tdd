(ns rooms.core)

(defn- overlaps?
  "True when [s1, e1) and [s2, e2) share at least one tick."
  [s1 e1 s2 e2]
  (and (< s1 e2) (< s2 e1)))

(defn- clashes?
  "True when booking b is for room and clashes with [start, end)."
  [b room start end]
  (and (= (:room b) room)
       (overlaps? start end (:start b) (:end b))))

(defn free?
  "True when room is one of the calendar's rooms, start < end, and no
  booking of room clashes with [start, end)."
  [cal room start end]
  (and (contains? (:rooms cal) room)
       (< start end)
       (not-any? #(clashes? % room start end) (vals (:bookings cal)))))

(defn book
  "When (free? cal room start end), adds a booking for who under :next-id,
  bumps :next-id, and returns [cal' id]. Otherwise returns [cal nil]."
  [cal room start end who]
  (if (free? cal room start end)
    (let [id (:next-id cal)
          booking {:id id :room room :start start :end end :who who}]
      [(-> cal
           (assoc-in [:bookings id] booking)
           (update :next-id inc))
       id])
    [cal nil]))

(defn cancel
  "When booking id exists and its :who is who, removes it. Anything else
  changes nothing. Returns the calendar."
  [cal id who]
  (let [booking (get (:bookings cal) id)]
    (if (and booking (= (:who booking) who))
      (update cal :bookings dissoc id)
      cal)))

(defn busy
  "The bookings of room as [start end] pairs, sorted by start."
  [cal room]
  (->> (vals (:bookings cal))
       (filter #(= (:room %) room))
       (map (fn [b] [(:start b) (:end b)]))
       (sort-by first)
       vec))

(defn usage
  "Total number of ticks booked by who, over every room."
  [cal who]
  (->> (vals (:bookings cal))
       (filter #(= (:who %) who))
       (map #(- (:end %) (:start %)))
       (reduce + 0)))
