# malliとclj-kondoによる静的型チェック

## malliチュートリアル

malliについて素早くキャッチアップするためにこちらをご参照ください。また、malliで使えるキーワード群のリファレンスとしてもお使いください(「malliのビルトインスキーマの一覧」の項目)。

[malliの基本機能チュートリアル](./malli.md)

## どうなる？

![clj-kondoによる型の不一致警告](./image/kondo.png)

## 試す

まずはこのリポジトリをクローンして、中に入ってください。

### コマンドラインから

以下で、`clj-kondo`が実行され、`.clj-kondo/metosin/malli-types-clj/config.edn`の設定に従いリントエラーが表示されます。

```sh
clj -M:lint

src/example/core.clj:17:11: error: Expected: integer, received: string.
src/example/core.clj:19:11: error: Expected: integer, received: string.
linting took 47ms, errors: 2, warnings: 0
```

### エディタを開いて

- Calvaでは「どうなる？」のように自動で`clj-kondo`連携されて、下線とエラー表示ガ出るはずです。
- 他のエディタでも、`clj-kondo`連携されていれば同様のエラーが表示されるはずです。

## プロジェクトでmalliによるスキーマとclj-kondoを連携する手順

### 1. `deps.edn`にmalliを追加

```clojure
{:paths ["src", "dev"]
 :deps {org.clojure/clojure {:mvn/version "1.12.1"}
        metosin/malli {:mvn/version "0.19.1"}}
 :aliases
 {:lint {:extra-deps {clj-kondo/clj-kondo {:mvn/version "2025.07.28"}}
         :main-opts ["-m" "clj-kondo.main"
                     "--lint" "src"]}
  :emit-malli {:exec-fn user/generate-schema}}}
```

- `src/`, `dev/` ディレクトリをパスに追加
- `metosin/malli`を依存関係に追加
- `lint`エイリアス: `clj-kondo`の実行
- `emit-malli`エイリアス: `malli.clj-kondo`を使ってclj-kondo用の設定ファイルを生成する

### 2. 関数定義し、malliのスキーマも追加

`src/example/core.clj`

```clojure
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
```

### 3. REPLでclj-kondo用のファイルを生成

`dev/user.clj`

```clojure
(ns user
  (:require [malli.clj-kondo :as mc]
            [example.core :as example]))

(defn generate-schema
  "malliのスキーマをclj-kondo向けに生成する"
  ([] (generate-schema {})) ; REPL用
  ([_opts] (mc/emit!))) ; CLI用
```

REPLで

```clojure
(generate-schema)
;; 以下が生成される
;; .clj-kondo/metosin/malli-types-clj/config.edn
```

### 4. 自動化への道

- 毎回手動でREPLで`generate-schema`していては面倒だし忘れる可能性があるので、CLIから使えるようにした上で、lintする前に自動実行させるようにしてもいいかもしれない。
- CLIから実行するには、以下のコマンド実行

```sh
clj -X:emit-malli
clj -M:lint  # エラーがあれば▼報告される
```