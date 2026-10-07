(ns stack.z2
  "Deep mutant: compile-expr folds the negation of a number into one push"
  (:require [expr.core :as expr]))

(defn compile-expr [e]
  (case (first e)
    :num [[:push (second e)]]
    :var [[:load (second e)]]
    (:add :mul) (into (into (compile-expr (nth e 1)) (compile-expr (nth e 2))) [[(first e)]])
    :neg (if (= :num (first (nth e 1))) [[:push (- (second (nth e 1)))]] (conj (compile-expr (nth e 1)) [:neg]))))

(defn- exec [stack [op x] env]
  (case op
    :push (conj stack x)
    :load (conj stack (get env x 0))
    (:add :mul) (when (<= 2 (count stack))
                  (let [b (peek stack) a (peek (pop stack)) s (pop (pop stack))]
                    (conj s (if (= :add op) (+ a b) (* a b)))))
    :neg (when (<= 1 (count stack))
           (conj (pop stack) (- (peek stack))))))

(defn run [prog env]
  (let [stack (reduce (fn [s ins] (if s (exec s ins env) (reduced nil))) [] prog)]
    (when (= 1 (count stack)) (peek stack))))

(defn compile-simplified [e] (compile-expr (expr/simplify e)))
