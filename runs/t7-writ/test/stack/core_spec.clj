(ns stack.core-spec
  "The contract for stack.core, built on expr.core.

  A program is a vector of instructions; compile-expr writes one in
  postorder, and run executes it on an empty stack, giving the one value
  left or nil when an instruction wants more values than the stack holds,
  or the program ends with other than exactly one value.

  The component's meaning is taken at its own spec: evaluate and simplify
  are expr.core's, and their proved laws are the lemmas used here.  The
  central law is the compiler's: running the program compile-expr builds
  gives the expression's value, for every expression and environment."
  (:require [writ.spec :refer [spec data ann law graph example]]))

(spec stack.core {:require :proved :uses [expr.core-spec]})

(data Instr (push Int) (load Keyword) (add) (mul) (neg))

(ann compile-expr [Expr -> (Vec Instr)])
(ann run [(Vec Instr) (Map Keyword Int) -> (Opt Int)])
(ann compile-simplified [Expr -> (Vec Instr)])

(graph machine
  {:states {:expression Expr, :program (Vec Instr), :value (Opt Int)}
   :edges  {:expression {[compile-expr] #{:program}
                         [compile-simplified] #{:program}}
            :program {[run (Map Keyword Int)] #{:value}}}})

;; The prover does not unfold either recursive fn over its result: for
;; compile-expr it will not conj onto a value not known to be a vector,
;; and for run it will not unroll the loop over a symbolic program.  Each
;; law below is run on 1000+ generated inputs instead.

;; --- the exact program compile-expr writes ------------------------------------

(law a-number-is-pushed
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [n Int] (= (compile-expr [:num n]) [[:push n]])))

(law a-variable-is-loaded
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [k Keyword] (= (compile-expr [:var k]) [[:load k]])))

(law a-sum-appends-its-operator
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [a Expr, b Expr]
    (= (compile-expr [:add a b])
       (conj (into (compile-expr a) (compile-expr b)) [:add]))))

(law a-product-appends-its-operator
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [a Expr, b Expr]
    (= (compile-expr [:mul a b])
       (conj (into (compile-expr a) (compile-expr b)) [:mul]))))

(law a-negation-appends-its-operator
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [a Expr]
    (= (compile-expr [:neg a]) (conj (compile-expr a) [:neg]))))

;; --- run: the value (or nil) of a program -------------------------------------

(law run-a-push
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [n Int, env (Map Keyword Int)] (= (run [[:push n]] env) n)))

(law run-a-load
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [k Keyword, env (Map Keyword Int)]
    (= (run [[:load k]] env) (get env k 0))))

(law run-a-negation
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [n Int, env (Map Keyword Int)]
    (= (run [[:push n] [:neg]] env) (- n))))

(law run-a-sum
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [a Int, b Int, env (Map Keyword Int)]
    (= (run [[:push a] [:push b] [:add]] env) (+ a b))))

(law run-a-product
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [a Int, b Int, env (Map Keyword Int)]
    (= (run [[:push a] [:push b] [:mul]] env) (* a b))))

;; --- every way run can fail ---------------------------------------------------

(law run-an-empty-program-is-nil
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [env (Map Keyword Int)] (= (run [] env) nil)))

(law run-a-sum-with-one-value-is-nil
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [n Int, env (Map Keyword Int)]
    (= (run [[:push n] [:add]] env) nil)))

(law run-a-sum-with-no-values-is-nil
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [env (Map Keyword Int)] (= (run [[:add]] env) nil)))

(law run-a-product-with-one-value-is-nil
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [n Int, env (Map Keyword Int)]
    (= (run [[:push n] [:mul]] env) nil)))

(law run-a-product-with-no-values-is-nil
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [env (Map Keyword Int)] (= (run [[:mul]] env) nil)))

(law run-a-negation-with-no-value-is-nil
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [env (Map Keyword Int)] (= (run [[:neg]] env) nil)))

(law run-a-program-ending-with-two-values-is-nil
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [a Int, b Int, env (Map Keyword Int)]
    (= (run [[:push a] [:push b]] env) nil)))

(law run-a-program-ending-with-two-values-after-a-negation-is-nil
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [a Int, b Int, env (Map Keyword Int)]
    (= (run [[:push a] [:push b] [:neg]] env) nil)))

(law run-a-program-ending-with-a-left-over-value-is-nil
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [a Int, b Int, c Int, env (Map Keyword Int)]
    (= (run [[:push a] [:push b] [:push c] [:add]] env) nil)))

;; --- compile-expr and run together --------------------------------------------

(law running-a-compiled-expression-gives-its-value
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [e Expr, env (Map Keyword Int)]
    (= (run (compile-expr e) env) (expr.core/evaluate e env))))

(law compiling-a-simplified-expression
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [e Expr]
    (= (compile-simplified e) (compile-expr (expr.core/simplify e)))))

(law running-a-simplified-compiled-expression-gives-its-value
  {:require :tested :because "the prover does not unfold compile-expr or run's loop over a program; the law is run on 1000+ inputs"}
  (forall [e Expr, env (Map Keyword Int)]
    (= (run (compile-simplified e) env) (expr.core/evaluate e env))))

;; --- examples -----------------------------------------------------------------

;; Examples for compile-expr and compile-simplified would have the spec
;; alone run them at their arguments, which calls the component
;; expr.core; that is written after the spec, so they wait for the code.
(example run [[[:push 1] [:push 2] [:add]] {}] 3)
(example run [[[:push 5] [:neg]] {}] -5)
