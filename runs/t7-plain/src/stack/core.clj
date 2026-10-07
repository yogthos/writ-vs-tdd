(ns stack.core
  (:require [expr.core :as expr]))

(defn compile-expr
  "The program for e, in postorder."
  [e]
  (case (first e)
    :num [[:push (second e)]]
    :var [[:load (second e)]]
    :add (conj (into (compile-expr (nth e 1)) (compile-expr (nth e 2))) [:add])
    :mul (conj (into (compile-expr (nth e 1)) (compile-expr (nth e 2))) [:mul])
    :neg (conj (compile-expr (nth e 1)) [:neg])))

(defn run
  "Runs program prog on an empty stack with env, returning the one value
  left on the stack at the end. nil when an instruction finds too few
  values on the stack, or when the program ends with other than exactly
  one value on the stack."
  [prog env]
  (loop [prog (seq prog)
         stack []]
    (if (nil? prog)
      (when (= 1 (count stack)) (peek stack))
      (let [instr (first prog)]
        (case (first instr)
          :push (recur (next prog) (conj stack (second instr)))
          :load (recur (next prog) (conj stack (get env (second instr) 0)))
          :add (if (< (count stack) 2)
                 nil
                 (let [b (peek stack)
                       a (peek (pop stack))]
                   (recur (next prog) (conj (pop (pop stack)) (+ a b)))))
          :mul (if (< (count stack) 2)
                 nil
                 (let [b (peek stack)
                       a (peek (pop stack))]
                   (recur (next prog) (conj (pop (pop stack)) (* a b)))))
          :neg (if (< (count stack) 1)
                 nil
                 (recur (next prog) (conj (pop stack) (- (peek stack))))))))))

(defn compile-simplified
  "The program for the simplified e."
  [e]
  (compile-expr (expr/simplify e)))
