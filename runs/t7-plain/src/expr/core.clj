(ns expr.core)

(defn evaluate
  "The Int value of expression e, where env maps keywords to Ints.
  A variable env does not have is 0."
  [e env]
  (case (first e)
    :num (second e)
    :var (get env (second e) 0)
    :add (+ (evaluate (nth e 1) env) (evaluate (nth e 2) env))
    :mul (* (evaluate (nth e 1) env) (evaluate (nth e 2) env))
    :neg (- (evaluate (nth e 1) env))))

(defn simplify
  "Simplifies e bottom-up: the parts of a node are simplified first, then
  the first matching rule is applied to the node once."
  [e]
  (let [op (first e)]
    (cond
      (= op :neg)
      (let [a (simplify (nth e 1))]
        (cond
          (= :num (first a)) [:num (- (second a))]
          (= :neg (first a)) (second a)
          :else [:neg a]))

      (= op :add)
      (let [a (simplify (nth e 1))
            b (simplify (nth e 2))]
        (cond
          (and (= :num (first a)) (= :num (first b)))
          [:num (+ (second a) (second b))]

          (and (= :num (first a)) (zero? (second a))) b
          (and (= :num (first b)) (zero? (second b))) a
          :else [:add a b]))

      (= op :mul)
      (let [a (simplify (nth e 1))
            b (simplify (nth e 2))]
        (cond
          (and (= :num (first a)) (= :num (first b)))
          [:num (* (second a) (second b))]

          (and (= :num (first a)) (= 1 (second a))) b
          (and (= :num (first b)) (= 1 (second b))) a

          (and (= :num (first a)) (zero? (second a))) [:num 0]
          (and (= :num (first b)) (zero? (second b))) [:num 0]
          :else [:mul a b]))

      :else e)))

(defn size
  "The number of nodes in e."
  [e]
  (case (first e)
    :num 1
    :var 1
    :neg (+ 1 (size (nth e 1)))
    :add (+ 1 (size (nth e 1)) (size (nth e 2)))
    :mul (+ 1 (size (nth e 1)) (size (nth e 2)))))

(defn- vars
  [e]
  (case (first e)
    :num []
    :var [(second e)]
    :neg (vars (nth e 1))
    :add (concat (vars (nth e 1)) (vars (nth e 2)))
    :mul (concat (vars (nth e 1)) (vars (nth e 2)))))

(defn variables
  "The vector of the distinct keywords e mentions, sorted."
  [e]
  (vec (sort (distinct (vars e)))))
