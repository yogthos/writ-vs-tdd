(ns slots.core-test
  "clojure.test tests for slots.core. These pin the earliest-slot search
  (smallest start, then lowest room), the from/until window, the len-0
  rule, and the return shapes of book-earliest and book-all."
  (:require [clojure.test :refer [deftest testing is are]]
            [rooms.core :as rooms]
            [slots.core :as slots]))

;; -- helpers -------------------------------------------------------------

(defn booking [id room start end who]
  {:id id :room room :start start :end end :who who})

(defn cal
  "A calendar with the given room ids and bookings. Bookings are keyed by
  their :id and next-id is supplied explicitly."
  [rooms bookings next-id]
  {:rooms (set rooms)
   :bookings (into {} (map (fn [b] [(:id b) b])) bookings)
   :next-id next-id})

;; -- earliest ------------------------------------------------------------

(deftest earliest-finds-first-free-slot
  (is (= [1 0] (slots/earliest (cal [1] [] 0) 5 0 10))))

(deftest earliest-honours-from
  (is (= [1 3] (slots/earliest (cal [1] [] 0) 5 3 10))))

(deftest earliest-honours-until
  (testing "start + len may equal until"
    (is (= [1 0] (slots/earliest (cal [1] [] 0) 5 0 5))))
  (testing "start + len may not exceed until"
    (is (nil? (slots/earliest (cal [1] [] 0) 5 0 4))))
  (testing "until before from finds nothing"
    (is (nil? (slots/earliest (cal [1] [] 0) 1 5 4)))))

(deftest earliest-zero-length-is-nil
  (is (nil? (slots/earliest (cal [1] [] 0) 0 0 10)))
  (is (nil? (slots/earliest (cal [1] [] 0) 0 5 5))))

(deftest earliest-no-rooms-is-nil
  (is (nil? (slots/earliest (cal [] [] 0) 1 0 10))))

(deftest earliest-skips-booked-time
  (let [c (cal [1] [(booking 0 1 0 5 9)] 1)]
    (is (= [1 5] (slots/earliest c 5 0 10)))))

(deftest earliest-uses-a-gap
  (let [c (cal [1] [(booking 0 1 0 2 9) (booking 1 1 4 6 9)] 2)]
    (is (= [1 2] (slots/earliest c 2 0 6)))))

(deftest earliest-bookings-before-from-still-block
  (testing "a booking covering from blocks starts inside it"
    (is (nil? (slots/earliest (cal [1] [(booking 0 1 0 10 9)] 1) 1 5 10))))
  (testing "from inside a booking moves to its end"
    (is (= [1 3] (slots/earliest (cal [1] [(booking 0 1 0 3 9)] 1) 1 2 10)))))

(deftest earliest-later-start-in-same-room
  (is (= [1 3] (slots/earliest (cal [1] [(booking 0 1 0 3 9)] 1) 1 0 10))))

(deftest earliest-jumps-over-chained-bookings
  (let [c (cal [1] [(booking 0 1 0 2 9) (booking 1 1 2 5 9)] 2)]
    (is (= [1 5] (slots/earliest c 1 0 10)))))

(deftest earliest-handles-nested-bookings
  (let [c (cal [1] [(booking 0 1 0 10 9) (booking 1 1 5 6 9)] 2)]
    (is (= [1 10] (slots/earliest c 1 0 15)))))

(deftest earliest-smallest-start-beats-room-order
  ;; room 1 is busy until 5, so room 2 at 0 wins despite its higher id.
  (let [c (cal [1 2] [(booking 0 1 0 5 9)] 1)]
    (is (= [2 0] (slots/earliest c 2 0 10)))))

(deftest earliest-lowest-room-wins-a-start-tie
  (is (= [1 0] (slots/earliest (cal [3 1 2] [] 0) 1 0 10))))

(deftest earliest-tie-break-skips-a-busy-lower-room
  (let [c (cal [1 2 3]
               [(booking 0 1 0 5 9) (booking 1 2 0 5 9)]
               2)]
    (is (= [3 0] (slots/earliest c 2 0 10)))))

(deftest earliest-lowest-room-at-a-shared-later-start
  ;; both rooms are busy until 2, so the tie is decided at start 2.
  (let [c (cal [2 1] [(booking 0 1 0 2 9) (booking 1 2 0 2 9)] 2)]
    (is (= [1 2] (slots/earliest c 1 0 10)))))

(deftest earliest-picks-whichever-room-offers-the-smallest-start
  ;; room 1's first gap is at 4; room 2's is at 3 -> room 2 at 3.
  (let [c (cal [1 2] [(booking 0 1 0 4 9) (booking 1 2 0 3 9)] 2)]
    (is (= [2 3] (slots/earliest c 1 0 10)))))

