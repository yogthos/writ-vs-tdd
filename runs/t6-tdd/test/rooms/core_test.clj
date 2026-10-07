(ns rooms.core-test
  "clojure.test tests for rooms.core. These pin every rule stated in
  TASK.md: room membership and positive length for free?, clash on tick
  overlap, half-open adjacency, id allocation on book, cancel
  authorisation, busy ordering and usage totals."
  (:require [clojure.test :refer [deftest testing is are]]
            [rooms.core :as rooms]))

;; -- helpers -------------------------------------------------------------

(defn booking [id room start end who]
  {:id id :room room :start start :end end :who who})

(defn cal
  "A calendar with the given room ids and bookings. Bookings are keyed by
  their :id and next-id is supplied explicitly so tests can control ids."
  [rooms bookings next-id]
  {:rooms (set rooms)
   :bookings (into {} (map (fn [b] [(:id b) b])) bookings)
   :next-id next-id})

;; -- free? ---------------------------------------------------------------

(deftest free?-known-room-empty-calendar
  (testing "a known room with no bookings is free"
    (is (rooms/free? (cal [1] [] 0) 1 0 10)))
  (testing "an unknown room is never free"
    (is (not (rooms/free? (cal [1] [] 0) 2 0 10))))
  (testing "a calendar with no rooms has no free room"
    (is (not (rooms/free? (cal [] [] 0) 1 0 10)))))

(deftest free?-requires-positive-length
  (let [c (cal [1] [] 0)]
    (testing "start must be strictly less than end"
      (is (not (rooms/free? c 1 5 5)))
      (is (not (rooms/free? c 1 6 5)))
      (is (rooms/free? c 1 5 6)))))

(deftest free?-adjacent-ranges-do-not-clash
  (let [c (cal [1] [(booking 1 1 5 10 9)] 2)]
    (testing "ending at 10 and starting at 10 share no tick"
      (is (rooms/free? c 1 10 15)))
    (testing "ending at 5 and starting at 5 share no tick"
      (is (rooms/free? c 1 0 5)))))

(deftest free?-overlapping-ranges-clash
  (let [c (cal [1] [(booking 1 1 5 10 9)] 2)]
    (are [s e] (not (rooms/free? c 1 s e))
      5 10   ; exactly the booking
      5 6    ; same start, ends inside
      6 9    ; strictly inside
      9 10   ; ends at the booking end
      0 20   ; covers the booking
      0 7    ; overlaps on the left
      7 13   ; overlaps on the right
      4 6))) ; straddles the start

(deftest free?-gap-between-bookings
  (let [c (cal [1] [(booking 0 1 0 2 9) (booking 1 1 4 6 9)] 2)]
    (is (rooms/free? c 1 2 4))
    (is (rooms/free? c 1 2 3))
    (is (rooms/free? c 1 3 4))
    (is (not (rooms/free? c 1 1 2)))
    (is (not (rooms/free? c 1 4 5)))))

(deftest free?-only-the-same-room-clashes
  (let [c (cal [1 2] [(booking 1 1 5 10 9)] 2)]
    (is (rooms/free? c 2 5 10))
    (is (rooms/free? c 2 0 20))))

;; -- book ----------------------------------------------------------------

