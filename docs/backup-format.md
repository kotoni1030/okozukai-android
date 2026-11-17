# バックアップ形式と復元

契約名：`okozukai-backup`、formatVersion：1。実例は [backup-v1.json](../examples/backup-v1.json)。例は架空データで、利用者の記録ではない。

## 1. JSONの契約

UTF-8のJSONオブジェクトを1つ保存する。圧縮なし、暗号化なし。MIME typeは `application/json`。標準ファイル名は `okozukai-backup-YYYYMMDD-HHmmss.json`（端末の時刻）。元のRoomファイルをそのままコピーしない。

| 最上位フィールド | 型 | 意味 |
|---|---|---|
| format | String | 必ず `okozukai-backup` |
| formatVersion | Integer | 必ず1 |
| exportedAt | String | 書き出し日時、UTC Instant |
| appVersion | String | 出力したアプリ版（1〜80文字） |
| currency | String | 必ずJPY |
| categories | Array | カテゴリ定義 |
| transactions | Array | 収入・支出 |
| monthlyBudgets | Array | 月全体予算 |
| categoryBudgets | Array | カテゴリ別予算 |
| savingsGoals | Array | 貯金目標 |

全フィールド必須、null不可。空の配列は許可。ただしcategoriesは収入と支出それぞれに最低1つのactiveなカテゴリを含むこと。初期の10カテゴリとIDが完全一致する必要はない。これにより将来カテゴリが変わった場合も定義と参照を一緒に保持できる。

### 各配列の要素

| 配列 | 必須フィールド |
|---|---|
| categories | id: String、kind: String、name: String、sortOrder: Integer、isActive: Boolean |
| transactions | id: String、kind: String、amountYen: Integer、categoryId: String、date: String、note: String、createdAt: String、updatedAt: String |
| monthlyBudgets | month: String、amountYen: Integer |
| categoryBudgets | month: String、categoryId: String、amountYen: Integer |
| savingsGoals | id: String、name: String、targetAmountYen: Integer、savedAmountYen: Integer、createdAt: String、updatedAt: String |

各値の範囲は [data-model.md](data-model.md) に一致させる。日付は `YYYY-MM-DD`、月は `YYYY-MM`、時刻はUTCのISO 8601でミリ秒精度を保持する。UUIDは小文字の標準表記で出力し、入力もその表記を要求する。

数値はJSON numberの整数で、文字列、指数形式、小数形式を拒否する。浮動小数点型を経由して丸めない。未知のフィールド、同じオブジェクト内の重複キー、コメント、末尾カンマは拒否する。パーサーの標準設定だけで重複キーを検出できるとは仮定せず、重複検出と数値字句検証を実装・テストする。

## 2. 容量の初期上限

軽量な個人用初期版の運用上限として、全配列の合計20,000レコード、カテゴリ200件、貯金目標200件、ファイル32MiBを仮採用する。通常の新規登録・予算コピーも同じレコード上限を守る。上限でも閲覧・編集・削除・書き出しは可能にする。

出力はコンパクトなJSONを使う。受け入れ可能な通常データが必ず自分の形式で復元できることを容量境界テストで確認する。必要があれば上限と処理方式を一緒に見直す。ファイルの申告サイズだけを信用せず、読み取ったバイト数にも制限を設ける。巨大な配列や過度に深い入れ子を解析途中で停止できるようにする。

## 3. 書き出し

1. システムの保存先選択を開く。取消はデータ変更なし。
2. 全テーブルを単一のRoom読取トランザクションで読み、一貫したスナップショットを得る。
3. DTOへ変換し、同じバリデーターで形式・参照・容量を確認する。
4. 出力ストリームへ書き、クローズの成功まで確認する。DBトランザクションはファイル操作まで保持しない。
5. ファイルへの書込完了を表示する。クラウド提供元のサーバー同期完了まで保証した表現にはしない。

書き出し途中に失敗した場合、DBに影響を与えない。可能なら作成した不完全なファイルを削除し、できなければ未完成ファイルの可能性を伝える。成功と表示してはいけない。

## 4. 復元前の検証

通常データベースを変更する前に、以下をすべて検証する。

- format / version / currency、JSON構文、重複キー、型、必須/未知フィールド。
- 実在日付、月、時刻、文字数、金額、UUID表記、件数、ファイル容量。
- 各テーブル内でIDが一意。カテゴリは `(kind, name)` が一意。
- 月予算はmonthが一意。カテゴリ予算は `(month, categoryId)` が一意。
- 取引とカテゴリ予算の参照先が存在。取引とカテゴリのkind一致。カテゴリ予算はEXPENSEのみ。
- categoriesに両種別のactiveカテゴリがある。非activeカテゴリへの既存参照は許可する。
- createdAt ≦ updatedAt。amountYen等の合計がLongの範囲を超えない。

未来の日付は仕様上許可するので、復元だけで拒否しない。目標積立額の目標超えも許可する。

v1以外のformatVersionはデータを変更せず拒否する。「対応していないバックアップ形式です」と表示し、将来版を勝手にv1として読まない。

## 5. 全体置き換え

1. 検証済みの内容から出力日時、件数、現在データが置き換わることを表示。現在データを書き出す選択肢も示す。
2. 利用者が置き換えを選択した後、全更新共通のMutexを取得し、画面の編集を停止する。
3. 確認に使った同じ検証済みスナップショットを使用する。URIから無検証で再読込した別内容を適用しない。
4. Roomの `withTransaction` 内で、取引・カテゴリ予算・月予算・目標を削除し、その後カテゴリを削除する。
5. 同じトランザクションでカテゴリを先に挿入し、残りを挿入する。通常起動の初期seedを混ぜない。
6. 1件でも失敗したらトランザクションを中止し元データに戻す。成功時のみ完了表示を出す。
7. 入力途中データ、カテゴリの記憶、ページングをクリアし、履歴の今月画面から状態を再読込する。Mutexを解放する。

復元はマージではない。同じファイルを2回復元しても行数とIDは変わらない。新規UUIDへ振り直さず、書き出しに含まれるIDと作成時刻を保持する。

## 6. 含めない情報

端末URI、アクセス権、ログ、キャッシュ、直近選択カテゴリ、未保存フォーム、署名情報はバックアップに含めない。アプリの業務データをサーバーへ自動送信しない。