;; -- book-earliest -------------------------------------------------------

(deftest book-earliest-books-the-earliest-slot
  (let [c (cal [1] [] 0)
        [c' placed] (slots/book-earliest c 5 0 10 42)]
    (is (= [1 0] placed))
    (is (= 1 (:next-id c')))
    (is (= [(booking 0 1 0 5 42)] (vals (:bookings c'))))
    (is (= [[0 5]] (rooms/busy c' 1)))
    (is (= 5 (rooms/usage c' 42)))))

(deftest book-earliest-no-slot-leaves-calendar
  (let [c (cal [1] [(booking 0 1 0 10 9)] 1)
        [c' placed] (slots/book-earliest c 2 0 10 42)]
    (is (nil? placed))
    (is (= c c'))))

(deftest book-earliest-zero-length-leaves-calendar
  (let [c (cal [1] [] 0)
        [c' placed] (slots/book-earliest c 0 0 10 42)]
    (is (nil? placed))
    (is (= c c'))))

;; -- book-all ------------------------------------------------------------

(deftest book-all-empty-requests
  (let [c (cal [1] [] 0)]
    (is (= [c []] (slots/book-all c [] 0 10)))))

(deftest book-all-places-requests-in-order
  (let [c (cal [1] [] 0)
        [c' placed] (slots/book-all c [{:who 1 :len 5}
                                       {:who 1 :len 5}] 0 10)]
    (is (= [[1 0] [1 5]] placed))
    (is (vector? placed))
    (is (= [[0 5] [5 10]] (rooms/busy c' 1)))
    (is (= 10 (rooms/usage c' 1)))))

(deftest book-all-later-requests-see-earlier-bookings
  ;; request 1 takes room 1 at 0; request 2 must then take room 2 at 0.
  (let [c (cal [1 2] [] 0)
        [c' placed] (slots/book-all c [{:who 1 :len 10}
                                       {:who 2 :len 10}] 0 10)]
    (is (= [[1 0] [2 0]] placed))
    (is (= [[0 10]] (rooms/busy c' 1)))
    (is (= [[0 10]] (rooms/busy c' 2)))))

(deftest book-all-records-nil-and-continues
  (let [c (cal [1] [] 0)
        [c' placed] (slots/book-all c [{:who 1 :len 10}
                                       {:who 2 :len 5}] 0 10)]
    (is (= [[1 0] nil] placed))
    (is (= 2 (count placed)))
    (is (= [[0 10]] (rooms/busy c' 1)))
    (is (= 10 (rooms/usage c' 1)))
    (is (= 0 (rooms/usage c' 2)))))

(deftest book-all-nil-request-does-not-block-later
  (let [c (cal [1] [] 0)
        [c' placed] (slots/book-all c [{:who 1 :len 20}
                                       {:who 2 :len 10}] 0 10)]
    (is (= [nil [1 0]] placed))
    (is (= [[0 10]] (rooms/busy c' 1)))
    (is (= 0 (rooms/usage c' 1)))
    (is (= 10 (rooms/usage c' 2)))))

(deftest book-all-respects-from-and-until
  (let [c (cal [1] [] 0)]
    (is (= [[1 3]]
           (second (slots/book-all c [{:who 1 :len 5}] 3 8)))
        "the only request is placed at from")
    (is (= [[1 0] nil]
           (second (slots/book-all c [{:who 1 :len 5}
                                      {:who 2 :len 5}] 0 5)))
        "the second request cannot fit before until")))

(deftest book-all-zero-length-request-is-nil
  (let [c (cal [1] [] 0)
        [c' placed] (slots/book-all c [{:who 1 :len 0}] 0 10)]
    (is (= [nil] placed))
    (is (= c c'))))

(deftest book-all-mixes-placements-and-failures
  ;; room 1 only, until 10:
  ;;   req 0 (len 4) -> [1 0]
  ;;   req 1 (len 4) -> [1 4]
  ;;   req 2 (len 4) -> nil   (only [8,10) left)
  ;;   req 3 (len 2) -> [1 8]
  (let [c (cal [1] [] 0)
        [c' placed] (slots/book-all c [{:who 1 :len 4}
                                       {:who 2 :len 4}
                                       {:who 3 :len 4}
                                       {:who 4 :len 2}] 0 10)]
    (is (= [[1 0] [1 4] nil [1 8]] placed))
    (is (= [[0 4] [4 8] [8 10]] (rooms/busy c' 1)))
    (is (= 4 (rooms/usage c' 1)))
    (is (= 4 (rooms/usage c' 2)))
    (is (= 0 (rooms/usage c' 3)))
    (is (= 2 (rooms/usage c' 4)))))
