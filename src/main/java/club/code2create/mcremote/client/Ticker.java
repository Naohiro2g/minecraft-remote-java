package club.code2create.mcremote.client;

import java.time.Duration;
import java.util.function.LongSupplier;

/** The client's monotonic clock and waits, replaceable by tests. */
interface Ticker {

    Ticker SYSTEM = of(System::nanoTime);

    long nanoTime();

    void sleep(long nanos) throws InterruptedException;

    /** A clock read from {@code clock} whose waits use real time. */
    static Ticker of(LongSupplier clock) {
        return new Ticker() {
            @Override
            public long nanoTime() {
                return clock.getAsLong();
            }

            @Override
            public void sleep(long nanos) throws InterruptedException {
                if (nanos > 0) {
                    Thread.sleep(Duration.ofNanos(nanos));
                }
            }
        };
    }
}
