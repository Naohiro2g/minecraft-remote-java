package club.code2create.mcremote.client;

/** Receives a notice each time the client opened a new connection after the previous one closed. */
@FunctionalInterface
public interface ReconnectListener {

    /**
     * Called on the thread whose call reconnected, before that call is sent. Other calls wait
     * until this returns, so keep it short. It may call this client.
     */
    void reconnected(ReconnectEvent event);
}
