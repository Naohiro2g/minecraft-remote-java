package club.code2create.mcremote.client;

import java.util.ArrayList;
import java.util.List;

/**
 * A clock that moves only when a test advances it. In auto-advancing mode a sleep moves the clock
 * by itself, so a reconnect policy runs to its end at once; every sleep is recorded.
 */
final class VirtualTicker implements Ticker {

    private final boolean autoAdvance;
    private final List<Long> sleeps = new ArrayList<>();
    private long now;

    VirtualTicker() {
        this(false);
    }

    VirtualTicker(boolean autoAdvance) {
        this.autoAdvance = autoAdvance;
    }

    @Override
    public synchronized long nanoTime() {
        return now;
    }

    @Override
    public synchronized void sleep(long nanos) throws InterruptedException {
        sleeps.add(nanos);
        long until = now + nanos;
        if (autoAdvance) {
            now = Math.max(now, until);
            notifyAll();
            return;
        }
        while (now - until < 0) {
            wait();
        }
    }

    synchronized void advance(long nanos) {
        now += nanos;
        notifyAll();
    }

    synchronized List<Long> sleeps() {
        return List.copyOf(sleeps);
    }
}
