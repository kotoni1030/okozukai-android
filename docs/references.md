# 技術判断の参考資料

確認日：2026-11-17。Android公式資料を中心に確認。具体的なSDK・ライブラリのバージョンと配布条件は、実装・公開する時点で再確認する。

| 公式資料 | 本設計との関係 |
|---|---|
| [Recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations) | UIとデータ処理の分離、Repository、ViewModel、Coroutines/Flowを参考にした |
| [Guide to app architecture](https://developer.android.com/topic/architecture) | 小さなアプリではUI層とデータ層を基本にし、必要性に応じて層を追加する判断 |
| [Compose UI Architecture](https://developer.android.com/develop/ui/compose/architecture) | 状態を表示し、ユーザー操作から状態を更新する画面構成 |
| [Save data in a local database using Room](https://developer.android.com/training/data-storage/room) | 端末内の構造化データ保存にRoomを採用 |
| [Data and file storage overview](https://developer.android.com/training/data-storage) | アプリ内部DBと、利用者が保存する共有ファイルの違い |
| [Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files) | SAFによる保存先/ファイル選択とURIでの読書き |
| [Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup) | 自動バックアップと端末移行の設定を手動バックアップとは別に扱う |

これらはAPIや設計上の参考資料。金額上限、カテゴリ初期値、予算の計算ルール、画面構成、復元方式、性能目標などは、このアプリ向けの初期設計判断である。

バージョン番号を資料作成時点の推測で固定せず、P0で実際に組み合わせてビルドできた構成を `docs/toolchain.md` に記録する。APIや推奨ライブラリの変化だけを理由に、動く実装を無目的に置き換えない。
