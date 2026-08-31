package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.CredentialStore;
import club.code2create.mcremote.client.MinecraftRemoteException;
import club.code2create.mcremote.client.ServerTarget;
import java.io.PrintStream;
import java.util.Optional;
import java.util.Scanner;

final class CredentialStoreSelector {

    private final ProtectedCredentialStoreProvider provider;
    private final PrintStream output;

    CredentialStoreSelector(ProtectedCredentialStoreProvider provider, PrintStream output) {
        this.provider = provider;
        this.output = output;
    }

    CredentialStore select(CredentialMode requested, Scanner input) {
        CredentialMode mode = requested == CredentialMode.PROMPT ? prompt(input) : requested;
        if (mode == CredentialMode.NO_SAVE) {
            output.println("認証情報は保存せず、このprocess内だけで使用します。");
            return CredentialStore.inMemory();
        }
        if (mode != CredentialMode.SAVE) {
            throw new IllegalArgumentException("接続用credential modeではありません: " + mode);
        }
        Optional<CredentialStore> protectedStore = provider.open();
        if (protectedStore.isPresent()) {
            return protectedStore.get();
        }
        output.println("保護されたOS資格情報storeを利用できません。");
        output.println("認証情報はこのprocess内だけで使用するため、次回もpairingが必要です。");
        return CredentialStore.inMemory();
    }

    void forget(ServerTarget target) throws MinecraftRemoteException {
        Optional<CredentialStore> protectedStore = provider.open();
        if (protectedStore.isEmpty()) {
            output.println("保護されたOS資格情報storeを利用できないため、local tokenを確認できませんでした。");
            return;
        }
        protectedStore.get().clear(target);
        output.println("選択した接続先のlocal session tokenを削除しました。");
        output.println("server上のtokenをrevokeまたはlogoutした操作ではありません。");
    }

    private CredentialMode prompt(Scanner input) {
        output.println("session tokenを保護されたOS資格情報storeへ保存しますか？");
        output.println("  save    次回の実行で再利用する");
        output.println("  memory  このprocess内だけで使用する");
        while (true) {
            output.print("選択してください [save/memory]: ");
            if (!input.hasNextLine()) {
                throw new IllegalArgumentException("saveまたはmemoryの選択が必要です");
            }
            String answer = input.nextLine().trim();
            if ("save".equalsIgnoreCase(answer)) {
                return CredentialMode.SAVE;
            }
            if ("memory".equalsIgnoreCase(answer)) {
                return CredentialMode.NO_SAVE;
            }
            output.println("saveまたはmemoryを入力してください（既定値はありません）。");
        }
    }
}
