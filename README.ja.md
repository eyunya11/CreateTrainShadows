# Create: Train Shadows

[English](README.md) | 日本語

Create の列車(`CarriageContraptionEntity`)の下に、車両の足跡の形をした影を描く、クライアント専用の NeoForge アドオン mod です。バニラの丸い影の代わりに、車両の形の影を描きます。Create 本体は改造しません。

Create の列車には、もともと影がありません。このアドオンがそれを足します。

## 動作環境

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x (`[21,)`) |
| Create | 6.0.8 以降の 6.x (`[6.0.8,7)`) |
| Flywheel | 1.x (Create に同梱のもので動きます) |

クライアント専用です。サーバーには入れなくて構いません。

## 機能

- 車両の足跡の形で、地面に影を描きます。向きと、坂での傾きに合わせます。
- 影の縁は、外側に向かって少しずつ薄くなります。
- 列車の下に張り出したブロックと、そのブロックの下の床にも影を落とします。
- 列車の横にある壁(トンネルの壁、ホームの縁、地面の段差)の側面も、近いほど濃く暗くします。
- Create の線路(坂とカーブを含む)の上にも影を描きます。線路のモデルの面を黒い半透明でもう一度描くので、レールと枕木に影が乗ります。
- 動画設定の「エンティティの影」を切ると、影は描きません。
- シェーダーパックの使用中は、影を描きません。パックが列車を含めて世界の影を付けるためです。

## 設定

Mods → Create: Train Shadows → Config から変えられます。変更はその場で反映されます。設定ファイルは `config/create_train_shadows-client.toml` です。

| 項目 | 内容 |
|---|---|
| `enabled` | 影を描くかどうか |
| `opacity` | 影が最も濃い所の濃さ(バニラは 0.5) |
| `fadeDistance` | 影が完全に消える、カメラからの距離 |
| `fadeHeight` | 列車が地面から高いとき、影が消えるまでの高さ |
| `graceHeight` | 地面に接しているとみなす高さ(Create は列車を線路の 1 ブロック上に置きます) |
| `margin` | 影が列車の輪郭の外へ広がる距離 |
| `softness` | 影の縁がぼやける幅 |
| `sideReach` / `sideOpacity` | 壁の影の届く距離と濃さ(`sideReach = 0` で壁には描きません) |
| `trackShadowMode` | `MODEL`(線路のモデルに影を重ねる)、`PLATE`(レールの高さに板を敷く。軽い)、`OFF` |

## ビルド

Java 21 が必要です。

```
./gradlew build
```

jar は `build/libs/` に `create_train_shadows-neoforge-1.21.1-<version>.jar` という名前でできます。開発用のクライアントは `./gradlew runClient` で起動します。実行する Create の版は `-Pcreate_version_runtime=6.0.8-169` のように選べます(コンパイルは常に 6.0.8 に対して行います)。

テストは `./gradlew test` です。影の計算は `core` パッケージにあり、Minecraft の型を使わないので、単体テストで確かめています。

## ブランチ

Minecraft のバージョンとローダーごとにブランチを分けます。名前は `mc<バージョン>-<ローダー>` です。

- `mc1.21.1-neoforge`: このブランチ
- `mc1.20.1-forge`: 予定

## ライセンス

[MIT License](LICENSE.md)