(deftest book-adds-booking-under-next-id
  (let [c (cal [1] [] 7)
        [c' id] (rooms/book c 1 2 5 42)]
    (is (= 7 id) "the id is the old next-id")
    (is (= 8 (:next-id c')) "next-id advances by one")
    (is (= (booking 7 1 2 5 42) (get (:bookings c') 7)))
    (is (= #{7} (set (keys (:bookings c')))))))

(deftest book-returns-a-pair
  (let [result (rooms/book (cal [1] [] 0) 1 0 5 1)]
    (is (= 2 (count result)))
    (is (map? (first result)))))

(deftest book-advances-ids-across-calls
  (let [c (cal [1 2] [] 0)
        [c1 i1] (rooms/book c 1 0 5 1)
        [c2 i2] (rooms/book c1 2 0 5 2)]
    (is (= 0 i1))
    (is (= 1 i2))
    (is (= 2 (:next-id c2)))
    (is (= 2 (count (:bookings c2))))))

(deftest book-clash-returns-nil-and-leaves-calendar
  (let [c (cal [1] [(booking 1 1 5 10 9)] 2)
        [c' id] (rooms/book c 1 7 8 3)]
    (is (nil? id))
    (is (= c c'))))

(deftest book-invalid-returns-nil-and-leaves-calendar
  (let [c (cal [1] [] 0)]
    (are [room s e] (let [[c' id] (rooms/book c room s e 1)]
                      (and (nil? id) (= c c')))
      2 0 5   ; room not in the calendar
      1 5 5   ; zero length
      1 6 5))) ; negative length

(deftest book-after-cancel-does-not-rewind-next-id
  (let [c (cal [1] [(booking 0 1 0 5 9)] 1)
        c1 (rooms/cancel c 0 9)
        [c2 id] (rooms/book c1 1 0 5 9)]
    (is (= 1 id))
    (is (= 2 (:next-id c2)))))

;; -- cancel --------------------------------------------------------------

(deftest cancel-removes-booking-when-who-matches
  (let [c (cal [1] [(booking 1 1 5 10 9)] 2)
        c' (rooms/cancel c 1 9)]
    (is (= {} (:bookings c')))
    (is (rooms/free? c' 1 5 10))))

(deftest cancel-wrong-who-changes-nothing
  (let [c (cal [1] [(booking 1 1 5 10 9)] 2)]
    (is (= c (rooms/cancel c 1 8)))))

(deftest cancel-missing-id-changes-nothing
  (let [c (cal [1] [(booking 1 1 5 10 9)] 2)]
    (is (= c (rooms/cancel c 2 9)))))

(deftest cancel-keeps-other-bookings-and-next-id
  (let [c (cal [1 2]
               [(booking 0 1 0 5 1)
                (booking 1 1 5 10 2)
                (booking 2 2 0 5 1)]
               3)
        c' (rooms/cancel c 1 2)]
    (is (= #{0 2} (set (keys (:bookings c')))))
    (is (= 3 (:next-id c')))
    (is (= [[0 5]] (rooms/busy c' 1)))
    (is (= [[0 5]] (rooms/busy c' 2)))))

;; -- busy ----------------------------------------------------------------

(deftest busy-returns-pairs-sorted-by-start
  (let [c (cal [1 2]
               [(booking 0 1 10 15 1)
                (booking 1 1 0 5 2)
                (booking 2 2 0 3 3)]
               3)]
    (is (= [[0 5] [10 15]] (rooms/busy c 1)))
    (is (= [[0 3]] (rooms/busy c 2)))
    (is (= [] (rooms/busy c 3)))))

(deftest busy-empty-for-room-without-bookings
  (is (= [] (rooms/busy (cal [1] [] 0) 1)))
  (is (= [] (rooms/busy (cal [1] [] 0) 99))))

;; -- usage ---------------------------------------------------------------

(deftest usage-sums-ticks-over-every-room
  (let [c (cal [1 2 3]
               [(booking 0 1 0 5 7)
                (booking 1 2 2 10 7)
                (booking 2 3 0 100 8)]
               3)]
    (is (= 13 (rooms/usage c 7)))
    (is (= 100 (rooms/usage c 8)))
    (is (= 0 (rooms/usage c 1)))))

(deftest usage-sums-multiple-bookings-in-one-room
  (let [c (cal [1] [(booking 0 1 0 5 7) (booking 1 1 10 12 7)] 2)]
    (is (= 7 (rooms/usage c 7)))))

(deftest usage-counts-overlapping-bookings-in-different-rooms
  (let [c (cal [1 2] [(booking 0 1 0 5 7) (booking 1 2 0 5 7)] 2)]
    (is (= 10 (rooms/usage c 7)))))
