# Javaで動かすサンプル

このcheckoutのクライアントと一緒にbuildされるサンプルです。
初めて使う場合は[READMEの導入手順](../README.md)を進めてください。
[短い作例](../docs/java-examples_ja.md)と[Java APIリファレンス](../docs/java-api-reference_ja.md)も参照できます。

## 一つずつ試す

```bash
./gradlew --console=plain demo --args=menu
```

初回はtokenの扱いを`save`か`memory`から選び、必要なら表示されたpairingコマンドをMinecraftで実行します。
メニューでは下のIDを入力します。`r`で取得済みtokenを使って再接続、`x`で終了します。
サーバーから操作を拒否されてもメニューは続くので、設定を調べてから別のサンプルを試せます。
通信切断・timeoutは処理結果が不明な場合があり、書き込みを自動再試行しません。

コードを編集した場合は一度終了し、同じコマンドで起動し直すと再コンパイルされます。
`save`で使えるstoreへ保存できていれば、期限内tokenを再利用できます。
`memory`はプロセス終了時にtokenを失うため、起動し直すとpairingが必要です。

| ID | 読むコード | 期待する結果 | world変更と片付け |
| --- | --- | --- | --- |
| `my-world` | [MyWorld.java](src/main/java/club/code2create/mcremote/examples/MyWorld.java) | 編集した言葉をチャットに表示 | 変更なし |
| `hello` | [Demo00Hello.java](src/main/java/club/code2create/mcremote/examples/Demo00Hello.java) | 固定の挨拶をチャットに表示 | 変更なし |
| `01` | [Demo01SetAndReadBlock.java](src/main/java/club/code2create/mcremote/examples/Demo01SetAndReadBlock.java) | 金ブロックを置き、読み返したIDが一致 | 1ブロックを変更。`01-cleanup`でその位置をairにする |
| `02` | [Demo02Protocol23Tour.java](src/main/java/club/code2create/mcremote/examples/Demo02Protocol23Tour.java) | catalog・位置・eventを表示し、看板とflame particleを出す | 看板1個。`02-cleanup`でairにする。particleは自然に消える |
| `inspect` / `03` | [Demo03InspectWorld.java](src/main/java/club/code2create/mcremote/examples/Demo03InspectWorld.java) | 現在位置・権限・使えるブロック名を表示 | 読み取り専用 |
| `events` / `04` | [Demo04Events.java](src/main/java/club/code2create/mcremote/examples/Demo04Events.java) | 約10秒間、ツルハシ右クリック・チャット等のeventを読む | 読み取り専用。常駐しない |
| `axis-flat` | [AxisFlat.java](src/main/java/club/code2create/mcremote/examples/AxisFlat.java) | 平面と色分けしたXYZ軸を描く | 下記の広い範囲を変更。`axis-flat-clear`は軸だけ除去 |

`01-cleanup`と`02-cleanup`は、以前そこにあったブロック・看板の内容を復元する操作ではありません。
各サンプルは途中で拒否・切断されると、そこまでの変更が残ることがあります。

接続せずに一覧だけを見るには`./gradlew demo --args=list`、一つだけ動かすには
`./gradlew --console=plain demo --args='--save inspect'`のように指定します。

## 接続先と作業場所

JDK 21または25と、protocol 23に対応したMcRemoteサーバーが必要です。
Javaの導入・切り替えは[miseを使うREADMEの手順](../README.md)に従います。以下の`./gradlew`は`mise exec -- ./gradlew`として実行してください。
既定のTCP接続先は`127.0.0.1:25575`です。
環境変数はプログラムを動かすターミナルで設定します。以下のhostは説明用です。

```bash
export MCREMOTE_HOST=example.local
export MCREMOTE_PORT=25575
export MCREMOTE_ORIGIN_X=300
export MCREMOTE_ORIGIN_Y=0
export MCREMOTE_ORIGIN_Z=300
```

建築原点の既定値は絶対座標`(300, 0, 300)`です。ブロックを変更するサンプルは`build.setOrigin`でこの原点を設定します。
原点を変えてもプレイヤーは移動しません。`inspect`は現在の接続の原点をそのまま表示します。

| サンプル | 原点X/Zが300のときの絶対位置 |
| --- | --- |
| `01` | `(302, 96, 302)` |
| `02` | `(304, 96, 304)`の看板と、その2ブロック上のparticle |
| `axis-flat`の床 | X/Z=`252..348`、Y=`62` |
| `axis-flat`の整地空間 | X/Z=`252..348`、Y=`63..127` |

これらのサンプルは原点Yを変えても絶対Y=96にブロックや看板を置きます。相対Yは`96 - origin.y`です。

### 整地する前に

**`axis-flat`は97×97の床と、高さ63〜127の全空間を書き換えます。完全復元にはworld backupが必要です。**
管理者に場所・範囲・backupを確認し、他の作品と重ならない原点を設定してから実行してください。

各層を最大64×64のtileに分け、上から順にairへ置き換えて床を作ります。
X/Z軸は原点から±40、Y軸は絶対Y=`63..127`、交点は絶対Y=96です。
負方向は破線、正方向は連続線になります。

| 表示 | ブロック |
| --- | --- |
| X軸 | diamond block |
| Y軸 | sea lantern（上端5ブロックはglowstone） |
| Z軸 | gold block |
| 原点 | emerald block |

## 認証情報の扱い

| 指定 | 動作 |
| --- | --- |
| 指定なし | 保存するかを既定値なしで選ぶ |
| `--save` | 利用可能な保護されたOS storeのtokenを読み、取得したtokenを保存する。有効期限は標準設定で発行から2時間 |
| `--no-save` | 永続storeへ触れず、このprocess内だけでtokenを保持する |
| `--forget` | 選択接続先のlocal tokenを削除し、接続せず終了する |

OS adapterはmacOS Keychain、Windows Credential Manager、Linux Secret Serviceを使います。
承認待ちpair codeの有効期限は120秒です。承認後のsession tokenの2時間とは別の期限です。
利用不能なら警告を出してmemoryへ戻り、平文ファイルへのfallbackはしません。
`--forget`はサーバー側のrevokeやlogoutではありません。

`menu`の`r`は保存先の同じstoreを使って接続を作り直します。tokenが有効なら再pairingせず、
接続時に決まる権限snapshotやevent ringを新しく取得します。建築原点は新しいhelloの値になるので、
書き込みサンプルはあらためて設定済みの原点を選びます。

## 現行プロトコルでの確認

[検証結果](../docs/compatibility_ja.md#2026-10-21のサンプル確認)に、実サーバーで確認できたことと自動テストの範囲を分けて記録しています。
サンプルの最低protocolは23.0.0、Java公開artifactはまだありません。
同期requestで実worldへ操作し、`inspect`と`events`は読み取りだけを行います。

```bash
./gradlew :examples:test
```

`ExampleProtocolCompatibilityTest`は、サンプルの本体をloopbackの23.2.1 peerへ接続します。
チャットのnull応答、block／sign／particle／eventの形、整地requestの設置数、拒否後も続くメニューを検証します。
共有fixtureは既存のvendor copyを読み、外部Minecraftサーバーは必要ありません。

各サンプルclassにも`main()`があります。IDEでこのGradle projectを読み込めば、各classから起動できます。
通常のJARへ外部依存を埋め込んでいないため、ターミナルの主導線はGradle wrapperです。
