(ns slots.core-spec
  "Find and book free slots through the rooms component.  This spec builds
  on rooms.core-spec: a Calendar is the component's type, its fns are
  taken at their signatures, and its proved laws are lemmas here.  What a
  slot is, this spec says in its own words, over the calendar's data."
  (:require [writ.spec :refer [spec ann law graph refine example calls]]))

(spec slots.core {:require :proved, :uses [rooms.core-spec]})

;; --- the spec's own model of the component -------------------------------

(defn overlap?
  "Two half-open ranges share a tick.  Both must be real: an empty range
  shares none."
  [s e s2 e2]
  (and (< s e) (< s2 e2) (< s e2) (< s2 e)))

(defn free-in?
  "What rooms.core/free? means, in this spec's own words."
  [c room start end]
  (and (contains? (:rooms c) room)
       (< start end)
       (not-any? (fn [b]
                   (and (= room (:room b))
                        (overlap? (:start b) (:end b) start end)))
                 (vals (:bookings c)))))

(defn earliest-model
  "The earliest free slot: the smallest start with a free room, then the
  lowest room free there.  nil for a zero length, or none."
  [c len from until]
  (when (pos? len)
    (let [found (sort (for [start (range from (inc (- until len)))
                            room (:rooms c)
                            :when (free-in? c room start (+ start len))]
                        [start room]))]
      (when (seq found)
        (let [[start room] (first found)]
          [room start])))))

(defn book-model
  "What rooms.core/book does, in this spec's own words."
  [c room start end who]
  (if (free-in? c room start end)
    (let [id (:next-id c)]
      [(-> c
           (assoc-in [:bookings id] {:id id, :room room, :start start, :end end, :who who})
           (update :next-id inc))
       id])
    [c nil]))

(defn book-all-model
  "Each request booked at its earliest slot at the time it comes."
  [c requests from until]
  (reduce (fn [[cal placed] req]
            (let [e (earliest-model cal (:len req) from until)]
              (if (nil? e)
                [cal (conj placed nil)]
                (let [room (first e)
                      start (second e)
                      [cal2 _] (book-model cal room start (+ start (:len req)) (:who req))]
                  [cal2 (conj placed e)]))))
          [c []]
          requests))

;; --- signatures ------------------------------------------------------------

(ann earliest [Calendar Nat Nat Nat -> (Opt (Tuple Nat Nat))])
(ann book-earliest [Calendar Nat Nat Nat Nat -> (Tuple Calendar (Opt (Tuple Nat Nat)))])
(ann book-all [Calendar (Vec {:who Nat, :len Nat}) Nat Nat -> (Tuple Calendar (Vec (Opt (Tuple Nat Nat))))])

;; --- laws ------------------------------------------------------------------

(law earliest-is-the-model
  {:require :tested
   :because "the model searches range and sorts; the prover does not read sort over a search"}
  (forall [c Calendar, len Nat, from Nat, until Nat]
    (= (earliest c len from until) (earliest-model c len from until))))

(law no-slot-of-zero-length
  (forall [c Calendar, from Nat, until Nat]
    (nil? (earliest c 0 from until))))

(law earliest-prefers-the-lower-room
  {:require :tested
   :because "the prover does not read reduce min over a filtered set"}
  (forall [lo Nat, hi Nat]
    (=> (< lo hi)
        (= (earliest {:rooms #{lo hi}, :bookings {}, :next-id 0} 1 0 1) [lo 0]))))

(law earliest-prefers-the-earlier-start
  (let [c {:rooms #{1 5}
           :bookings {0 {:id 0, :room 1, :start 0, :end 2, :who 7}}
           :next-id 1}]
    (= (earliest c 1 0 5) [5 0])))

(law book-earliest-books-the-earliest
  {:require :tested
   :because "it rests on the earliest search, which the prover does not read"}
  (forall [c Calendar, len Nat, from Nat, until Nat, who Nat]
    (let [e (earliest-model c len from until)]
      (= (book-earliest c len from until who)
         (if (nil? e)
           [c nil]
           (let [room (first e)
                 start (second e)
                 [cal2 _] (book-model c room start (+ start len) who)]
             [cal2 e]))))))

(law book-all-follows-the-requests
  {:require :tested
   :because "a fold over the requests compared pointwise to another fold, with the earliest search inside"}
  (forall [c Calendar, requests (Vec {:who Nat, :len Nat}), from Nat, until Nat]
    (= (book-all c requests from until) (book-all-model c requests from until))))

(law book-all-places-one-per-request
  {:require :tested
   :because "one fold's count against the request count"}
  (forall [c Calendar, requests (Vec {:who Nat, :len Nat}), from Nat, until Nat]
    (= (count (second (book-all c requests from until))) (count requests))))

;; --- the call graph the spec intends ---------------------------------------

(calls earliest [rooms.core/free?])
(calls book-earliest [earliest rooms.core/book])
(calls book-all [book-earliest])

;; --- the state graph -------------------------------------------------------

(graph slots
  {:states {:cal Calendar,
            :slot (Opt (Tuple Nat Nat)),
            :booked (Tuple Calendar (Opt (Tuple Nat Nat))),
            :all (Tuple Calendar (Vec (Opt (Tuple Nat Nat))))}
   :edges {:cal {[earliest Nat Nat Nat] #{:slot}
                 [book-earliest Nat Nat Nat Nat] #{:booked}
                 [book-all (Vec {:who Nat, :len Nat}) Nat Nat] #{:all}}
           :booked {[first] #{:cal}}}
   ;; that book-earliest returns the component's calendar is the
   ;; component's law to prove (book only returns well-formed calendars);
   ;; this spec does not unfold it
   :tested {[:cal book-earliest] "book-earliest returns rooms.core/book's calendar"}})

;; --- examples --------------------------------------------------------------

(example earliest [{:rooms #{1 2}, :bookings {}, :next-id 0} 2 0 4] [1 0])
(example earliest [{:rooms #{1 2}, :bookings {}, :next-id 0} 1 0 0] nil)
(example book-all [{:rooms #{1}, :bookings {}, :next-id 0}
                   [{:who 7, :len 2} {:who 8, :len 2}]
                   0 4]
         [{:rooms #{1}, :bookings {0 {:id 0, :room 1, :start 0, :end 2, :who 7},
                                   1 {:id 1, :room 1, :start 2, :end 4, :who 8}},
           :next-id 2}
          [[1 0] [1 2]]])
