(ns stack.core-test
  "Tests for stack.core: compile-expr, run (including every failure mode)
  and compile-simplified, plus that running a compiled expression gives the
  expression's value."
  (:require [clojure.test :refer [deftest is testing are]]
            [expr.core :as e]
            [stack.core :as s]))

;; ---------------------------------------------------------------------------
;; Sample expressions and environments.  Same deterministic enumeration as
;; expr.core-test, duplicated so this namespace stands alone.

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
;; compile-expr: the exact program, in postorder

(deftest compile-expr-leaves
  (is (= [[:push 5]] (s/compile-expr [:num 5])))
  (is (= [[:push -3]] (s/compile-expr [:num -3])))
  (is (= [[:load :x]] (s/compile-expr [:var :x])))
  (is (= [[:load :answer]] (s/compile-expr [:var :answer]))))

(deftest compile-expr-operators
  (testing "binary operators emit a, then b, then the operator"
    (is (= [[:push 1] [:push 2] [:add]]
           (s/compile-expr [:add [:num 1] [:num 2]])))
    (is (= [[:push 1] [:push 2] [:mul]]
           (s/compile-expr [:mul [:num 1] [:num 2]])))
    (is (= [[:push 2] [:push 1] [:add]]
           (s/compile-expr [:add [:num 2] [:num 1]]))))
  (testing "neg emits its operand's program then :neg"
    (is (= [[:push 3] [:neg]]
           (s/compile-expr [:neg [:num 3]])))
    (is (= [[:load :x] [:neg]]
           (s/compile-expr [:neg [:var :x]])))))

(deftest compile-expr-nested
  (is (= [[:push 1] [:load :x] [:mul] [:push 2] [:neg] [:add]]
         (s/compile-expr [:add [:mul [:num 1] [:var :x]]
                          [:neg [:num 2]]])))
  (is (= [[:push 1] [:push 2] [:add] [:push 3] [:mul]]
         (s/compile-expr [:mul [:add [:num 1] [:num 2]] [:num 3]])))
  (is (= [[:load :x] [:load :y] [:add] [:load :z] [:mul]]
         (s/compile-expr [:mul [:add [:var :x] [:var :y]] [:var :z]])))
  (is (= [[:push 1] [:push 2] [:add] [:neg] [:push 3] [:mul]]
         (s/compile-expr [:mul [:neg [:add [:num 1] [:num 2]]] [:num 3]]))))

;; ---------------------------------------------------------------------------
;; run: successes

(deftest run-pushes-and-loads
  (is (= 5 (s/run [[:push 5]] {})))
  (is (= -3 (s/run [[:push -3]] {})))
  (is (= 7 (s/run [[:load :x]] {:x 7})))
  (is (= 0 (s/run [[:load :x]] {})))
  (is (= 0 (s/run [[:load :x]] {:y 3}))))

(deftest run-arithmetic
  (is (= 3 (s/run [[:push 1] [:push 2] [:add]] {})))
  (is (= 6 (s/run [[:push 2] [:push 3] [:mul]] {})))
  (is (= -3 (s/run [[:push 3] [:neg]] {})))
  (is (= -3 (s/run [[:push 1] [:push 2] [:add] [:neg]] {})))
  (is (= 7 (s/run [[:push 2] [:push 5] [:mul] [:push 3] [:neg] [:add]] {})))
  (is (= -12 (s/run [[:push 1] [:push 2] [:add] [:push 4] [:neg] [:mul]] {})))
  (is (= 10 (s/run [[:load :x] [:load :y] [:add]] {:x 4 :y 6})))
  (is (= 0 (s/run [[:load :x] [:push 2] [:mul]] {:y 9}))))

;; ---------------------------------------------------------------------------
;; run: every way it can fail

(deftest run-fails-with-too-few-values
  (testing "an empty program leaves zero values"
    (is (nil? (s/run [] {}))))
  (testing ":add, :mul and :neg find too few values"
    (is (nil? (s/run [[:add]] {})))
    (is (nil? (s/run [[:mul]] {})))
    (is (nil? (s/run [[:neg]] {})))
    (is (nil? (s/run [[:push 1] [:add]] {})))
    (is (nil? (s/run [[:push 1] [:mul]] {})))
    (is (nil? (s/run [[:load :x] [:add]] {})))
    (is (nil? (s/run [[:load :x] [:mul]] {}))))
  (testing "only one value is left when the failing instruction runs"
    (is (nil? (s/run [[:push 1] [:neg] [:add]] {})))
    (is (nil? (s/run [[:push 1] [:push 2] [:add] [:neg] [:neg] [:mul]] {})))))

(deftest run-fails-with-wrong-final-stack
  (testing "more than one value left at the end"
    (is (nil? (s/run [[:push 1] [:push 2]] {})))
    (is (nil? (s/run [[:push 1] [:push 2] [:push 3] [:mul]] {})))
    (is (nil? (s/run [[:push 1] [:push 2] [:add] [:push 3]] {})))
    (is (nil? (s/run [[:push 1] [:push 2] [:add]
                      [:push 3] [:push 4] [:mul] [:add] [:push 5]] {})))))

;; ---------------------------------------------------------------------------
;; compile-simplified

(deftest compile-simplified-uses-simplify
  (is (= [[:load :x]] (s/compile-simplified [:add [:num 0] [:var :x]])))
  (is (= [[:load :x]] (s/compile-simplified [:add [:var :x] [:num 0]])))
  (is (= [[:push 2]] (s/compile-simplified [:mul [:num 1] [:num 2]])))
  (is (= [[:push 3]]
         (s/compile-simplified [:add [:add [:num 1] [:num 2]] [:num 0]])))
  (is (= [[:push 5]] (s/compile-simplified [:neg [:num -5]])))
  (is (= [[:push 0]] (s/compile-simplified [:mul [:num 0] [:var :x]]))))

;; ---------------------------------------------------------------------------
;; running a compiled expression gives its value

(deftest compile-run-matches-evaluate
  (doseq [expr sample-exprs
          env  envs]
    (is (= (e/evaluate expr env)
           (s/run (s/compile-expr expr) env))
        (str "compile/run mismatch for " (pr-str expr)
             " under " (pr-str env)))))

(deftest compile-expr-always-produces-one-value
  (doseq [expr sample-exprs
          env  envs]
    (is (integer? (s/run (s/compile-expr expr) env)))))

(deftest compile-simplified-run-matches-evaluate
  (doseq [expr sample-exprs
          env  envs]
    (is (= (e/evaluate expr env)
           (s/run (s/compile-simplified expr) env))
        (str "simplified compile/run mismatch for " (pr-str expr)
             " under " (pr-str env)))))
