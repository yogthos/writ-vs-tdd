(ns stack.k03
  "Mutant: an absent variable loads nil"
  (:require [expr.core :as expr]))

(defn compile-expr [e]
  (case (first e)
    :num [[:push (second e)]]
    :var [[:load (second e)]]
    (:add :mul) (into (into (compile-expr (nth e 1)) (compile-expr (nth e 2))) [[(first e)]])
    :neg (conj (compile-expr (nth e 1)) [:neg])))

(defn- exec [stack [op x] env]
  (case op
    :push (conj stack x)
    :load (conj stack (get env x))
    (:add :mul) (when (<= 2 (count stack))
                  (let [b (peek stack) a (peek (pop stack)) s (pop (pop stack))]
                    (conj s (if (= :add op) (+ a b) (* a b)))))
    :neg (when (<= 1 (count stack))
           (conj (pop stack) (- (peek stack))))))

(defn run [prog env]
  (let [stack (reduce (fn [s ins] (if s (exec s ins env) (reduced nil))) [] prog)]
    (when (= 1 (count stack)) (peek stack))))

(defn compile-simplified [e] (compile-expr (expr/simplify e)))
