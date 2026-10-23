# Create: Train Shadows

Create の列車(`CarriageContraptionEntity`)の下に、車両の足跡の形をした影を描く NeoForge 用のアドオン mod です。Create 本体は改造しません。

Create's trains have no shadow of their own. This client-side addon draws one under every carriage, in the shape of its footprint, instead of vanilla's round blob.

## 動作環境 / Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x (`[21,)`) |
| Create | 6.0.8 以降の 6.x (`[6.0.8,7)`) |
| Flywheel | 1.x (Create に同梱のもので動きます) |

クライアント専用です。サーバーには入れなくて構いません。

## 機能 / Features

- 列車の足跡の形で、地面に影を描きます。ヨー(向き)や坂での傾きに合わせます。
- 影の縁は、外側に向かって少しずつ薄くなります。
- 列車の下に張り出したブロックがあれば、その下の床にも影を落とします。
- 列車の横にある壁(トンネルの壁、ホームの縁、地面の段差)の側面も、近いほど濃く暗くします。
- Create の線路(坂とカーブを含む)の上にも影を描きます。線路のモデルの面をもう一度、黒い半透明で重ねて描くので、レールと枕木に影が乗ります。
- 動画設定の「エンティティの影」を切ると、影は描きません。
- シェーダーパックの使用中は、影を描きません(パック側の影が使われるため)。

## 設定 / Configuration

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
| `trackShadowMode` | `MODEL`(線路のモデルに影を重ねる)、`PLATE`(レールの高さに板を敷く、軽い)、`OFF` |

## ビルド / Building

Java 21 が必要です。

```
./gradlew build
```

jar は `build/libs/` にできます。ファイル名は `create_train_shadows-neoforge-1.21.1-<version>.jar` です。開発用のクライアントは `./gradlew runClient` で起動します。実行する Create の版は `-Pcreate_version_runtime=6.0.8-169` のように選べます(コンパイルは 6.0.8 に対して行います)。

テストは `./gradlew test` です。影の計算は `core` パッケージにあり、Minecraft の型を使わないので、単体テストで確かめています。

## ブランチ / Branches

Minecraft のバージョンとローダーごとにブランチを分けます。名前は `mc<バージョン>-<ローダー>` です。

- `mc1.21.1-neoforge`: 現在のブランチ
- `mc1.20.1-forge`: 予定

## ライセンス / License

[MIT License](LICENSE.md)
