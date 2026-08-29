package club.code2create.mcremote.client;

import java.time.Duration;

/** Human-visible information for approving a pairing in Minecraft. */
public record PairingChallenge(String pairCode, String displayCode, Duration expiresIn) {

    public String command() {
        return "/mcremote pair " + displayCode;
    }
}
