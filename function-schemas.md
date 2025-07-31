# Malli関数スキーマ実践ガイド

malliの関数スキーマは、Clojureの関数に対して引数と戻り値の型を定義し、実行時に検証する強力な機能です。このガイドでは、実際の開発でよく使用されるユースケースに焦点を当てて説明します。

## 1. 基本的な関数スキーマの定義

### シンプルな関数の型定義

最も基本的なケースから始めましょう。整数を受け取って整数を返す関数です：

```clojure
(require '[malli.core :as m])

; 関数の定義
(defn increment [x]
  (inc x))

; 関数スキーマの定義
(m/=> increment [:=> [:cat :int] :int])
```

### より実践的な例：ユーザー登録関数

```clojure
; ユーザー情報を受け取って、IDを付与したマップを返す関数
(defn register-user [email password]
  {:id (java.util.UUID/randomUUID)
   :email email
   :password-hash (hash password)
   :created-at (java.time.Instant/now)})

; スキーマ定義
(m/=> register-user
  [:=>
   [:cat :string :string]  ; emailとpasswordの文字列
   [:map
    [:id :uuid]
    [:email :string]
    [:password-hash :any]
    [:created-at inst?]]])
```

## 2. 開発時の関数検証（推奨アプローチ）

開発時に関数の入出力を自動的に検証するには、`malli.dev`を使用します：

```clojure
(require '[malli.dev :as dev])
(require '[malli.dev.pretty :as pretty])

; 開発モードを開始（綺麗なエラー表示付き）
(dev/start! {:report (pretty/reporter)})

; これで関数呼び出し時に自動的に検証される
(increment "文字列")  ; => エラー！期待されるのは整数

(increment 5)  ; => 6（正常動作）
```

## 3. メタデータを使った関数スキーマ定義

関数定義時にメタデータとしてスキーマを記述する方法です。これによりmalliへの依存を最小限に抑えられます：

```clojure
(defn calculate-total
  "注文の合計金額を計算する"
  {:malli/schema [:=> [:cat
                        [:sequential [:map
                                      [:price :double]
                                      [:quantity :int]]]
                        :double]  ; 税率
                       :double]}  ; 合計金額
  [items tax-rate]
  (let [subtotal (reduce + (map #(* (:price %) (:quantity %)) items))]
    (* subtotal (+ 1 tax-rate))))

; スキーマを収集して登録
(require '[malli.instrument :as mi])
(mi/collect!)
```

## 4. インラインスキーマ定義（mx/defn）

Plumatic Schemaスタイルの記法を使った、より簡潔な定義方法：

```clojure
(require '[malli.experimental :as mx])

(mx/defn process-order :- [:map [:status :keyword] [:total :double]]
  [order-id :- :uuid
   items :- [:sequential [:map [:id :int] [:price :double]]]]
  (let [total (reduce + (map :price items))]
    {:status :processed
     :total total}))
```

## 5. 複雑な検証ルールの実装

### ガード関数を使った条件付き検証

```clojure
; パスワードは8文字以上で、確認用と一致する必要がある
(defn change-password [user-id new-password confirm-password]
  ; 実装...
  )

(m/=> change-password
  [:=>
   [:cat :uuid :string :string]
   :boolean
   [:fn {:error/message "パスワードは8文字以上で、確認用と一致する必要があります"}
    (fn [[[_ new-pw confirm-pw] _]]
      (and (>= (count new-pw) 8)
           (= new-pw confirm-pw)))]])
```

### カスタムスキーマを使った再利用可能な定義

```clojure
; よく使うスキーマを定義
(def Email [:string {:min 3, :max 254, :error/message "有効なメールアドレスを入力してください"}])
(def Password [:string {:min 8, :max 100}])
(def UserId :uuid)

; これらを使って関数スキーマを定義
(m/=> create-account
  [:=>
   [:cat Email Password]
   [:map
    [:id UserId]
    [:email Email]
    [:created-at inst?]]])
```

## 6. 実行時検証の活用

### 本番環境での選択的な検証

```clojure
; 重要な関数のみを手動でインストルメント
(def validate-payment
  (m/-instrument
    {:schema [:=> [:cat
                   [:map
                    [:amount :double]
                    [:currency [:enum "JPY" "USD" "EUR"]]
                    [:card-token :string]]
                  :boolean]]
     :scope #{:input}  ; 入力のみ検証（パフォーマンスを考慮）
     :report (fn [type data]
               (log/error "Payment validation failed" type data))}
    (fn [payment-info]
      ; 実際の支払い処理
      )))
```

## 7. テスト生成の活用

```clojure
(require '[malli.generator :as mg])

; スキーマから自動的にテストデータを生成
(def user-schema
  [:map
   [:name [:string {:min 1, :max 50}]]
   [:age [:int {:min 0, :max 150}]]
   [:email Email]])

; ランダムなユーザーデータを生成
(mg/generate user-schema)
; => {:name "John", :age 42, :email "john@example.com"}

; プロパティベーステストの実装
(m/=> sort-users
  [:=> [:cat [:sequential user-schema]]
       [:sequential user-schema]])

; 自動的にテストケースを生成して検証
(require '[malli.core :as m])
(def sort-users-schema
  (m/schema
    [:=> [:cat [:sequential user-schema]]
         [:sequential user-schema]]
    {::m/function-checker mg/function-checker}))

(m/validate sort-users-schema sort-users)
```

## 8. 実践的なベストプラクティス

### 1. 開発時は`malli.dev`を使う

```clojure
; プロジェクトの開発用名前空間で
(ns myapp.dev
  (:require [malli.dev :as dev]
            [malli.dev.pretty :as pretty]))

(defn start-dev! []
  (dev/start! {:report (pretty/reporter)}))

(defn stop-dev! []
  (dev/stop!))
```

### 2. 重要なビジネスロジックには必ずスキーマを定義

```clojure
; 金額計算など、間違えると困る関数
(mx/defn calculate-tax :- :double
  [amount :- :double
   tax-rate :- [:double {:min 0.0 :max 1.0}]]
  (* amount tax-rate))
```

### 3. エラーメッセージをわかりやすくする

```clojure
(def PositiveNumber
  [:double {:min 0.0
            :error/message "金額は0以上である必要があります"}])

(m/=> withdraw-money
  [:=> [:cat UserId PositiveNumber]
       [:or
        [:map [:success :boolean] [:balance :double]]
        [:map [:error :string]]]])
```

## まとめ

malliの関数スキーマを使うことで：

1. **開発時のバグを早期発見**：型の不一致をすぐに検出
2. **ドキュメントとしての役割**：関数の期待する入出力が明確
3. **テストの自動生成**：スキーマからテストデータを生成
4. **選択的な本番環境での検証**：重要な箇所のみ実行時チェック

開発を始める際は、まず`malli.dev/start!`で開発モードを有効にし、重要な関数から順次スキーマを定義していくことをお勧めします。