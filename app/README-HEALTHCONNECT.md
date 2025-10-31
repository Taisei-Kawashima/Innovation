このリポジトリは Compose UI のダッシュボード構成と、Health Connect と連携するための差し替え可能なリポジトリ設計を含みます。

現在の状態
- `MainActivity.kt` にモック実装（`MockHealthRepository`）を組み込み済みで、UI はモックデータで動作します。
- `HealthConnectRepository.kt` をプレースホルダとして追加済み（現状はモック値を返す）。

本物の Health Connect SDK を導入する手順
1. 依存関係の追加
   app/build.gradle.kts の dependencies に以下を追加してください（バージョンは最新を参照して置き換えてください）。

   implementation("androidx.health:health-connect-client:1.0.0")

   あるいは Gradle libs.versions.toml を使う場合はライブラリ定義を追加して参照してください。

2. AndroidManifest の変更
   Health Connect を使う場合、以下の権限が必要になる場合があります（Health Connect の仕様に従ってください）。

   <uses-permission android:name="android.permission.BODY_SENSORS" />

   また、targetSdk に合わせた追加設定を Health Connect のドキュメントで確認してください。

3. ランタイム権限とユーザ同意
   Health Connect はユーザの健康データを扱うため明示的な同意が必要です。Android 側で Health Connect の permission ポップアップを表示する実装が必要になります。

   公式ドキュメント（参考）: https://developer.android.com/guide/health-and-fitness/health-connect

4. 実装の差し替え方
   `HealthConnectRepository.kt` の実装を編集して Health Connect Client を呼び出し、`HealthData` にマッピングして返してください。

   例（擬似コード）:

   // build.gradle に依存追加後に有効
   import androidx.health.connect.client.HealthConnectClient
   import androidx.health.connect.client.records.HeartRate
   import androidx.health.connect.client.records.Steps
   import androidx.health.connect.client.records.SleepSession
   import androidx.health.connect.client.permission.HealthPermission
   // ...

   class HealthConnectRepositoryImpl(private val context: Context): HealthRepository {
       private val client by lazy { HealthConnectClient.getOrCreate(context) }

       override suspend fun fetchHealthData(): HealthData {
           // 取得したレコードを集計して HealthData を作成
       }
   }

5. MainActivity 側での差し替え
   現状、`HealthDashboard()` のデフォルトで `MockHealthRepository()` を使うようになっています。
   実行時にコンテキスト依存の実実装を渡すには、`MainActivity` の setContent 内で `HealthDashboard(repository = HealthConnectRepositoryImpl(this))` のように渡します。

品質ゲート / テスト
- このコミットではビルドエラーが出ないよう、外部 SDK の import はまだ行っていません。
- SDK を追加後、プロジェクトをクリーンしてビルドしてください。

ビルドコマンド (Windows / cmd.exe):

> .\\gradlew.bat clean assembleDebug

注意点
- Health Connect は実端末と Google Play サービス（または対応する実装）が必要です。エミュレータでは一部機能が使えない場合があります。
- ユーザの健康データを扱うため、プライバシーと Data Minimization を遵守してください。

次のステップ（私が代行できます）
- あなたの許可があれば `app/build.gradle.kts` に依存を追加し、`HealthConnectRepository.kt` に Health Connect を使った実装を追加します。ただしビルド時にネットから依存を取得するため、実行環境でのビルドが必要です。
- また、権限ダイアログの Compose ベースの UI 実装も追加できます。

