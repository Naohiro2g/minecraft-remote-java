# minecraft-remote-java

`minecraft-remote-java`は、現行Minecraft Remote ProtocolのJava Client Libraryです。

## McRemote SSOT

McRemote固有の設計判断の正本はGitHub上の`Naohiro2g/mc-remote-knowledge`です。

McRemote固有文脈に依存する作業に入る前に、agentはそのSSOT repositoryの関連文書を必ず読んでください。
対象にはarchitecture、protocol、release flow、contributor workflow、learning design、およびMcRemote固有の
判断理由に依存する挙動変更が含まれます。

最初に、knowledge repositoryのremote `main`から最新のdev agent runtime protocolだけを取得して指示として
読んでください。取得元ファイル全体を会話へ出力してはいけません。

```bash
protocol_source="$(mktemp)"
knowledge_commit="$(gh api repos/Naohiro2g/mc-remote-knowledge/commits/main -q .sha)"
gh api "repos/Naohiro2g/mc-remote-knowledge/contents/00-hub/dev-repo-protocol_ja.md?ref=$knowledge_commit" \
  -q .content | base64 -d > "$protocol_source"
if [ "$(grep -Fxc '<!-- BEGIN: DEV-AGENT-RUNTIME -->' "$protocol_source")" -ne 1 ] || \
   [ "$(grep -Fxc '<!-- END: DEV-AGENT-RUNTIME -->' "$protocol_source")" -ne 1 ]; then
  echo "dev agent runtime marker missing or duplicated" >&2
  exit 1
fi
printf 'knowledge commit: %s\n' "$knowledge_commit"
awk '/^<!-- BEGIN: DEV-AGENT-RUNTIME -->$/{reading=1;next} \
     /^<!-- END: DEV-AGENT-RUNTIME -->$/{reading=0} \
     reading' "$protocol_source"
```

SSOT repositoryへアクセスできない場合は作業を止め、その旨を明示してください。このrepository単体、assistant
memory、過去会話、ローカル推論から欠けたMcRemote文脈を補完してはいけません。

このファイルはSSOTを複製しません。複製はdriftを生みます。

- 関連スポーク: `16-java-client/`, `10-protocol/`

## このrepository固有の指示

- build／testにはrepositoryのGradle wrapperを使う。
- build／実行にはJDK 21または25を使い、Javaの選択・切り替えはmiseを正準とする。生成するclassfileのJava targetは21を維持し、root packageは`club.code2create.mcremote.client`とする。
- Maven coordinateの確定部分は`club.code2create.mcremote:minecraft-remote-client`とし、Gradle project名やrepository名から置換しない。
- 公開versionは未確定であり、Gradle project名を公開versionとみなさない。
- publish plugin、registry coordinate、release workflowはknowledge SSOTで確定するまで追加しない。
- wire formatに触れる前に`10-protocol`、Java bootstrap作業では`16-java-client`を読む。
- token、private endpoint、credential、operator-local設定をcommitしない。
- 横断する発見はlocal `NOTES_ja.md`へ一時捕捉し、session／slice終端にknowledge hubへescalateする。
- 自分が編集したpathだけを明示stageする。
