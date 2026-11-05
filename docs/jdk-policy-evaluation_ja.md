# Java Clientの標準JDKとCIの評価

2026-11-05に、miseの既定vendor変更を受けてJava Clientの案内・開発・CIを点検しました。
評価の結論は、**標準環境と必須CIをTemurin Java 21／25へ揃える**です。

## 判断の根拠

### 既存方針と選定経緯

knowledgeの[決定2026-10-29-01](https://github.com/Naohiro2g/mc-remote-knowledge/blob/a6605fb5d2fff576d87f2ec5b15f0864e9b7c097/00-hub/DECISIONS_ja.md)は、
TemurinをOSS利用者への案内、我々の開発・検証・VPSへ適用する既決方針です。
Java Clientの[設計スポーク](https://github.com/Naohiro2g/mc-remote-knowledge/blob/a6605fb5d2fff576d87f2ec5b15f0864e9b7c097/16-java-client/README_ja.md)は、
build・test・実行にmiseで選ぶJDK 21か25を使い、生成classfileのtargetを21としています。

このrepoのCorretto指定は、同方針より前のbootstrap commit `4300c5de42cfb8c731361de463f5e4a8a15f402e`
（2026-08-29）から残っていました。読んだSSOT、repoの文書、導入commitには、Java ClientだけCorrettoを
標準とする理由や、CIを意図的な別vendorの互換試験にする方針は見つかりませんでした。

### 技術要件と提供条件

- TemurinとCorrettoはいずれもOpenJDK由来で、各提供元はJava SE TCKの適合を公表しています。
  TemurinはAQAvit検証も公表しています。ただし、この事実だけで両者の性能・安全性・個別アプリの挙動が
  同一とは判断しません（[Adoptium](https://adoptium.net/temurin/)、[AWS FAQ](https://aws.amazon.com/corretto/faqs/)）。
- このrepoで必要な21／25を使えることは、Temurinでのlocal buildとCorrettoでの既存CIの両方で確認できています。
  setup-javaも[両distributionを扱えます](https://github.com/actions/setup-java#supported-distributions)。
- coreのbuildにはJacksonを使い、sourceからvendor固有APIへの依存は見つかりませんでした。
  API文書の生成にはJDKのdoclet APIを使っています。examplesのOS資格情報storeはJNAを含み、
  OSとnative backendの差は別の検証軸です。vendorを揃えるだけでWindows／macOSの実機確認を代替しません。
- Java ClientとMinecraftサーバーは別processです。接続互換性を決めるのはProtocolであり、
  同じJDK vendorを使うことをwireの接続条件にしません。

## 選択肢の比較

| 選択肢 | 得られるもの | 費用・不足 | 評価 |
| --- | --- | --- | --- |
| localはTemurin、必須CIはCorrettoだけ | 別vendorでの互換性を継続観測できる | 案内した標準環境をCIで継続検証できず、既決方針の開発・検証環境とずれる | 必須CIの構成には採らない |
| local・必須CIともTemurin 21／25 | 案内と同じvendorで変更を継続検証でき、既存方針と一致する | 継続的なvendor横断の観測は減る | 現在の標準に採る |
| TemurinとCorrettoの21／25をすべて必須CIにする | 標準環境の検証と別vendorの互換試験を両立できる | 同じsuiteが2 jobから4 jobへ増え、障害の切り分けと保守の軸も増える | 別vendor対応の要求・不具合が具体化した際に追加を検討する |

この選択は、Temurinの性能がCorrettoより優れているという判断ではありません。比較benchmarkは実施していません。
標準の利用手順を自動検証へ接続し、少ない構成で再現・調査できることを優先したrepo側の適用判断です。
通常のCIにCorrettoを残すこと自体を禁止する新しい横断方針にはしません。

## 反映する範囲

- `mise.toml`：系列の`java = "21"`を維持し、`java.shorthand_vendor = "temurin"`を明示。
  mise自体の既定や個人のglobal設定だけに標準vendorを委ねず、`java@25`にも同じrepo設定を使う。
- `.github/workflows/ci.yml`：JDK 21／25のmatrixを維持し、下のmise経由の構成へ変更。
- README・互換性文書：Temurin Java 21／25、最新の正式パッチ推奨、実際の検証版を区別して案内する。
- Gradle：classfile target 21を維持する。vendorでbuildを拒否する制約は追加しない。
  このCI選定から、他のJava SE実装で利用できないという結論は導かない。

パッチ更新の運用は既存のknowledge方針を参照し、この評価で別の周期・自動更新手順を定義しません。

## CIでもmiseを使う評価

miseは[公式のCI用Action](https://mise.jdx.dev/continuous-integration.html)を提供しています。
このrepoでは、`setup-java`で同じvendorを重ねて指定する構成より、CIでも同じ`mise.toml`を読む構成を採ります。
Javaの系列はmatrixの検証軸として残し、配布元の選択をrepoのmise設定へ集約できるためです。

構成は次のとおりです。

- `jdx/mise-action@v5`でmiseとJDKを用意する。mise自体は、手元で設定解決を確認した`2026.10.25`に固定する。
- job全体の`MISE_JAVA_VERSION`へ21／25を渡し、導入時と後続の`mise exec`で同じ系列を使う。
  repoの`mise.toml`をActionの入力で書き換えない。cache keyにも系列を含める
  （[公式のmatrix構成](https://github.com/jdx/mise-action#matrix-builds)）。
- `mise --version`、`mise exec -- java -version`、`javac -version`をCIログへ出す。
- buildは手元と同じ`mise exec -- ./gradlew build --no-daemon`で行う。shell activationは不要。
- JDKのcacheはmise-action、Gradleのcacheは`gradle/actions/setup-gradle@v6`の`basic` providerで扱う。
  Gradle本体は引き続きrepoのwrapperを使う。

この選択ではmise本体の導入がCIにも必要になり、その固定版の更新も保守対象になります。
その費用に対して、このrepoでは既にmiseを標準としているため、案内とCIの設定を一緒に検証できる利点を優先します。

### 既定変更の時期と既存環境

vendor省略時の既定がOpenJDKからTemurinへ変わったのは
[mise 2026.10.5](https://github.com/jdx/mise/blob/main/release-notes/2026.10.5.md)です。
評価日の約1か月前の変更なので、「miseならTemurin」と過去の環境全体へ一般化しません。
設定の明示、CIのmise版、実際に動くJDKの表示をそれぞれ確認します。
導入済みの旧JDKは[既定変更だけでは置き換わりません](https://mise.jdx.dev/configuration/settings.html#java-shorthand-vendor)。
既存のOpenJDK／Correttoでの検証結果も、その環境での実績として保持します。

## 検証と限界

- `ef71f56af7e433f47c7427f32181bdbe88f1c86d`：Temurin 21.0.13／25.0.5で各76 testがPASSし、
  core／examplesのclassfile major 65、生成API一致を確認済みです（[検証記録](compatibility_ja.md#2026-11-04のstable追従slice)）。
- 同じcommitの変更前CIは、Corretto 21／25の両jobが2026-11-05にsuccessでした
  （[run 41989901747](https://github.com/Naohiro2g/minecraft-remote-java/actions/runs/41989901747)）。
  これはCorrettoでの実績として保持し、Temurin CIの実績へ読み替えません。
- 新しいjobと同じ`MISE_JAVA_VERSION=21`／`25`を指定し、各々
  `mise exec -- ./gradlew build --no-daemon`が成功しました。両系列で既存のcore 56＋examples 20件の
  成功結果をGradle build cacheから再利用しています。生成API一致、classfile major 65も確認しました。
- workflowのYAML構文と、mise-action／setup-gradleの入力名を公式Action定義で照合しました。
- CIをmise経由のTemurinへ変更した後のGitHub Actions実行は、push後に別途確認します。
  localのTemurinでPASSしていることと、GitHub上で新しいworkflowがPASSすることを区別します。
