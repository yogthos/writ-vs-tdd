(ns expr.a087
  "Reference implementation (hidden from the model under test).")

(defn evaluate [e env]
  (case (first e)
    :num (second e)
    :var (get env (second e) 0)
    :add (+ (evaluate (nth e 1) env) (evaluate (nth e 2) env))
    :mul (* (evaluate (nth e 1) env) (evaluate (nth e 2) env))
    :neg (- (evaluate (nth e 1) env))))

(defn- num? [x] (= :num (first x)))
(defn- num-of? [x n] (and (num? x) (= n (second x))))

(defn- rule [e]
  (let [[op a b] e]
    (cond
      (and (= :add op) (num? a) (num? b)) [:num (+ (second a) (second b))]
      (and (= :mul op) (num? a) (num? b)) [:num (* (second a) (second b))]
      (and (= :neg op) (num? a)) [:num (- (second a))]
      (and (= :add op) (num-of? a 0)) b
      (and (= :add op) (num-of? b 0)) a
      (and (= :mul op) (num-of? a 1)) b
      (and (= :mul op) (num-of? b 1)) a
      (and (= :mul op) (or (num-of? a 0) (num-of? b 0))) [:num 0]
      (and (= :neg op) (= :neg (first a))) (second a)
      :else e)))

(defn simplify [e]
  (case (first e)
    (:num :var) e
    (:add :mul) (rule [(first e) (simplify (nth e 1)) (simplify (nth e 2))])
    :neg (rule [:neg (simplify (nth e 1))])))

(defn size [e]
  (case (first e)
    (:num :var) 1
    (:add :mul) (+ 1 (size (nth e 1)) (size (nth e 2)))
    :neg (+ 2 (size (nth e 1)))))

(defn- vars-in [e]
  (case (first e)
    :num #{}
    :var #{(second e)}
    (:add :mul) (into (vars-in (nth e 1)) (vars-in (nth e 2)))
    :neg (vars-in (nth e 1))))

(defn variables [e] (vec (sort (vars-in e))))
