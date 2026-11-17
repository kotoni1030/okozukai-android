# 開発環境の確認記録

確認日：2026-11-17（日本時間）。P0の保存基盤まで作成し、主要機能を実装。最新結果は末尾「機能実装後の検証」を参照。

## このPCで確認できたこと

| 項目 | 結果 |
|---|---|
| CPU | Apple Silicon（`uname -m` は `arm64`） |
| Android Studio | Homebrewで2026.2.2.9（rabbit2-patch1、Apple Silicon用）を `/Applications/Android Studio.app` に導入済み |
| JDK | Studio同梱OpenJDK 25.0.4。絶対パスから `java -version` の成功を確認。システム全体のJAVA_HOMEは未変更 |
| Android SDK | `~/Library/Android/sdk` にAPI 37（android-37.0、revision 2）、Build Tools 36.0.0を確認 |
| CLI | SDK同梱adbとemulatorを絶対パスで使用。GradleはプロジェクトのWrapperを使用 |
| Homebrew | `/opt/homebrew/bin/brew` あり。android-studioの導入成功を確認 |
| 実機・エミュレーター | AVD `Medium_Phone_API_37.0`、Android 17 / API 37、arm64、RAM 4096MB、Emulator 37.2.13。実機は未確認 |
| プロジェクト | 単一appモジュール、Compose起動確認画面、開発用APKと試験用APKを作成 |

標準配置とPATHの調査結果であり、独自の場所にある環境の不存在までは断定しない。

## 次の手順

1. Room・KSPの導入とスキーマ出力設定を整え、P1のDB・支出保存・履歴表示へ進む。
2. 実機があれば機種・OS・メモリを確認し、性能計測の基準を確定する。
3. 仮の開発用ID `jp.local.okozukai.dev` とデバッグ署名は検証専用。日常利用版のID・署名方針を配布前に確定する。
4. 次のコマンドでビルド・単体試験・静的検査を実行する。

   ```sh
   ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
   ./gradlew :app:connectedDebugAndroidTest
   ```

5. 支出を1件保存して再起動後も残ることを、P1で検証する。

## 採用バージョンと理由

| 項目 | 採用値・理由 |
|---|---|
| AGP / Gradle | 9.4.0 / 9.6.0。公式の互換性表に合わせた安定版の組合せ |
| JDK / JVM出力 | 実行はStudio同梱25.0.4、アプリのJava・Kotlin出力は17。Gradle 9.6はJava 25で実行可能 |
| Kotlin / Compose Compiler | 2.2.10。AGP 9.4.0の公開POMの内蔵Kotlinと一致させ、別途kotlin-androidプラグインを適用しない |
| Compose BOM | 2026.09.00。Material 3とCompose UI試験の整合する版を管理 |
| Activity Compose | 1.13.0。Activity上でCompose画面を起動するため |
| JUnit / AndroidX Test | 4.13.2 / ext.junit 1.3.0・runner 1.7.0・Espresso 3.7.0。単体試験と端末試験の基盤 |
| min / compile / target SDK | 26 / 37 / 37。対応下限は初期案を維持し、導入済み安定SDKでコンパイル |

Version CatalogとGradle Wrapperに固定。Wrapperには公式配布物のSHA-256を設定。Room・KSP、Navigation、serializationは未導入で、必要な機能を作る段階で互換性を確認する。

## 公式資料で確認した情報

