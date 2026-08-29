package club.code2create.mcremote.client;

/** Receives the human-visible pairing command before polling starts. */
@FunctionalInterface
public interface PairingListener {

    void pairingRequired(PairingChallenge challenge);
}
