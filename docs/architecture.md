# 技術構成と環境準備

## 1. 技術の役割

| 技術 | 担当 |
|---|---|
| Kotlin | 入力チェック、集計結果の処理、保存や復元の制御 |
| Jetpack Compose / Material 3 | 入力画面、履歴、グラフ、テーマ |
| ViewModel / StateFlow | 画面の状態と操作の受け取り |
| Coroutines / Flow | 非同期処理とDB更新の通知 |
| Room / SQLite | 端末内の永続保存、検索、集計 |
| Navigation Compose | 単一Activity内の画面遷移 |
| kotlinx.serialization | JSONの読込・書出し。DB Entityとは別のDTOを使用 |
| Storage Access Framework | 利用者が指定したファイルへの入出力 |

画面からの依存方向は `Composable → ViewModel → Repository → DAO → Room`。Composableには状態とコールバックを渡す。Roomの変更をFlowで受け取り、ViewModelがUiStateにまとめ、画面が `collectAsStateWithLifecycle` で購読する。

サーバーは初期版にない。将来同期を追加する場合にもUIから保存先が見えないよう、Repositoryを境界にする。ただし、同期用の空クラスは先行実装しない。

## 2. 小さく始める構成

単一 `app` モジュールとする。以下はパッケージの役割を示す案。package名はP0で確定する。

| パス（app/src/main/java/配下） | 役割 |
|---|---|
| app/ | Application、Activity、AppContainer、ナビゲーション |
| ui/entry/、history/、summary/ | 各画面とViewModel |
| ui/budget/、goals/、settings/ | 予算・目標・設定画面 |
| ui/components/、theme/ | 少数の再利用部品、配色・文字 |
| data/local/ | Room Database、Entity、DAO、Migration |
| data/repository/ | 取引・予算・目標の操作と整合性管理 |
| data/backup/ | DTO、JSONの検証、ファイル処理、復元の調整 |
| model/ | Kindなどの型、金額入力、日付範囲など純粋な計算 |

Repositoryを操作するだけのUseCaseを大量に作らない。複数画面で複雑な処理の共有が必要になった時点で分離する。DIフレームワークは初期必須にしない。

## 3. 設計上の要点

- 保存・削除はRepositoryが担当。エラーを日本語表示用の状態に変換し、例外やSQL全文を利用者に表示しない。
- 再構成の中でDB書き込みや初期データ投入をしない。初期カテゴリはDB作成時だけ投入する。
- 大きな全取引リストをUIやViewModelに保持しない。月集計はDAOの `SUM` / `GROUP BY`。履歴は50件単位の読み込みとLazyColumn。
- グラフは集計済みの最大7カテゴリ程度をComposeの単純な横棒で描く。アクセシビリティ向けの名称・金額・割合を併記する。
- 装飾のためだけに画像・チャート・フォントの巨大な依存を足さない。
- DBが開けない場合、空データのアプリとして処理せず復旧案内を表示。マイグレーション失敗時に初期化しない。
- 復元と通常編集は同時に走らせない。Repository経由の全更新を共有Mutexで制御し、復元時は画面操作を停止する。Roomトランザクションだけで古いフォームの再保存を防げるとは考えない。
- 手動復元後はナビゲーションを履歴画面に戻し、入力状態とページングを破棄する。保存済みデータから再構築する。

## 4. 初期セットアップ（P0）

1. 既存ファイル、JDK、Android SDK、Android Studio、実機/エミュレーターの有無を確認する。
2. Android Studioの安定版テンプレートを基準に、Kotlin・Composeの新規プロジェクトを作成。IDEが使えない場合も同等のGradle構成とする。
3. 仮minSdkは26。compileSdk / targetSdk、AGP、Gradle、JDK、Kotlin、Compose BOM、KSP、Room、Navigation、serializationの互換性を公式資料で確認する。
4. 具体的なバージョンを `gradle/libs.versions.toml` 等に固定し、Gradle Wrapperを収録する。`+` や未確認の最新版指定は使わない。
5. `docs/toolchain.md` を新規作成し、バージョン、確認日、公式参照先、ビルド結果、実機情報を記録する。これはP0の成果物であり、現在の資料には未確認値を埋めない。
6. applicationIdは `com.example` のまま日常利用版を配布しない。固有の識別子と継続する署名を初回利用版までに決める。署名鍵やパスワード、local.propertiesはリポジトリに含めない。
7. 空画面をビルドし、起動確認できた状態から機能実装を始める。

## 5. 権限とOSバックアップ

初期版ではネットワーク、連絡先、位置、カメラ、広範なストレージ権限を追加しない。JSONの出力は `CreateDocument`、入力は `OpenDocument` のActivity Result契約を使い、ContentResolverで扱う。URIを通常のファイルパスと仮定しない。

OSのAuto Backupはアプリ自身の通信とは別なので、マニフェストの設定を放置しない。ローカル保存中心の初期方針として `allowBackup=false` を設定し、対応OS別の `fullBackupContent` / `dataExtractionRules` でDB・個人データ・設定をクラウドバックアップとdevice-transferから除外する。新しいdomainやOEM差も公式情報と実機で確認する。フラグ1つで全メーカーの端末移行を完全に防げると断言しない。

手動で利用者が選ぶバックアップ先は別扱い。復元の保証は、検証したJSON形式と実施したテスト範囲に限る。

## 6. ビルド・試験

作成後の標準コマンド：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

後者は実機またはエミュレーターが必要。リリース相当の性能計測にはデバッグモードを使わない。署名鍵を成果物に含めず、本人が保管するための手順を用意する。

通常のアプリ利用はオフラインだが、初回の依存関係取得やSDK導入には開発環境の通信が必要になる。
