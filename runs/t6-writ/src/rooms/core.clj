(ns rooms.core
  "Bookings of meeting rooms.  A calendar holds its rooms, its bookings
  keyed by id, and the next unused id.  A booking covers start <= t <
  end; two bookings clash when they are for the same room and their tick
  ranges share a tick.")

(defn free? [cal room start end]
  (and (contains? (:rooms cal) room)
       (< start end)
       (not-any? (fn [b]
                   (and (= room (:room b))
                        (< (:start b) (:end b))
                        (< start (:end b))
                        (< (:start b) end)))
                 (vals (:bookings cal)))))

(defn book [cal room start end who]
  (if (free? cal room start end)
    (let [id (:next-id cal)]
      [(-> cal
           (assoc-in [:bookings id] {:id id, :room room, :start start, :end end, :who who})
           (update :next-id inc))
       id])
    [cal nil]))

(defn cancel [cal id who]
  (if (and (contains? (:bookings cal) id)
           (= who (:who (get (:bookings cal) id))))
    (update cal :bookings dissoc id)
    cal))

(defn busy [cal room]
  (sort (for [b (vals (:bookings cal)) :when (= room (:room b))]
          [(:start b) (:end b)])))

(defn usage [cal who]
  (reduce + 0 (for [b (vals (:bookings cal)) :when (= who (:who b))]
                (- (:end b) (:start b)))))
