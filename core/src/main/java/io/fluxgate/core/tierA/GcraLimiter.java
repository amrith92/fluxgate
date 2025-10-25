package io.fluxgate.core.tierA;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple lock-free GCRA (token bucket) implementation using CAS loops.
 */
public final class GcraLimiter {

    private final long periodNanos;
    private final double permitsPerSecond;
    private final long burstTokens;
    private final AtomicLong tatNanos = new AtomicLong(Long.MIN_VALUE);

    public GcraLimiter(double permitsPerSecond, double burstTokens) {
        this(Duration.ofSeconds(1).toNanos(), permitsPerSecond, burstTokens);
    }

    public GcraLimiter(long periodNanos, double permitsPerSecond, double burstTokens) {
        this.periodNanos = periodNanos;
        this.permitsPerSecond = permitsPerSecond;
        this.burstTokens = (long) Math.max(1, Math.ceil(burstTokens));
    }

    public Outcome tryAcquire(long nowNanos) {
        long period = (long) (periodNanos / permitsPerSecond);
        if (period <= 0) {
            period = 1;
        }
        long burstMultiplier = Math.max(0L, burstTokens - 1);
        long burstAllowance;
        if (burstMultiplier == 0) {
            burstAllowance = 0;
        } else if (burstMultiplier > Long.MAX_VALUE / period) {
            burstAllowance = Long.MAX_VALUE;
        } else {
            burstAllowance = period * burstMultiplier;
        }
        while (true) {
            long lastTat = tatNanos.get();
            long tat = lastTat == Long.MIN_VALUE ? nowNanos : lastTat;
            long newTat = Math.max(tat, nowNanos) + period;
            long allowAt = tat - burstAllowance;
            if (nowNanos < allowAt) {
                return new Outcome(false, allowAt - nowNanos);
            }
            if (tatNanos.compareAndSet(lastTat, newTat)) {
                return new Outcome(true, 0);
            }
        }
    }

    public record Outcome(boolean allowed, long retryAfterNanos) {
        public boolean allowed() {
            return allowed;
        }

        public long retryAfterNanos() {
            return retryAfterNanos;
        }
    }
}
