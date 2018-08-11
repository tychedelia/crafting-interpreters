(ns lox.parse)

::function
::method

(defn init [tokens]
  {:tokens tokens :current 0 :statements []})

(defn peek-token [{:keys [tokens current] :as parse}]
  (nth tokens current))

(defn is-finished? [{:keys [tokens current] :as parse}]
  (= :lox.token/eof (:type (peek-token parse))))

(defn previous [{:keys [tokens current] :as parse}]
  (let [n (max 0 (dec current))]
    (nth tokens n)))

(defn advance [{:keys [current] :as parse}]
  (if (not (is-finished? parse))
    (assoc parse :current (inc current))
    parse))

(defn check [parse type]
  (if (is-finished? parse) false
      (= type (:type (peek-token parse)))))

(defn consume
  ([parse type] (consume parse type (str "Could not consume " (name type))))
  ([{:keys [] :as parse} type message]
   (if (check parse type)
     (let [parse (advance parse)]
       [parse (previous parse)])
     (throw (Exception. message)))))

(defn match? [parse & types]
  (some #(check parse %) types))

(defn get-params [{:keys [] :as parse}]
  (if (not (check parse :lox.token/r-paren))
    (loop [{:keys [] :as parse} parse
           params []]
      (if (match? parse :lox.token/comma)
        (let [[parse token] (consume parse :lox.token/identifier "?")]
          (recur parse (conj params token)))
        [parse params]))))

(defn function [{:keys [] :as parse} type]
  (let [[parse name] (consume :lox.token/identifier (str "Expect " (name type) " name."))
        [parse _] (consume :lox.token/l-paren (str "Expect ( after " (name type) " name."))
        [parse params] (get-params parse)
        [parse _] (consume :lox.token/r-paren (str "Expect ) after params."))
        [parse _] (consume :lox.roken/l-brace (str "Expect { " (name type) " body."))
        [parse body] (block parse)]
    (:lox.statment/->Function name params body)))

(defn get-methods [{:keys [] :as parse}]
  (loop [methods []]
    (if (and (not (check parse :lox.token/r-brace)) (not (is-finished? parse)))
      (recur (conj methods (function ::method)))
      [parse methods])))

(defn get-superclass [{:keys [] :as parse}]
  (if (match? :lox.token/less)
    (consume parse :lox.token/identifier "Expect superclass name.")
    [parse nil]))

(defn class-declaration [{:keys [current] :as parse}]
  (let [parse (advance parse)
        [parse name] (consume parse :lox.token/identifier "Expected a class name")
        [parse superclass] (get-superclass parse)
        [parse _] (consume parse :lox.token/l-brace "")
        [parse methods] (get-methods parse)
        [parse _] (consume parse :lox.token/r-brace "")]
    [parse (lox.statement/->Statement name superclass methods)]))

(defn var-declaration [{:keys [] :as parse}])

(defn declaration [{:keys [] :as parse}]
  (cond
    (match? parse :lox.token/class) (class-declaration parse)
    (match? parse :lox.token/fun) (function parse ::function)
    (match? parse :lox.token/var) (var-declaration parse)
    :else (lox.statement/->Statement parse)))

(defn parse-statements [{:keys [statements] :as parse}]
  (if (is-finished? parse)
    (:statements parse)
    (let [[parse statement] (declaration parse)
          parse (assoc parse :statements (conj statements statement))]
      (parse-statements parse))))


(defn block [{:keys [] :as parse}]
  (let [statements (loop [statements []]
                     (if (and (not (check parse :lox.token/r-brace)) (not (is-finished? parse)))
                       (recur (declaration parse))
                       statements))]
    [(consume :lox.token/r-brace "Expect } after block.") statements]))



(defn parse [tokens]
  (let [parse (init tokens)]
    (parse-statements parse)))
