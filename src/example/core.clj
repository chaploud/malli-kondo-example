(ns example.core
  (:require [malli.core :as m]))

(defn square [x] (* x x))
(m/=> square [:=> [:cat int?] nat-int?])

(defn plus
  ([x] x)
  ([x y] (+ x y)))

(m/=> plus [:function
            [:=> [:cat int?] int?]
            [:=> [:cat int? int?] int?]])

(comment
  (square 3)
  (square "1")
  (plus 1 2)
  (plus 1 "2"))