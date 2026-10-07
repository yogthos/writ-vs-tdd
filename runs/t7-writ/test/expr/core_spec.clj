(ns expr.core-spec
  "The contract for expr.core, the pure arithmetic-expression library:

  - the shape of an expression, as tagged data;
  - what an expression evaluates to under an environment, where a
    variable the environment lacks is 0;
  - the meaning of simplify: its parts are simplified first, then the
    first matching rule is applied once, rules 1-5 in the order of the
    problem statement, so a node of two numbers is combined before the
    0/1/0 rules can look at it;
  - the node count, and the distinct keywords mentioned, sorted.

  The rules' order is said twice: the rules themselves, and closed laws
  that only hold when the first matching rule wins."
  (:require [writ.spec :refer [spec data ann law graph example]]))

(spec expr.core {:require :proved})

(data Expr (num Int) (var Keyword) (add Expr Expr) (mul Expr Expr) (neg Expr))

(ann evaluate  [Expr (Map Keyword Int) -> Int])
(ann simplify  [Expr -> Expr])
(ann size      [Expr -> Nat])
(ann variables [Expr -> (Vec Keyword)])

(graph expressions
  {:states {:expression Expr, :value Int, :count Nat, :names (Vec Keyword)}
   :edges  {:expression {[evaluate (Map Keyword Int)] #{:value}
                         [simplify] #{:expression}
                         [size] #{:count}
                         [variables] #{:names}}}})

;; --- evaluate -----------------------------------------------------------------

(law evaluate-a-number
  (forall [n Int, env (Map Keyword Int)]
    (= (evaluate [:num n] env) n)))

(law evaluate-a-variable
  (forall [k Keyword, env (Map Keyword Int)]
    (= (evaluate [:var k] env) (get env k 0))))

(law evaluate-an-absent-variable-is-zero
  (forall [k Keyword, env (Map Keyword Int)]
    (=> (not (contains? env k)) (= (evaluate [:var k] env) 0))))

(law evaluate-a-sum
  (forall [a Expr, b Expr, env (Map Keyword Int)]
    (= (evaluate [:add a b] env) (+ (evaluate a env) (evaluate b env)))))

(law evaluate-a-product
  (forall [a Expr, b Expr, env (Map Keyword Int)]
    (= (evaluate [:mul a b] env) (* (evaluate a env) (evaluate b env)))))

(law evaluate-a-negation
  (forall [a Expr, env (Map Keyword Int)]
    (= (evaluate [:neg a] env) (- (evaluate a env)))))

;; --- the spec's own vocabulary for the simplifier -----------------------------

(defn num-of?
  "Is e a [:num n] with that n?"
  [e n]
  (and (= :num (first e)) (= n (second e))))

(defn simplified?
  "No rule of simplify applies anywhere in e: every number combining has
  happened, no 0 or 1 operand is left under an add or mul, and a negation
  is of neither a number nor another negation."
  [e]
  (case (first e)
    :num true
    :var true
    :add (let [[_ a b] e]
           (and (simplified? a) (simplified? b)
                (not (and (= :num (first a)) (= :num (first b))))
                (not (num-of? a 0)) (not (num-of? b 0))))
    :mul (let [[_ a b] e]
           (and (simplified? a) (simplified? b)
                (not (and (= :num (first a)) (= :num (first b))))
                (not (num-of? a 1)) (not (num-of? b 1))
                (not (num-of? a 0)) (not (num-of? b 0))))
    :neg (let [[_ a] e]
           (and (simplified? a) (not= :num (first a)) (not= :neg (first a))))))

;; --- simplify: parts first, then the first matching rule once ------------------

(law simplify-a-number
  (forall [n Int] (= (simplify [:num n]) [:num n])))

(law simplify-a-variable
  (forall [k Keyword] (= (simplify [:var k]) [:var k])))

;; rule 1
(law combining-numbers
  (forall [a Int, b Int]
    (= (simplify [:add [:num a] [:num b]]) [:num (+ a b)])))

(law combining-numbers-by-multiplying
  (forall [a Int, b Int]
    (= (simplify [:mul [:num a] [:num b]]) [:num (* a b)])))

(law negating-a-number
  (forall [a Int] (= (simplify [:neg [:num a]]) [:num (- a)])))

;; rule 2
(law add-zero-on-the-left
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [x Expr] (= (simplify [:add [:num 0] x]) (simplify x))))

(law add-zero-on-the-right
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [x Expr] (= (simplify [:add x [:num 0]]) (simplify x))))

;; rule 3
(law multiply-by-one-on-the-left
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [x Expr] (= (simplify [:mul [:num 1] x]) (simplify x))))

(law multiply-by-one-on-the-right
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [x Expr] (= (simplify [:mul x [:num 1]]) (simplify x))))

;; rule 4
(law multiply-by-zero-on-the-left
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [x Expr] (= (simplify [:mul [:num 0] x]) [:num 0])))

(law multiply-by-zero-on-the-right
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [x Expr] (= (simplify [:mul x [:num 0]]) [:num 0])))

;; rule 5
(law two-negations-cancel
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [x Expr] (= (simplify [:neg [:neg x]]) (simplify x))))

;; the rules composed: simplify's parts, then the first matching rule once
(law simplify-a-sum
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [a Expr, b Expr]
    (= (simplify [:add a b])
       (let [a (simplify a)
             b (simplify b)]
         (cond
           (and (= :num (first a)) (= :num (first b))) [:num (+ (second a) (second b))]
           (and (= :num (first a)) (= 0 (second a))) b
           (and (= :num (first b)) (= 0 (second b))) a
           :else [:add a b])))))

