(ns stack.core
  "A small stack machine for arithmetic expressions: compile an
  expression to instructions, and run them.

  Uses expr.core for the meaning of an expression."
  (:require [expr.core :as expr]))

(defn compile-expr
  "The program for e, in postorder."
  [e]
  (case (first e)
    :num (let [[_ n] e] [[:push n]])
    :var (let [[_ k] e] [[:load k]])
    :add (let [[_ a b] e] (conj (into (compile-expr a) (compile-expr b)) [:add]))
    :mul (let [[_ a b] e] (conj (into (compile-expr a) (compile-expr b)) [:mul]))
    :neg (let [[_ a] e] (conj (compile-expr a) [:neg]))))

(defn run
  "Run prog on an empty stack; the one value left, or nil when an
  instruction finds too few values, or the program ends with other than
  exactly one value."
  [prog env]
  (loop [prog prog
         stack []]
    (if (empty? prog)
      (if (= 1 (count stack)) (first stack) nil)
      (let [i (first prog)]
        (case (first i)
          :push (recur (rest prog) (conj stack (second i)))
          :load (recur (rest prog) (conj stack (get env (second i) 0)))
          :add (if (< (count stack) 2)
                 nil
                 (let [n (count stack)]
                   (recur (rest prog)
                          (conj (vec (butlast (butlast stack)))
                                (+ (nth stack (- n 2)) (nth stack (dec n)))))))
          :mul (if (< (count stack) 2)
                 nil
                 (let [n (count stack)]
                   (recur (rest prog)
                          (conj (vec (butlast (butlast stack)))
                                (* (nth stack (- n 2)) (nth stack (dec n)))))))
          :neg (if (empty? stack)
                 nil
                 (let [n (count stack)]
                   (recur (rest prog)
                          (conj (vec (butlast stack)) (- (nth stack (dec n))))))))))))

(defn compile-simplified
  "The program for e's simplified form."
  [e]
  (compile-expr (expr/simplify e)))
