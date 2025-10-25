package io.fluxgate.core.adaptive;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.UnaryOperator;

/**
 * Maintains an exponentially weighted moving average of local QPS and a cluster-wide
 * estimate built from optional gossip samples. The estimator is lock-free and safe for
 * concurrent use by the limiter hot-path.
 */
public final class EwmaTrafficEstimator {

    private static final double ALPHA = 0.2d;
    private static final double MIN_QPS = 1d;
    private static final long SAMPLE_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    private final DoubleAdder pendingPermits = new DoubleAdder();
    private final AtomicLong lastSampleNanos = new AtomicLong();
    private final AtomicReference<AdaptiveState> state =
            new AtomicReference<>(new AdaptiveState(MIN_QPS, MIN_QPS, 0L));

    /**
     * Returns the current adaptive state after flushing any pending local samples for the
     * provided timestamp.
     */
    public AdaptiveState observe(long nowNanos) {
        flushSamples(nowNanos);
        return state.get();
    }

    /**
     * Records locally processed permits. Samples are accumulated and then folded into the
     * EWMA once the sampling window elapses. The updated adaptive state is returned for
     * observability purposes.
     */
    public AdaptiveState recordLocalPermits(long permits, long nowNanos) {
        if (permits > 0) {
            pendingPermits.add(permits);
        }
        flushSamples(nowNanos);
        return state.get();
    }

    /**
     * Ingests a gossip supplied cluster-wide QPS estimate. This method is non-blocking and
     * optional – callers that do not participate in gossip can simply avoid invoking it.
     */
    public AdaptiveState ingestClusterEstimate(double clusterQps, long nowNanos) {
        double sanitized = Math.max(MIN_QPS, clusterQps);
        updateState(current -> {
            double smoothed = smooth(current.clusterQps(), sanitized);
            double cluster = Math.max(smoothed, current.localQps());
            return new AdaptiveState(current.localQps(), cluster, nowNanos);
        });
        return state.get();
    }

    private void flushSamples(long nowNanos) {
        long last = lastSampleNanos.get();
        long elapsed = nowNanos - last;
        if (elapsed < SAMPLE_INTERVAL_NANOS) {
            return;
        }
        if (!lastSampleNanos.compareAndSet(last, nowNanos)) {
            return;
        }

        double intervalSeconds = elapsed <= 0 ? 1d : elapsed / 1_000_000_000d;
        double permits = pendingPermits.sumThenReset();
        double sampledQps = permits / intervalSeconds;
        if (Double.isNaN(sampledQps) || Double.isInfinite(sampledQps)) {
            sampledQps = MIN_QPS;
        }
        double sanitized = Math.max(MIN_QPS, sampledQps);
        updateState(current -> {
            double local = smooth(current.localQps(), sanitized);
            double cluster = Math.max(local, current.clusterQps());
            return new AdaptiveState(local, cluster, nowNanos);
        });
    }

    private void updateState(UnaryOperator<AdaptiveState> transformer) {
        AdaptiveState current;
        AdaptiveState updated;
        do {
            current = state.get();
            updated = transformer.apply(current);
        } while (!state.compareAndSet(current, updated));
    }

    private double smooth(double previous, double sample) {
        double prev = Math.max(MIN_QPS, previous);
        double next = ALPHA * Math.max(MIN_QPS, sample) + (1 - ALPHA) * prev;
        if (Double.isNaN(next) || Double.isInfinite(next)) {
            return MIN_QPS;
        }
        return next;
    }

    /**
     * Backwards compatible accessor used by callers that only require the share factor.
     */
    public double instanceShare() {
        return state.get().share();
    }

    /**
     * Immutable snapshot of the adaptive state. The share represents the fraction of the
     * global limit this instance should enforce.
     */
    public record AdaptiveState(double localQps, double clusterQps, long updatedAtNanos) {

        public double share() {
            double denominator = Math.max(MIN_QPS, clusterQps);
            double ratio = localQps / denominator;
            if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
                return 1d;
            }
            if (ratio <= 0d) {
                return MIN_QPS / denominator;
            }
            return Math.min(1d, ratio);
        }

        public Map<String, Double> debugView() {
            return Map.of(
                    "localQps", localQps,
                    "clusterQps", clusterQps,
                    "share", share()
            );
        }
    }
}