(law simplify-a-product
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [a Expr, b Expr]
    (= (simplify [:mul a b])
       (let [a (simplify a)
             b (simplify b)]
         (cond
           (and (= :num (first a)) (= :num (first b))) [:num (* (second a) (second b))]
           (and (= :num (first a)) (= 1 (second a))) b
           (and (= :num (first b)) (= 1 (second b))) a
           (or (and (= :num (first a)) (= 0 (second a)))
               (and (= :num (first b)) (= 0 (second b)))) [:num 0]
           :else [:mul a b])))))

(law simplify-a-negation
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [a Expr]
    (= (simplify [:neg a])
       (let [a (simplify a)]
         (cond
           (= :num (first a)) [:num (- (second a))]
           (= :neg (first a)) (second a)
           :else [:neg a])))))

;; the parts are simplified before the node's rule looks at them
(law simplify-simplifies-the-parts-first
  (= (simplify [:add [:mul [:num 2] [:num 3]] [:num 0]]) [:num 6]))

(law simplify-simplifies-the-parts-first-under-a-one
  (= (simplify [:mul [:add [:num 1] [:num 2]] [:num 1]]) [:num 3]))

;; rule 1 wins over the 0/1/0 rules when both operands are numbers
(law combining-numbers-beats-adding-zero
  (= (simplify [:add [:num 0] [:num 5]]) [:num 5]))

(law combining-numbers-beats-multiplying-by-one
  (= (simplify [:mul [:num 1] [:num 7]]) [:num 7]))

(law combining-numbers-beats-multiplying-by-zero
  (= (simplify [:mul [:num 0] [:num 7]]) [:num 0]))

(law combining-numbers-beats-multiplying-by-zero-the-other-way
  (= (simplify [:mul [:num 7] [:num 0]]) [:num 0]))

;; the inner number is negated first, so the outer negation sees a number
(law a-double-negation-of-a-number-is-the-number
  (= (simplify [:neg [:neg [:num 5]]]) [:num 5]))

;; a node no rule matches keeps its simplified parts
(law a-node-no-rule-matches-stays
  (= (simplify [:add [:var :x] [:var :y]]) [:add [:var :x] [:var :y]]))

(law a-mixed-node-keeps-what-simplifies-to-non-numbers
  (= (simplify [:mul [:var :x] [:neg [:num 3]]]) [:mul [:var :x] [:num -3]]))

;; simplify leaves a normal form
(law simplify-makes-a-normal-form
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [e Expr] (simplified? (simplify e))))

(law simplify-leaves-a-normal-form
  (forall [e Expr] (=> (simplified? e) (= (simplify e) e))))

(law simplifying-twice-is-simplifying-once
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [e Expr] (= (simplify (simplify e)) (simplify e))))

;; simplification never changes a value
(law simplifying-keeps-the-value
  {:require :tested :because "the prover's search over simplify's case/cond runs out of fuel; the law is run on 1000+ inputs"}
  (forall [e Expr, env (Map Keyword Int)]
    (= (evaluate (simplify e) env) (evaluate e env))))

;; --- size ---------------------------------------------------------------------

(law size-of-a-number (forall [n Int] (= (size [:num n]) 1)))
(law size-of-a-variable (forall [k Keyword] (= (size [:var k]) 1)))

(law size-of-a-sum
  (forall [a Expr, b Expr] (= (size [:add a b]) (+ 1 (size a) (size b)))))

(law size-of-a-product
  (forall [a Expr, b Expr] (= (size [:mul a b]) (+ 1 (size a) (size b)))))

(law size-of-a-negation
  (forall [a Expr] (= (size [:neg a]) (+ 1 (size a)))))

;; --- variables ----------------------------------------------------------------

(law variables-of-a-number (forall [n Int] (= (variables [:num n]) [])))

(law variables-of-a-variable (forall [k Keyword] (= (variables [:var k]) [k])))

(law variables-of-a-negation
  (forall [a Expr] (= (variables [:neg a]) (variables a))))

(law variables-of-a-sum
  (forall [a Expr, b Expr]
    (= (variables [:add a b])
       (vec (sort (distinct (concat (variables a) (variables b))))))))

(law variables-of-a-product
  (forall [a Expr, b Expr]
    (= (variables [:mul a b])
       (vec (sort (distinct (concat (variables a) (variables b))))))))

(law variables-are-in-order
  (= (variables [:add [:var :b] [:var :a]]) [:a :b]))

(law variables-are-distinct
  (= (variables [:add [:var :a] [:var :a]]) [:a]))

(law variables-of-a-mixed-expression
  (= (variables [:mul [:neg [:var :z]] [:add [:var :a] [:num 4]]]) [:a :z]))

;; --- examples the problem statement works through -----------------------------

(example evaluate [[:add [:num 1] [:num 2]] {}] 3)
(example evaluate [[:mul [:var :x] [:num 4]] {:x 3}] 12)
(example simplify [[:add [:num 1] [:num 2]]] [:num 3])
(example simplify [[:mul [:num 0] [:var :x]]] [:num 0])
(example size [[:add [:num 1] [:var :x]]] 3)
(example variables [[:add [:var :b] [:var :a]]] [:a :b])
