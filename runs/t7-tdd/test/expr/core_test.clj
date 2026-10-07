(ns expr.core-test
  "Tests for expr.core: the shape of an expression, evaluate, simplify
  (each rule, rule order, bottom-up evaluation), size and variables."
  (:require [clojure.test :refer [deftest is testing are]]
            [expr.core :as e]))

;; ---------------------------------------------------------------------------
;; Sample expressions and environments, used for the property-style checks.
;;
;; `exprs-of-size` enumerates every expression of a given node count over a
;; small set of numbers and variables.  Enumerating (rather than generating
;; randomly) keeps the tests deterministic.

(def ^:private nums [0 1 2 -1])
(def ^:private vars [:x :y])

(defn- exprs-of-size
  "All expressions with exactly n nodes over nums and vars."
  [n]
  (if (= n 1)
    (concat (map (fn [x] [:num x]) nums)
            (map (fn [k] [:var k]) vars))
    (concat
     (map (fn [a] [:neg a]) (exprs-of-size (dec n)))
     (for [i (range 1 n)
           j (range 1 n)
           :when (= (+ i j) (dec n))
           op [:add :mul]
           a (exprs-of-size i)
           b (exprs-of-size j)]
       [op a b]))))

(def ^:private sample-exprs
  (vec (mapcat exprs-of-size (range 1 4))))

(def ^:private envs
  [{} {:x 0 :y 0} {:x 1 :y 2} {:x -3 :y 5} {:y -4}])

;; ---------------------------------------------------------------------------
;; Shape and evaluation

(deftest evaluate-numbers
  (are [expr val] (= val (e/evaluate expr {}))
    [:num 0]      0
    [:num 5]      5
    [:num -7]     -7
    [:num 42]     42))

(deftest evaluate-variables
  (testing "a variable present in env evaluates to its value"
    (is (= 7 (e/evaluate [:var :x] {:x 7})))
    (is (= -3 (e/evaluate [:var :y] {:y -3})))
    (is (= 7 (e/evaluate [:var :x] {:x 7 :y 9}))))
  (testing "a variable absent from env evaluates to 0"
    (is (= 0 (e/evaluate [:var :x] {})))
    (is (= 0 (e/evaluate [:var :x] {:y 4})))
    (is (= 0 (e/evaluate [:var :missing] {:x 1}))))
  (testing "different keywords are looked up independently"
    (is (= 1 (e/evaluate [:var :x] {:x 1 :y 2})))
    (is (= 2 (e/evaluate [:var :y] {:x 1 :y 2})))))

(deftest evaluate-operators
  (are [expr val] (= val (e/evaluate expr {}))
    [:add [:num 3] [:num 4]]                        7
    [:mul [:num 3] [:num 4]]                        12
    [:neg [:num 3]]                                 -3
    [:neg [:num -3]]                                3
    [:neg [:num 0]]                                 0
    [:add [:num -2] [:num 5]]                       3
    [:mul [:num -2] [:num 5]]                       -10
    [:add [:mul [:num 2] [:num 5]] [:neg [:num 3]]] 7
    [:mul [:add [:num 1] [:num 2]] [:neg [:num 4]]] -12
    [:neg [:add [:num 1] [:num 2]]]                 -3
    [:neg [:neg [:num 4]]]                          4))

(deftest evaluate-with-env
  (let [env {:x 3 :y 4}]
    (is (= 2 (e/evaluate [:add [:mul [:num 2] [:var :x]]
                          [:neg [:var :y]]]
                         env)))
    (is (= 0 (e/evaluate [:add [:var :z] [:var :missing]] env)))
    (is (= 12 (e/evaluate [:mul [:var :x] [:var :y]] env)))
    (is (= -3 (e/evaluate [:neg [:var :x]] env)))))

;; ---------------------------------------------------------------------------
;; simplify: rule 1, constant folding

(deftest simplify-rule-1-constant-folding
  (testing "[:add [:num a] [:num b]] folds"
    (is (= [:num 5] (e/simplify [:add [:num 2] [:num 3]])))
    (is (= [:num -1] (e/simplify [:add [:num 2] [:num -3]]))))
  (testing "[:mul [:num a] [:num b]] folds"
    (is (= [:num 6] (e/simplify [:mul [:num 2] [:num 3]])))
    (is (= [:num -6] (e/simplify [:mul [:num -2] [:num 3]]))))
  (testing "[:neg [:num a]] folds"
    (is (= [:num -5] (e/simplify [:neg [:num 5]])))
    (is (= [:num 5] (e/simplify [:neg [:num -5]])))
    (is (= [:num 0] (e/simplify [:neg [:num 0]]))))
  (testing "rule 1 beats the identity rules when both operands are numbers"
    (is (= [:num 5] (e/simplify [:add [:num 0] [:num 5]])))
    (is (= [:num 6] (e/simplify [:mul [:num 1] [:num 6]])))
    (is (= [:num 0] (e/simplify [:mul [:num 0] [:num 7]])))))

;; ---------------------------------------------------------------------------
;; simplify: rules 2-5

