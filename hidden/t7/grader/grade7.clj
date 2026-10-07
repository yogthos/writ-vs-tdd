(ns grade7
  (:require [ref.expr :as re] [ref.stack :as rs] [expr.core :as ie] [stack.core :as is]))

(def fails (atom {}))
(defn try* [f] (try (f) (catch Throwable e (list :threw))))
(defn cmp [nm a b args]
  (let [x (try* a) y (try* b)]
    (when (not= x y) (swap! fails update nm (fnil conj []) {:args args :want x :got y}))
    x))

(defn rand-expr [d]
  (if (or (zero? d) (zero? (rand-int 3)))
    (if (zero? (rand-int 2)) [:num (- (rand-int 5) 2)] [:var (rand-nth [:x :y :z])])
    (case (rand-int 3)
      0 [:add (rand-expr (dec d)) (rand-expr (dec d))]
      1 [:mul (rand-expr (dec d)) (rand-expr (dec d))]
      2 [:neg (rand-expr (dec d))])))

(defn rand-prog []
  (vec (repeatedly (rand-int 6)
                   #(rand-nth [[:push (- (rand-int 7) 3)] [:load (rand-nth [:x :y :w])] [:add] [:mul] [:neg]]))))

(defn -main [& _]
  (dotimes [_ 3000]
    (let [e (rand-expr (rand-int 5))
          env (into {} (filter (fn [_] (pos? (rand-int 3))) {:x (- (rand-int 9) 4) :y (- (rand-int 9) 4)}))
          p (rand-prog)]
      (cmp 'evaluate #(re/evaluate e env) #(ie/evaluate e env) [e env])
      (cmp 'simplify #(re/simplify e) #(ie/simplify e) [e])
      (cmp 'size #(re/size e) #(ie/size e) [e])
      (cmp 'variables #(re/variables e) #(ie/variables e) [e])
      (cmp 'compile-expr #(rs/compile-expr e) #(is/compile-expr e) [e])
      (cmp 'compile-simplified #(rs/compile-simplified e) #(is/compile-simplified e) [e])
      (cmp 'run-compiled #(rs/run (rs/compile-expr e) env) #(is/run (rs/compile-expr e) env) [e env])
      (cmp 'run #(rs/run p env) #(is/run p env) [p env])))
  (if (empty? @fails)
    (println "GRADE: every function agrees with the reference")
    (doseq [[f xs] @fails]
      (println "GRADE:" f "disagrees on" (count xs) "inputs, e.g.")
      (prn (first (sort-by #(count (pr-str %)) xs))))))
