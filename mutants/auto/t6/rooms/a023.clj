(ns rooms.a023
  "Reference implementation (hidden from the model under test).")

(defn- clash? [b room start end]
  (and (< (:start b) (:end b)) (= room (:room b)) (< (:start b) end) (< start (:end b))))

(defn free? [cal room start end]
  (and (contains? (:rooms cal) room)
       (< start end)
       (not-any? #(clash? % room start end) (vals (:bookings cal)))))

(defn book [cal room start end who]
  (if (free? cal room start end)
    (let [id (:next-id cal)]
      [(-> cal
           (assoc-in [:bookings id] {:id id :room room :start start :end end :who who})
           (update :next-id inc))
       id])
    [cal nil]))

(defn cancel [cal id who]
  (if (= who (get-in cal [:bookings id :who]))
    (update cal :bookings dissoc id)
    cal))

(defn busy [cal room]
  (vec (sort (map (juxt :start :end) (filter #(= room (:room %)) (vals (:bookings cal)))))))

(defn usage [cal who]
  (reduce + 0 (map #(+ (:end %) (:start %)) (filter #(= who (:who %)) (vals (:bookings cal))))))
