# malliの基本機能チュートリアル

[本家(長い)](https://github.com/metosin/malli)

## 何をするもの？

- Clojureにおいては、"データ駆動"的なアプローチが一般的
- 複雑な構造をしたデータについても、malliでスキーマ定義をすることで以下の恩恵がある
  - どんなデータが来るかを明示的に定義できる
  - データのバリデーションを行える
  - 静的な型チェックが可能になる(clj-kondoを使用)

## ユースケース

- DBとアクセスする層のスキーマ定義
- APIのリクエスト/レスポンスのスキーマ定義
- 複雑な構造の引数を想定する関数のスキーマ定義

## 言い訳

- 「型」と「スキーマ」は同じようなものとして表記揺れがあります。malliの文脈ではあんまり気にしないでいいかも。

## `deps.edn`

```clojure
{:deps {metosin/malli {:mvn/version "0.19.1"}}}
```

## `require`

```clojure
(ns example
  (:require [malli.core :as m]))
;; または
(require '[malli.core :as m])
```

## 雰囲気を味わうためのコード例

REPLで実行していることを想定

```clojure
(require '[malli.core :as m])
(require '[malli.generator :as mg])

;; UserIdは文字列型
(def UserId :string)

;; {:street "Central Street", :country "FI"}のようなマップ
(def Address
  [:map
   [:street :string]
   [:country [:enum "FI" "UA"]]])

;; 定義済みスキーマを組み合わせることもできる
(def User
  [:map
   [:id #'UserId] ; #'必要(Varそのものを参照) => UserId型
   [:address #'Address] ; Address型
   [:friends [:set {:gen/max 2} [:ref #'User]]]])

;; スキーマを使ってデータを生成
(mg/generate User)
; {:id "AC",
;  :address {:street "mf", :country "UA"},
;  :friends #{{:id "1dm", :address {:street "8", :country "UA"}
;              :friends #{}}}}

;; スキーマを使ってデータを検証(前の評価結果*1を使用)
(m/validate User *1) ; => true
```

## 暗黙の取り決め

- ユーザーによるスキーマ定義は、キャピタルケース(例: `UserId`)

## ベクタ形式のスキーマ

- malliにはベクタ形式とマップ形式の2種類のスキーマ定義方法がある
- ベクタ形式が歴史的には先にあり、十分表現豊かたが、それに対応しきれないものはマップ形式を使う

```clojure
:string ; 文字列型
[:string {:min 1, :max 10}] ; 長さ1〜10の文字列型(マップでプロパティを指定できる)
[:tuple {:title "location"} :double :double] ; [2.0, 3.2]のようなタプル型

;; 関数スキーマ
[:=> [:cat :int] :int] ; （x: int) -> int の関数
[:-> :int :int] ; 同じ
```

## マップ形式のスキーマ

```clojure
{:type :string} ; 文字列型
{:type :string
 :properties {:min 1, :max 10}} ; 長さ1〜10の文字列型
{:type :tuple,
 :properties {:title "location"}
 :children [{:type :double}
            {:type :double}]} ; [2.0, 3.2]のようなタプル型

;; 関数スキーマ
{:type :=>
 :input {:type :cat, :children [{:type :int}]}
 :output :int} ; （x: int) -> int の関数
{:type :->
 :children [{:type :int} {:type :int}]} ; 同じ
```

## 値の動的バリデーション

Webアプリケーションではのデータ授受の際のバリデーションで使えそう

```clojure
(m/validate (m/schema :int) 1) ; => true 書き方1: 整数か？
(m/validate :int 1) ; => true 書き方2: 整数か？
(m/validate :int "1") ; => false 書き方3: 整数か？(文字列はfalse)
(m/validate [:= 1] 1) ; => true 1という値(単一値)か？
(m/validate [:enum 1 2] 1) ; => true 1または2(enum)か？
(m/validate [:and :int [:> 6]] 7) ; => true 整数 & 6より大きいか？

;; 構造的なスキーマの場合
(def valid?
  (m/validator
   [:map
    [:x :boolean]
    [:y {:optional true} :int]
    [:z :string]]))

(valid? {:x true, :z "kikka"}) ; => true
```

### スキーマにもプロパティを持たせることができう

- 範囲指定とか、説明など、プロジェクトに応じて有効に使おう

```clojure
(def Age
  [:and
    {:title "Age"
     :description "It's an age"
     :json-schema/example 20} ; 一個目に書けないのはちょっと気に食わない
   :int [:> 18]])

(m/properties Age)
;; => {:title "Age", :description "It's an age", :json-schema/example 20}
```

### Map型のスキーマはデフォルトでは "オープン" (追加のキーを許容)なので注意

```clojure
(m/validate
  [:map [:x :int]]
  {:x 1, :extra "key"}) ; => true スキーマにないキーがあってもtrue

;; もし、追加のキーを許容しない場合は、`{:closed true}`を指定する
(m/validate
  [:map {:closed true} [:x :int]]
  {:x 1, :extra "key"}) ; => false スキーマにないキーがあるのでfalse
```

- Mapのキーは`:keyword`以外にも、整数や文字列やnilや完全修飾(名前空間付き)キーワードも使える

## スキーマのレジストリ（保管場所）

- [本家](https://github.com/metosin/malli#schema-registry)
- 特に意識せずに使うと、`malli.core/default-registry`のスキーマが使われる
  - 大方のケースではこれで十分
- しかし、プロジェクト固有のスキーマ集、みたいなこだわりが出てきたときには、独自レジストリを作ってそれでバリデーションに活用するということもある

## 関数スキーマ

[本家でも関数スキーマの特集](https://github.com/metosin/malli/blob/master/docs/function-schemas.md)が組まれている通り、長いので、[関数スキーマ](./function-schemas.md)にClaudeにまとめさせた。

malliで関数スキーマを定義する方法は以下の3つがある。

### 1. 関数スキーマアノテーション (`m/=>`)

`m/=>`マクロで関数名に対し、後からスキーマを登録できる。元の関数にシンタクティックノイズが入らないので嬉しい。

```clojure
(defn plus [x y] (+ x y))
(m/=> plus [:=> [:cat :int :int] :int])
```

### 2. メタデータ方式 (`:malli/schema`)

既存の`defn`にメタデータ挟みこむ形。悪くない。

```clojure
(defn plus
  {:malli/schema [:=> [:cat :int :int] :int]}
  [x y]
  (+ x y))
```

### 3. インラインスキーマ (`mx/defn`)

最も簡潔に書けてスキーマが自動登録されるが、`mx/defn`を使わないとけないのであまり好きくない。

```clojure
(require '[malli.experimental :as mx])

(mx/defn plus :- :int
  [x :- :int, y :- :int]
  (+ x y))
```

### で、関数スキーマで何ができる？

個人的に嬉しいのは、3. 自動テストデータ生成、と 4. 静的型チェック

```clojure
(require '[malli.core :as m]
         '[malli.instrument :as mi]
         '[malli.generator :as mg]
         '[malli.dev :as dev]
         '[malli.dev.pretty :as pretty])

;; 1. 開発時の自動型チェック（インストルメンテーション）
(mi/instrument!)  ; または (dev/start!)
(plus "文字列" 2)  ; => エラー！整数が期待される

;; 2. 関数の実装チェック（mi/check）
(mi/check)
; 登録されたスキーマと実装が一致するか検証
; 例：戻り値の範囲が正しいか等

;; 3. 自動テストデータ生成
(mg/generate =>plus)
; スキーマから関数のモックを自動生成
; プロパティベーステストに活用可能

;; 4. 静的型チェック（clj-kondo連携）
; malli.devを使うと自動的にclj-kondo設定を生成
; エディタで型エラーを表示

;; 5. きれいなエラーメッセージ
(dev/start! {:report (pretty/reporter)})
; 型エラー時に見やすいエラー表示

;; 6. ドキュメントとしての機能
(m/function-schemas)
; 登録された全関数のスキーマ一覧を確認
; APIドキュメントの自動生成も可能

;; 7. 本番環境での選択的検証
(m/-instrument {:schema =>critical-function
                :scope #{:input}})
; 重要な関数のみ本番でも検証clojure
```

## malliのビルトインスキーマの一覧

- 何が使えるねん！は素朴な疑問なので書き出し。多いので、その意味は名前や検索で調べてください。
- [公式](https://github.com/metosin/malli?tab=readme-ov-file#built-in-schemas)


### `malli.core/predicate-schemas`

Clojureの述語に対応するようなスキーマ

```clojure
any?, some?,
number?, integer?, int?, pos-int?, neg-int?,
pos?, neg?,
float?, double?,
boolean?,
string?,
ident?, simple-ident?, qualified-ident?,
keyword?, simple-keyword?, qualified-keyword?,
symbol?, simple-symbol?, qualified-symbol?,
uuid?, uri?, decimal?, inst?,
seqable?, indexed?,
map?, vector?, list?, seq?, char?, set?, nil?, false?, true?,
zero?, rational?, coll?, empty?,
associative?, sequential?, ratio?, bytes?,
ifn?, fn?
```

### `malli.core/comparator-schemas`

比較系のスキーマ

```clojure
:>    ; 大なり
:>=   ; 大なりイコール
:<    ; 小なり
:<=   ; 小なりイコール
:=    ; イコール
:not= ; イコールではない
```

### `malli.core/type-schemas`

基本的な型のスキーマ

```clojure
:any        ; 何でも
:some       ; nil以外
:nil        ; nil
:string     ; 文字列
:int        ; 整数
:double     ; 浮動小数点数
:boolean    ; 真偽値
:keyword    ; キーワード
:qualified-keyword ; 名前空間付きキーワード
:symbol     ; シンボル
:qualified-symbol  ; 名前空間付きシンボル
:uuid       ; UUID (#uuid "d56121c5-7cd8-4060-9dfa-95ceed46dc4f")
```

### `malli.core/sequence-schemas`

正規表現風、個数やオプショナル、名前付き引数などのスキーマ

```clojure
:+      ; 1個以上
:*      ; 0個以上
:?      ; 0個または1個
:repeat ; 0個以上の繰り返し
:cat    ; 連結(concatenattion), 位置引数
:alt    ; 選択(alternative), どれか1つ
:catn   ; 名前付き連結(named concatenation)
:altn   ; 名前付き選択(named alternative)
```

### `malli.core/base-schemas`

```clojure
:and         ; 論理積
:or          ; 論理和
:orn         ; 名前付き論理和
:not         ; 否定
:map         ; マップ [:map [:x :int] [:y :tring]]
:map-of      ; キーの型と値の型が決まっているマップ(キーの名前は気にしない)
:vector      ; ベクタ [:vector :int]
:sequential  ; シーケンス(順序をもつ)
:set         ; セット [:set :int]
:enum        ; 列挙型 [:enum "S" "M" "L"] (いずれかの値)
:maybe       ; nilまたは指定したスキーマにマッチするか？
:tuple       ; タプル(固定長のベクタ) [:tuple :int :string number?]
:multi       ; マルチメソッド風に、キーに応じて異なるスキーマを適用
:re          ; 正規表現による文字列判定
:fn          ; スキーマとして関数を使える(柔軟性はあるが、分かりにくくなるので多様は禁物)
:ref         ; 主に自身を参照して再帰的なスキーマを定義するために使用
:=>          ; 関数自体のスキーマ表現 引数が1つ以上の場合(主にこっち使う) [:=> [:cat :int :int] :int]
:->          ; 関数自体のスキーマ表現 引数が1つの場合 [:-> :int :int]
:function    ; マルチアリティ関数スキーマの先頭に指定してラップ
:schema      ; malliスキーマを表す
```

### `malli.util/schemas`

```clojure
:merge  ; スキーマをマージ
:union  ; スキーマの和集合(orとはふるまいが微妙に違う)
:select-keys ; スキーマのうち指定したキーのスキーマを抽出
```

こちらは少し複雑なので、[本家](https://github.com/metosin/malli#declarative-schema-transformation)チェックをおすすめする。

### `malli.experimental.time`

- 時間に関するスキーマ。詳しくは[本家](https://github.com/metosin/malli?tab=readme-ov-file#malliexperimentaltime)を参照。
- Webアプリにおいては時間を頻繁に使うので、experimentalではあるが、使う価値はある。

```clojure
:time/instant ; 2022-12-18T12:00:25.840823567Z
;; 等々...
```
