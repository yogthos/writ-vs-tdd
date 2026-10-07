(ns rooms.core-spec
  "Bookings of meeting rooms: what free means, what booking and
  cancelling change, and what a calendar reports as taken.  Times are
  Nat ticks, covering start <= t < end, so ranges that only touch do not
  clash."
  (:require [writ.spec :refer [spec ann law graph refine example]]))

(spec rooms.core {:require :proved})

;; --- types -----------------------------------------------------------------

(refine Booking [b {:id Nat, :room Nat, :start Nat, :end Nat, :who Nat}] true)

(defn sane-booking? [b] (<= (:start b) (:end b)))

(defn fresh-ids? [c]
  (every? (fn [kv] (< (first kv) (:next-id c))) (:bookings c)))

(defn well-formed? [c]
  (and (every? sane-booking? (vals (:bookings c)))
       (fresh-ids? c)))

(defn settle
  "Make any base map a calendar: each booking sane and its id its own key,
  :next-id past every booking's id."
  [c]
  (let [bs (into {}
                 (for [[k b] (:bookings c)]
                   [k (assoc b
                             :id k
                             :start (min (:start b) (:end b))
                             :end (max (:start b) (:end b)))]))
        hi (reduce max -1 (keys bs))]
    (assoc c :bookings bs :next-id (max (:next-id c) (inc hi)))))

(refine Calendar [c {:rooms (Set Nat), :bookings (Map Nat Booking), :next-id Nat}]
  (well-formed? c)
  {:build settle})

;; --- signatures ------------------------------------------------------------

(ann free? [Calendar Nat Nat Nat -> Bool])
(ann book [Calendar Nat Nat Nat Nat -> (Tuple Calendar (Opt Nat))])
(ann cancel [Calendar Nat Nat -> Calendar])
(ann busy [Calendar Nat -> (List (Tuple Nat Nat))])
(ann usage [Calendar Nat -> Nat])

;; --- the spec's own words for what the code means --------------------------

(defn room-free?
  "room is one of the calendar's rooms, the range is a real one, and no
  booking of room shares a tick with it.  Ranges are half-open, so a
  booking that ends where another starts shares no tick; an empty booking
  or an empty query shares none either."
  [c room start end]
  (and (contains? (:rooms c) room)
       (< start end)
       (not-any? (fn [b]
                   (and (= room (:room b))
                        (< (:start b) (:end b))
                        (< start (:end b))
                        (< (:start b) end)))
                 (vals (:bookings c)))))

;; --- free? -----------------------------------------------------------------

(law free?-means-nothing-clashes
  (forall [c Calendar, room Nat, start Nat, end Nat]
    (= (free? c room start end) (room-free? c room start end))))

(law a-room-outside-the-calendar-is-never-free
  (forall [c Calendar, room Nat, start Nat, end Nat]
    (=> (not (contains? (:rooms c) room))
        (not (free? c room start end)))))

(law an-empty-range-is-never-free
  (forall [c Calendar, room Nat, start Nat, end Nat]
    (=> (<= end start) (not (free? c room start end)))))

(law a-booking-that-ends-where-another-starts-does-not-clash
  (let [c {:rooms #{1}
           :bookings {0 {:id 0, :room 1, :start 0, :end 10, :who 7}}
           :next-id 1}]
    (free? c 1 10 20)))

(law a-booking-that-shares-a-tick-clashes
  (let [c {:rooms #{1}
           :bookings {0 {:id 0, :room 1, :start 0, :end 10, :who 7}}
           :next-id 1}]
    (not (free? c 1 9 20))))

(law an-empty-booking-clashes-with-nothing
  (let [c {:rooms #{1}
           :bookings {0 {:id 0, :room 1, :start 5, :end 5, :who 7}}
           :next-id 1}]
    (free? c 1 0 10)))

;; --- book ------------------------------------------------------------------

(law book-adds-when-free
  (forall [c Calendar, room Nat, start Nat, end Nat, who Nat]
    (=> (free? c room start end)
        (let [[c2 id] (book c room start end who)]
          (and (= id (:next-id c))
               (= (get (:bookings c2) id)
                  {:id id, :room room, :start start, :end end, :who who})
               (= (:next-id c2) (inc (:next-id c)))
               (= (:rooms c2) (:rooms c))
               (= (dissoc (:bookings c2) id) (:bookings c)))))))

(law book-keeps-when-not-free
  (forall [c Calendar, room Nat, start Nat, end Nat, who Nat]
    (=> (not (free? c room start end))
        (= (book c room start end who) [c nil]))))

;; --- cancel ----------------------------------------------------------------

(law cancel-removes-only-the-owners
  (forall [c Calendar, id Nat, who Nat]
    (= (cancel c id who)
       (if (contains? (:bookings c) id)
         (if (= who (:who (get (:bookings c) id)))
           (update c :bookings dissoc id)
           c)
         c))))

;; --- busy ------------------------------------------------------------------

(law busy-is-the-rooms-pairs-sorted
  ;; sorted by the whole pair: with equal starts, the earlier end comes
  ;; first.  TASK only says "sorted by start", so this chooses the tie the
  ;; way `sort` does; without it a wrong tie-break satisfied every law.
  (forall [c Calendar, room Nat]
    (= (busy c room)
       (sort (for [b (vals (:bookings c)) :when (= room (:room b))]
               [(:start b) (:end b)])))))

;; --- usage -----------------------------------------------------------------

(law usage-is-the-booked-ticks
  (forall [c Calendar, who Nat]
    (= (usage c who)
       (reduce + 0 (for [b (vals (:bookings c)) :when (= who (:who b))]
                     (- (:end b) (:start b)))))))

;; --- the state graph -------------------------------------------------------

(graph calendar
  {:states {:cal Calendar,
            :yes Bool,
            :taken Nat,
            :pairs (List (Tuple Nat Nat)),
            :booked (Tuple Calendar (Opt Nat))}
   :edges {:cal {[free? Nat Nat Nat] #{:yes}
                 [book Nat Nat Nat Nat] #{:booked}
                 [cancel Nat Nat] #{:cal}
                 [busy Nat] #{:pairs}
                 [usage Nat] #{:taken}}
           :booked {[first] #{:cal}}}})

;; --- examples --------------------------------------------------------------

(example free? [{:rooms #{1}, :bookings {}, :next-id 0} 1 0 10] true)
(example free? [{:rooms #{1}, :bookings {0 {:id 0, :room 1, :start 0, :end 10, :who 7}}, :next-id 1}
                1 5 15]
         false)
(example busy [{:rooms #{1}, :bookings {}, :next-id 0} 1] [])
(example usage [{:rooms #{1}, :bookings {}, :next-id 0} 7] 0)
