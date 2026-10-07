(ns expr.core
  "Evaluate and simplify arithmetic expressions.

  An expression is one of

      [:num n]    n an Int
      [:var k]    k a Keyword
      [:add a b]  a and b expressions
      [:mul a b]
      [:neg a]")

(defn evaluate
  "The Int value of expression e, where env maps keywords to Ints; a
  variable env does not have is 0."
  [e env]
  (case (first e)
    :num (second e)
    :var (get env (second e) 0)
    :add (+ (evaluate (nth e 1) env) (evaluate (nth e 2) env))
    :mul (* (evaluate (nth e 1) env) (evaluate (nth e 2) env))
    :neg (- (evaluate (nth e 1) env))))

(defn- simplify-neg
  [a]
  (cond
    (= :num (first a)) [:num (- (second a))]   ; rule 1
    (= :neg (first a)) (second a)              ; rule 5
    :else [:neg a]))

(defn- simplify-add
  [a b]
  (cond
    (and (= :num (first a)) (= :num (first b)))
    [:num (+ (second a) (second b))]           ; rule 1

    (and (= :num (first a)) (zero? (second a))) b ; rule 2, left
    (and (= :num (first b)) (zero? (second b))) a ; rule 2, right

    :else [:add a b]))

(defn- simplify-mul
  [a b]
  (cond
    (and (= :num (first a)) (= :num (first b)))
    [:num (* (second a) (second b))]           ; rule 1

    (and (= :num (first a)) (= 1 (second a))) b ; rule 3, left
    (and (= :num (first b)) (= 1 (second b))) a ; rule 3, right

    (and (= :num (first a)) (zero? (second a))) [:num 0] ; rule 4, left
    (and (= :num (first b)) (zero? (second b))) [:num 0] ; rule 4, right

    :else [:mul a b]))

(defn simplify
  "Simplify e bottom-up: simplify the parts of a node first, then apply the
  first of the rules that matches the node with its simplified parts, once.
  A node no rule matches stays as it is, with its simplified parts."
  [e]
  (case (first e)
    :num e
    :var e
    :neg (simplify-neg (simplify (nth e 1)))
    :add (simplify-add (simplify (nth e 1)) (simplify (nth e 2)))
    :mul (simplify-mul (simplify (nth e 1)) (simplify (nth e 2)))))

(defn size
  "The number of nodes in e."
  [e]
  (case (first e)
    :num 1
    :var 1
    :neg (inc (size (nth e 1)))
    :add (+ 1 (size (nth e 1)) (size (nth e 2)))
    :mul (+ 1 (size (nth e 1)) (size (nth e 2)))))

(defn- mentioned
  "Every keyword e mentions, with duplicates."
  [e]
  (case (first e)
    :num []
    :var [(second e)]
    :neg (mentioned (nth e 1))
    :add (concat (mentioned (nth e 1)) (mentioned (nth e 2)))
    :mul (concat (mentioned (nth e 1)) (mentioned (nth e 2)))))

(defn variables
  "The vector of the distinct keywords e mentions, sorted."
  [e]
  (vec (sort (set (mentioned e)))))
