# お小遣い帳 Android — 開発資料

作成日：2026-11-17（日本時間）／仕様版：1.0

自分が「いつ・何に・いくら使ったか」を、短い入力と分かりやすい集計で把握するアプリです。Kotlin・Jetpack Compose・Roomで開発し、Android端末内で動作を完結させます。

単一appモジュールのAndroidアプリです。収入・支出の記録、編集・削除、月別履歴、カテゴリ集計、予算、貯金目標、JSONの書き出し・全体復元を実装しています。現在は開発版で、受入試験と実機確認を進めています。最新の検証範囲は [docs/toolchain.md](docs/toolchain.md) を参照してください。

## 開発コマンド

Android Studioでこのフォルダを開き、Gradle JDKを同梱JDKに設定します。SDKの場所はAndroid Studioが作る `local.properties` で指定します（Git管理対象外）。このPCのターミナルでは次を実行します。

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

2つ目のコマンドには起動済みの端末またはエミュレーターが必要です。APKは `app/build/outputs/apk/debug/app-debug.apk` に生成されます。開発版IDは仮の `jp.local.okozukai.dev`。日常利用版のIDと署名方針は配布前に確定します。

## 利用の流れ

履歴の「＋記録」から金額・カテゴリ・日付を入力して保存します。履歴の取引をタップすると編集・削除できます。上部の月表示で指定月へ移動し、集計・予算の月も共有します。目標の積立額は現在値を手入力し、収支とは別管理です。

「設定」でJSONを書き出し、アプリ外へ保管してください。JSONは暗号化されません。復元は全文検証と件数確認後の全体置き換えです。復元前に現在データの書き出しを行えます。アプリのデータ消去・アンインストールで内部記録は消えるため、日常利用前に実機で書き出し・復元を確認してください。

デバッグAPKはAndroid StudioのRunまたは `adb install -r app/build/outputs/apk/debug/app-debug.apk` で検証端末にインストールします。同じID・署名のまま更新し、検証のために日常データのあるアプリを削除しないでください。署名鍵はGitへ保存しません。

採用構成はAGP 9.4.0、Gradle 9.6.0、AGP内蔵Kotlin 2.2.10、Compose BOM 2026.09.00、Activity Compose 1.13.0。minSdk 26、compileSdk / targetSdk 37。バージョンは `gradle/libs.versions.toml` とWrapperに固定しています。環境と検証結果は [docs/toolchain.md](docs/toolchain.md) を参照してください。

## ファイルの役割

| ファイル | 内容 |
|---|---|
| app/src/main/java/jp/local/okozukai/ui/ | Composeの画面と、入力・処理状態を制御するViewModel |
| app/src/main/java/jp/local/okozukai/data/local/ | Roomのテーブル・SQL・新規DBの初期カテゴリ |
| app/src/main/java/jp/local/okozukai/data/repository/ | 保存・編集・集計・復元の整合性とトランザクション |
| app/src/main/java/jp/local/okozukai/data/backup/ | JSON契約・厳密な検証・容量制限・ファイルストリーム処理 |
| app/src/main/java/jp/local/okozukai/model/ | 金額・日付・文字数など画面に依存しない検証 |
| app/schemas/ | バージョン管理するRoomのschema JSON |
| [AGENTS.md](AGENTS.md) | 実装するAIの作業方針、守るルール、完了条件 |
| [docs/requirements.md](docs/requirements.md) | 機能の範囲、計算ルール、軽快さの目標 |
| [docs/architecture.md](docs/architecture.md) | Kotlin等の役割、構成、環境準備 |
| [docs/data-model.md](docs/data-model.md) | Roomのテーブル、型、制約、集計方法 |
| [docs/screens.md](docs/screens.md) | 画面の内容、入力操作、状態表示 |
| [docs/backup-format.md](docs/backup-format.md) | JSON形式、検証、書き出しと復元 |
| [docs/implementation-plan.md](docs/implementation-plan.md) | 実装する順番と各段階の完了条件 |
| [docs/acceptance-tests.md](docs/acceptance-tests.md) | 計算、保存、復元、実機動作の確認例 |
| [docs/decisions.md](docs/decisions.md) | ユーザー確定事項、初期仕様案、未決事項 |
| [docs/references.md](docs/references.md) | 設計時に確認した公式資料 |
| [docs/toolchain.md](docs/toolchain.md) | P0の環境調査結果、環境導入と検証の手順 |
| [examples/backup-v1.json](examples/backup-v1.json) | 架空データを使ったバックアップの例 |

## 最初に知っておくこと

- 画面、収支計算、端末内保存をKotlinで実装します。サーバー用言語は初期版にはありません。
- アプリの収支は記録した金額の差です。銀行口座や財布の残高ではありません。
- 初期のカテゴリ名、対応Androidの下限、配色などは変更可能な仕様案です。
- 開発PCとAPI 37エミュレーターで検証しています。Android実機の機種・OS・メモリは未確認です。
- Androidのデータ消去・アンインストールに備えるため、手動バックアップを用意します。

## 公開に向けて

初期版は自分用です。一般公開時もローカル保存型のまま配布できます。公開時点でアプリ名、applicationId、署名、配布方法、対象API、ストアの最新条件とデータの説明を整えます。複数端末同期が必要になった場合に、別途サーバー構成を検討します。
