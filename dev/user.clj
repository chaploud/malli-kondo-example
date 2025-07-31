(ns user
  (:require [malli.clj-kondo :as mc]
            [example.core :as example]))

(defn generate-schema
  "malliのスキーマをclj-kondo向けに生成する"
  []
  (mc/emit!))