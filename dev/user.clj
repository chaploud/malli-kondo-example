(ns user
  (:require [malli.clj-kondo :as mc]
            [example.core :as example]))

(defn generate-schema
  "malliのスキーマをclj-kondo向けに生成する"
  ([] (generate-schema {})) ; REPL用
  ([_opts] (mc/emit!))) ; CLI用