- [Android Studioのダウンロード](https://developer.android.com/studio)：PCへ導入する開発環境。実際の導入版は上表に記録。
- [AGP 8.13の互換性表](https://developer.android.com/build/releases/agp-8-13-0-release-notes)：環境導入前の調査候補。今回は同梱JDK 25と導入済みAPI 37に合わせ、9.4.0を採用した。
- [採用したAGP 9.4の互換性表](https://developer.android.com/build/releases/agp-9-4-0-release-notes)：API 37、Gradle 9.6.0、Build Tools 36.0.0。
- [GradleのJava互換性](https://docs.gradle.org/current/userguide/compatibility.html)：Java 25のGradle実行は9.1以降。
- [AGP内蔵Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)、[Activity安定版](https://developer.android.com/jetpack/androidx/releases/activity)、[AndroidX Test安定版](https://developer.android.com/jetpack/androidx/releases/test)。
- [Kotlinのリリース情報](https://kotlinlang.org/docs/releases.html)：安定版とプレビュー版を区別して採用する。
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)：Compose関連ライブラリの整合するバージョンをまとめて管理する仕組み。公式例は2026.09.00。採用時はSDK・Kotlin要件も確認する。

## 検証結果

`brew install --cask android-studio` は成功。`brew list --cask --versions android-studio` で導入版を確認。Studio同梱の `Contents/jbr/Contents/Home/bin/java -version` は成功。

初回設定は利用者が完了した旨を報告。その後SDK・AVDの導入をコマンドで確認。

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` は成功。単体試験は対象コードがないためNO-SOURCE（試験合格件数には含めない）。lintはエラー0、更新通知の警告3件。互換性を確認した構成のため、更新通知だけで一括更新しない。

最初の `connectedDebugAndroidTest` はエミュレーターがOFFLINEのため失敗。データ消去せず描画方式を切替えて再起動し、ADBサーバーの再起動後にオンライン接続を確認。

その後の試験は、Composeから推移的に取得したEspresso 3.5.0がAPI 37で削除された `InputManager.getInstance` を使うため失敗した。公式修正履歴を確認してEspresso 3.7.0を明示指定し、dependencyInsightでも置換を確認。

最終の `./gradlew :app:connectedDebugAndroidTest :app:lintDebug` は成功。`LaunchTest.launchShowsSetupScreen` 1件合格、失敗0、スキップ0。Compose画面の「開発準備中」が表示されることを検証した。端末のプロパティ取得に一時的なタイムアウト警告が出たが、試験は完了した。実機確認は未実施。

再現用のエミュレーター起動オプション：`-avd Medium_Phone_API_37.0 -no-window -no-snapshot -no-boot-anim -gpu swiftshader -feature -Vulkan`。接続回復には `adb kill-server` と `adb start-server` を実行した。

上記はP0時点の記録。性能は未測定。

## 機能実装後の検証

主要機能として収入・支出の追加編集削除、月別SQL集計、日別合計、50件単位の履歴、カテゴリ絞り込み、予算設定・前月コピー、複数貯金目標、SAF経由のJSON書き出し・復元を追加。

追加依存：Room 2.8.5（DBとスキーマ出力）、KSP 2.3.14（Roomコード生成）、Lifecycle 2.11.0（ViewModelと画面状態）、Coroutines 1.10.2（Kotlin 2.2.10に合わせた安定版）、serialization 1.9.0（同じKotlin世代でのJSON DTO）。Roomのschema v1は `app/schemas/` に収録。形式バージョンは別途1を維持する。

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest` は成功。

- JVM単体試験6件：金額、月境界、日付、Unicode文字数、予算差額、例JSON往復、不正JSON・重複キー・型・入れ子・容量拒否。
- エミュレーター試験6件：支出650円のUI保存と画面再生成後の表示、入力途中の画面再生成、DB再オープン後の保持、月別SQL集計、編集削除、同日同時刻120件のページング、外部キー制約、予算・目標の独立性、復元中の故障注入によるロールバック、復元後の旧世代入力の拒否。
- テスト用DBは本番DBと別名。架空データのみ使用。

バックアップの金額文字列がデコーダーに暗黙変換される点と、出力日時のナノ秒精度をミリ秒契約へ合わせる点は試験で検出して修正した。

未確認：実機でのSAFファイル選択・書き出し復元、ストレージ不足・I/O故障、文字200%・横画面・TalkBack、OSバックアップ除外の実機挙動、性能測定、API 26の端末試験、容量上限の合法データ全体往復。初回スキーマなのでv2への移行はまだ存在しない。