(deftest simplify-rule-2-add-zero
  (is (= [:var :x] (e/simplify [:add [:num 0] [:var :x]])))
  (is (= [:var :x] (e/simplify [:add [:var :x] [:num 0]])))
  (is (= [:mul [:var :x] [:var :y]]
         (e/simplify [:add [:num 0] [:mul [:var :x] [:var :y]]])))
  (is (= [:mul [:var :x] [:var :y]]
         (e/simplify [:add [:mul [:var :x] [:var :y]] [:num 0]]))))

(deftest simplify-rule-3-mul-one
  (is (= [:var :x] (e/simplify [:mul [:num 1] [:var :x]])))
  (is (= [:var :x] (e/simplify [:mul [:var :x] [:num 1]])))
  (is (= [:add [:var :x] [:var :y]]
         (e/simplify [:mul [:num 1] [:add [:var :x] [:var :y]]])))
  (is (= [:add [:var :x] [:var :y]]
         (e/simplify [:mul [:add [:var :x] [:var :y]] [:num 1]]))))

(deftest simplify-rule-4-mul-zero
  (is (= [:num 0] (e/simplify [:mul [:num 0] [:var :x]])))
  (is (= [:num 0] (e/simplify [:mul [:var :x] [:num 0]])))
  (is (= [:num 0] (e/simplify [:mul [:num 0] [:add [:var :x] [:var :y]]])))
  (is (= [:num 0] (e/simplify [:mul [:add [:var :x] [:var :y]] [:num 0]]))))

(deftest simplify-rule-5-double-negation
  (is (= [:var :x] (e/simplify [:neg [:neg [:var :x]]])))
  (is (= [:add [:var :x] [:var :y]]
         (e/simplify [:neg [:neg [:add [:var :x] [:var :y]]]])))
  (is (= [:mul [:var :x] [:num 2]]
         (e/simplify [:neg [:neg [:mul [:var :x] [:num 2]]]]))))

(deftest simplify-leaves-unmatched-nodes-alone
  (testing "a node no rule matches keeps its simplified parts"
    (are [expr] (= expr (e/simplify expr))
      [:num 3]
      [:var :x]
      [:neg [:var :x]]
      [:add [:var :x] [:var :y]]
      [:mul [:var :x] [:var :y]]
      [:add [:var :x] [:mul [:var :y] [:var :z]]]
      [:mul [:add [:var :x] [:var :y]] [:neg [:var :z]]])))

;; ---------------------------------------------------------------------------
;; simplify: bottom-up behaviour

(deftest simplify-is-bottom-up
  (testing "the parts of a node are simplified before its rule is chosen"
    (is (= [:num 3] (e/simplify [:add [:add [:num 1] [:num 2]] [:num 0]])))
    (is (= [:num 9] (e/simplify [:add [:num 0] [:add [:num 4] [:num 5]]])))
    (is (= [:var :x] (e/simplify [:mul [:num 1] [:add [:num 0] [:var :x]]])))
    (is (= [:num -3] (e/simplify [:neg [:add [:num 1] [:num 2]]])))
    (is (= [:num 3] (e/simplify [:neg [:neg [:num 3]]])))
    (is (= [:num 6] (e/simplify [:mul [:add [:num 1] [:num 2]] [:num 2]])))
    (is (= [:num 0] (e/simplify [:mul [:add [:num 1] [:num 2]]
                                 [:add [:num -3] [:num 3]]]))))
  (testing "the result is a fixed point: simplifying again changes nothing"
    (doseq [expr sample-exprs]
      (is (= (e/simplify expr) (e/simplify (e/simplify expr)))
          (str "not a fixed point: " (pr-str expr))))))

(deftest simplify-preserves-value
  (testing "simplify never changes an expression's value under any env"
    (doseq [expr sample-exprs
            env  envs]
      (is (= (e/evaluate expr env)
             (e/evaluate (e/simplify expr) env))
          (str "simplify changed value of " (pr-str expr)
               " under " (pr-str env))))))

;; ---------------------------------------------------------------------------
;; size

(deftest size-counts-nodes
  (are [expr n] (= n (e/size expr))
    [:num 3]                                        1
    [:var :x]                                       1
    [:neg [:num 3]]                                 2
    [:add [:num 1] [:var :x]]                       3
    [:mul [:num 1] [:var :x]]                       3
    [:neg [:add [:num 1] [:num 2]]]                 4
    [:add [:mul [:num 1] [:num 2]] [:neg [:num 3]]] 6
    [:mul [:add [:num 1] [:var :x]] [:neg [:var :y]]] 6))

(deftest size-is-a-count
  (is (integer? (e/size [:num 1])))
  (is (integer? (e/size [:var :a]))))

;; ---------------------------------------------------------------------------
;; variables

(deftest variables-distinct-sorted-vector
  (is (= [] (e/variables [:num 3])))
  (is (= [:x] (e/variables [:var :x])))
  (is (= [:a :b] (e/variables [:add [:var :b] [:var :a]])))
  (is (= [:x] (e/variables [:add [:var :x] [:var :x]])))
  (is (= [:a :m :z]
         (e/variables [:mul [:var :z] [:add [:var :a] [:neg [:var :m]]]])))
  (is (= [:x :y]
         (e/variables [:add [:mul [:var :y] [:num 2]] [:neg [:var :x]]])))
  (testing "the result is a vector"
    (is (vector? (e/variables [:var :x])))
    (is (vector? (e/variables [:num 1])))))
