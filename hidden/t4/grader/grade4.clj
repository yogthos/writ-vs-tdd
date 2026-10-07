(ns grade4
  (:require [ref.queue :as rq] [ref.dispatch :as rd] [queue.core :as iq] [dispatch.core :as id]))

(def fails (atom {}))
(defn try* [f] (try (f) (catch Throwable e (list :threw))))
(defn cmp [nm a b args]
  (let [x (try* a) y (try* b)]
    (when (not= x y) (swap! fails update nm (fnil conj []) {:args args :want x :got y}))
    x))

(defn rand-job [i]
  (let [st (rand-nth [:ready :ready :leased :done :dead])]
    {:id i :priority (rand-int 4) :attempts (rand-int 4) :status st :ready-at (rand-int 12)
     :lease (when (= :leased st) {:worker (rand-int 3) :until (rand-int 20)})}))

(defn rand-queue []
  (let [n (rand-int 6)]
    {:jobs (into {} (for [i (range n)] [i (rand-job i)])) :next-id (+ n (rand-int 2))}))

(defn -main [& _]
  (dotimes [_ 1500]
    (let [q (rand-queue) now (rand-int 20) w (rand-int 3) jid (rand-int 7) p (rand-int 4)]
      (cmp 'enqueue #(rq/enqueue q p now) #(iq/enqueue q p now) [q p now])
      (cmp 'next-job #(rq/next-job q now) #(iq/next-job q now) [q now])
      (cmp 'lease #(rq/lease q w now) #(iq/lease q w now) [q w now])
      (cmp 'complete #(rq/complete q jid w now) #(iq/complete q jid w now) [q jid w now])
      (cmp 'fail #(rq/fail q jid w now) #(iq/fail q jid w now) [q jid w now])
      (cmp 'expire #(rq/expire q now) #(iq/expire q now) [q now])
      (cmp 'backlog #(rd/backlog q now) #(id/backlog q now) [q now])
      (let [ws (vec (repeatedly (rand-int 4) #(rand-int 3)))]
        (cmp 'tick #(rd/tick q ws now) #(id/tick q ws now) [q ws now]))
      ;; a run of ticks with a job added and the first assignment finished between them
      (loop [i 0, r q, m q, t now]
        (when (< i 5)
          (let [ws [0 1 2]
                x (cmp 'tick-run #(rd/tick r ws t) #(id/tick m ws t) [r ws t])
                y (try* #(id/tick m ws t))]
            (when (and (vector? x) (vector? y))
              (let [[r2 as] x
                    m2 (first y)
                    [w2 j2] (first as)
                    r3 (if j2 (rq/complete r2 j2 w2 (inc t)) r2)
                    m3 (if j2 (iq/complete m2 j2 w2 (inc t)) m2)
                    pr (rand-int 4)]
                (recur (inc i) (first (rq/enqueue r3 pr t)) (first (iq/enqueue m3 pr t)) (+ t (rand-int 12))))))))))
  (if (empty? @fails)
    (println "GRADE: every function agrees with the reference")
    (doseq [[f xs] @fails]
      (println "GRADE:" f "disagrees on" (count xs) "inputs, e.g.")
      (prn (first (sort-by #(count (pr-str %)) xs))))))
