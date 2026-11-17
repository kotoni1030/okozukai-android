# データ設計

DB名案：`okozukai.db`、初期Room schema version：1。バックアップのformatVersionとは独立する。

## 1. 型と制約

- ID：UUID文字列。初期カテゴリには下表の固定ID、利用者データには新しいUUIDを使う。
- 金額：SQLite INTEGER / Kotlin Long（円）。取引・目標額は1〜999,999,999、予算・積立額は0〜999,999,999。
- `kind`：`EXPENSE` / `INCOME`。DBとJSONの文字列は永続契約として保持する。
- DBの取引日：`dateEpochDay: Long`。KotlinではLocalDate、JSONでは `YYYY-MM-DD`。
- `month`：`YYYY-MM`、1900-01〜2199-12。入力はYearMonthで検証。
- 作成・更新時刻：DBではepoch millisecondsのLong、JSONではUTCのISO 8601（Instant）。作成≦更新とする。時刻補正等で逆転する場合は更新時刻を前回時刻以上にする。
- 日付の「今日」は端末のZoneIdから作る。UTCへの変換で取引日を変えない。時刻・ZoneIdはテストで差し替え可能にする。
- 文字数制限は前後空白除去後のUnicodeコードポイント数で統一し、UI・Repository・復元で同じ検証関数を使う。
- 制約はUIだけでなくRepositoryとインポートにも適用。DBで表現できる主キー・外部キー・一意制約を併用する。

## 2. categories

| 列 | 型 | 制約 |
|---|---|---|
| id | String | 主キー、UUID |
| kind | String | EXPENSE / INCOME |
| name | String | 1〜30文字 |
| sortOrder | Int | 0以上、同順ならID順 |
| isActive | Boolean | 新規入力の選択に出すか |

`(kind, name)` は一意。同じ名前を収入・支出それぞれに使うことは許可する。使用済みカテゴリの物理削除は外部キーRESTRICTで防ぐ。カテゴリ編集UIは初期版に含めないが、非表示カテゴリの既存履歴と集計は維持する。既存取引が非表示カテゴリを参照していても、カテゴリを変えない編集は許可する。

### 初期カテゴリ（末尾以外共通のUUID）

| id | kind | name | sortOrder |
|---|---|---|---:|
| 00000000-0000-4000-8000-000000000001 | EXPENSE | 食費 | 0 |
| 00000000-0000-4000-8000-000000000002 | EXPENSE | 交通費 | 1 |
| 00000000-0000-4000-8000-000000000003 | EXPENSE | 日用品 | 2 |
| 00000000-0000-4000-8000-000000000004 | EXPENSE | 趣味・娯楽 | 3 |
| 00000000-0000-4000-8000-000000000005 | EXPENSE | 交際費 | 4 |
| 00000000-0000-4000-8000-000000000006 | EXPENSE | 学習 | 5 |
| 00000000-0000-4000-8000-000000000007 | EXPENSE | その他 | 6 |
| 00000000-0000-4000-8000-000000000008 | INCOME | バイト代 | 0 |
| 00000000-0000-4000-8000-000000000009 | INCOME | お小遣い | 1 |
| 00000000-0000-4000-8000-000000000010 | INCOME | その他 | 2 |

初期値のisActiveはすべてtrue。通常の起動や復元後に再投入しない。バックアップ内のカテゴリ定義を優先し、表示名をIDとして使わない。

## 3. transactions

| 列 | 型 | 制約 |
|---|---|---|
| id | String | 主キー、UUID |
| kind | String | EXPENSE / INCOME |
| amountYen | Long | 1〜999,999,999 |
| categoryId | String | categories.idへの外部キー、削除RESTRICT |
| dateEpochDay | Long | 1900-01-01〜2199-12-31 |
| note | String | 0〜200文字 |
| createdAt | Long | 作成時刻 |
| updatedAt | Long | 更新時刻 |

category.kindとtransaction.kindの一致はRepositoryで検証し、復元時にも全件検査する。取引は編集でもIDを変えない。`categoryId` と、履歴順の `(dateEpochDay, createdAt, id)` に索引を置く。さらに `(kind, dateEpochDay, categoryId)` を月集計用に検討し、実際のクエリ計画で確認する。

## 4. monthly_budgets / category_budgets

| テーブル | 列 | 制約 |
|---|---|---|
| monthly_budgets | month: String | 主キー |
| monthly_budgets | amountYen: Long | 0〜999,999,999 |
| category_budgets | month: String | 複合主キーの一部 |
| category_budgets | categoryId: String | 複合主キーの一部、カテゴリ外部キー |
| category_budgets | amountYen: Long | 0〜999,999,999 |

カテゴリ予算は支出カテゴリのみ許可。`(month, categoryId)` は一意で、categoryIdにも索引を置く。行なし＝未設定、amountYen=0＝0円の上限を設定済み。NULLを混ぜた複合一意制約に頼らず、月全体の予算とカテゴリ予算を別テーブルにする。

前月コピーは1トランザクションで対象月の設定を前月と一致させる。どちらも空ならコピー操作を無効にする。ユーザーが予算を未設定に戻す場合は行を削除する。

## 5. savings_goals

| 列 | 型 | 制約 |
|---|---|---|
| id | String | 主キー、UUID |
| name | String | 1〜40文字 |
| targetAmountYen | Long | 1〜999,999,999 |
| savedAmountYen | Long | 0〜999,999,999、目標額超え可 |
| createdAt | Long | 作成時刻 |
| updatedAt | Long | 更新時刻 |

取引との外部キーは設けない。積立額は現在値の置き換え。同名目標は許可する。並びはcreatedAt DESC、id DESC。

## 6. 集計の例

以下はDAO作成時の仕様例であり、実装済みSQLではない。

```sql
SELECT COALESCE(SUM(amountYen), 0)
FROM transactions
WHERE kind = :kind
  AND dateEpochDay >= :startDay
  AND dateEpochDay < :nextMonthDay;

SELECT categoryId, SUM(amountYen) AS totalYen
FROM transactions
WHERE kind = 'EXPENSE'
  AND dateEpochDay >= :startDay
  AND dateEpochDay < :nextMonthDay
GROUP BY categoryId;
```

計算過程もLongを使う。将来のデータ増加にも備え、インポートでは合計の安全性を検証する。割合は最後にDouble等へ変換し、金額へ逆変換しない。

## 7. 履歴のページング

月で絞り、`dateEpochDay DESC, createdAt DESC, id DESC` で50件ずつ読む。続きを読むときは最後の3値より辞書順で小さい行を取得する。日付だけのカーソルは同日行を取り落とすため使わない。

日別合計はページ内の行から計算せず、対象月のSQL集計を使う。同日の行が次ページにもあっても、その日の全額を表示する。編集・削除・新規追加・復元後は一覧のカーソルを無効にして先頭から再読込し、同じIDを二度表示しない。

## 8. 永続化しない派生値

残額、月収支、割合、目標の達成状態、表示用の書式済み金額は保存せず計算する。直近のカテゴリ選択等の便利設定は業務データと分け、初期版のバックアップ対象外にする。

## 9. マイグレーション

Room schemaをエクスポートし、バージョンごとの移行をテストする。移行後もID・金額・日付・参照関係が保たれること。アプリ更新だけで取引を削除しない。カテゴリ名変更もマイグレーションや明示操作として扱い、起動時の再seedで上書きしない。
