(ns stack.core
  "Compile arithmetic expressions to a small stack machine and run them.

  A program is a vector of instructions: [:push n], [:load k], [:add],
  [:mul], [:neg]."
  (:require [expr.core :as expr]))

(defn compile-expr
  "The program for expression e, in postorder.  A [:num n] is [[:push n]],
  a [:var k] is [[:load k]], [:add a b] is the program for a, then b's,
  then [:add] (likewise :mul), and [:neg a] is a's program then [:neg]."
  [e]
  (case (first e)
    :num [[:push (second e)]]
    :var [[:load (second e)]]
    :neg (conj (compile-expr (nth e 1)) [:neg])
    :add (conj (into (compile-expr (nth e 1)) (compile-expr (nth e 2))) [:add])
    :mul (conj (into (compile-expr (nth e 1)) (compile-expr (nth e 2))) [:mul])))

(defn run
  "Run program prog on an empty stack against env.  :push pushes n, :load
  pushes k's value in env (0 when absent), :add/:mul pop two values and push
  their sum/product, :neg pops one and pushes its negation.  The result is
  the one value left on the stack at the end, or nil when an instruction
  finds too few values on the stack, or when the program ends with other
  than exactly one value on the stack."
  [prog env]
  (loop [stack []
         instrs prog]
    (if (empty? instrs)
      (when (= 1 (count stack))
        (peek stack))
      (let [instr (first instrs)
            stack (case (first instr)
                    :push (conj stack (second instr))
                    :load (conj stack (get env (second instr) 0))
                    :add  (if (>= (count stack) 2)
                            (let [b (peek stack)
                                  a (peek (pop stack))]
                              (conj (pop (pop stack)) (+ a b)))
                            ::fail)
                    :mul  (if (>= (count stack) 2)
                            (let [b (peek stack)
                                  a (peek (pop stack))]
                              (conj (pop (pop stack)) (* a b)))
                            ::fail)
                    :neg  (if (>= (count stack) 1)
                            (conj (pop stack) (- (peek stack)))
                            ::fail))]
        (if (= ::fail stack)
          nil
          (recur stack (rest instrs)))))))

(defn compile-simplified
  "The program for e after expr.core/simplify."
  [e]
  (compile-expr (expr/simplify e)))
