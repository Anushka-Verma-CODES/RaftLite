package com.raftlite.monitoring;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts what actually happened during a simulation.
 *
 * Every counter is updated from a real simulator event by
 * {@link SimulationMonitor}; nothing here is preset or hardcoded. The class
 * is thread-safe because heartbeat and timeout events arrive from the
 * simulator's scheduler threads.
 */
public final class StatisticsTracker {

    private final AtomicLong totalElections = new AtomicLong();
    private final AtomicLong successfulElections = new AtomicLong();
    private final AtomicLong failedElections = new AtomicLong();
    private final AtomicLong leaderChanges = new AtomicLong();
    private final AtomicLong heartbeatsSent = new AtomicLong();
    private final AtomicLong heartbeatsReceived = new AtomicLong();
    private final AtomicLong electionTimeouts = new AtomicLong();
    private final AtomicLong nodeFailures = new AtomicLong();
    private final AtomicLong nodeRecoveries = new AtomicLong();
    private final AtomicLong messagesBlocked = new AtomicLong();

    // Duration statistics are updated together, so they are guarded by "this"
    // instead of one atomic per field.
    private long electionDurationSamples;
    private long totalElectionDurationMillis;
    private long minElectionDurationMillis = Long.MAX_VALUE;
    private long maxElectionDurationMillis;
    private long lastElectionDurationMillis;

    public void recordElectionStarted() {
        totalElections.incrementAndGet();
    }

    /**
     * Records an election that produced a leader.
     *
     * @param durationMillis how long the election took, must not be negative
     * @throws IllegalArgumentException if durationMillis is negative
     */
    public void recordElectionSucceeded(long durationMillis) {

        if (durationMillis < 0) {
            throw new IllegalArgumentException(
                    "durationMillis must not be negative: " + durationMillis);
        }

        successfulElections.incrementAndGet();

        synchronized (this) {
            electionDurationSamples++;
            totalElectionDurationMillis += durationMillis;
            minElectionDurationMillis =
                    Math.min(minElectionDurationMillis, durationMillis);
            maxElectionDurationMillis =
                    Math.max(maxElectionDurationMillis, durationMillis);
            lastElectionDurationMillis = durationMillis;
        }
    }

    public void recordElectionFailed() {
        failedElections.incrementAndGet();
    }

    /** Records that the cluster is now led by a different node. */
    public void recordLeaderChange() {
        leaderChanges.incrementAndGet();
    }

    public void recordHeartbeatSent() {
        heartbeatsSent.incrementAndGet();
    }

    public void recordHeartbeatReceived() {
        heartbeatsReceived.incrementAndGet();
    }

    public void recordElectionTimeout() {
        electionTimeouts.incrementAndGet();
    }

    public void recordNodeFailure() {
        nodeFailures.incrementAndGet();
    }

    public void recordNodeRecovery() {
        nodeRecoveries.incrementAndGet();
    }

    public void recordMessageBlocked() {
        messagesBlocked.incrementAndGet();
    }

    public long getTotalElections() {
        return totalElections.get();
    }

    public long getSuccessfulElections() {
        return successfulElections.get();
    }

    public long getFailedElections() {
        return failedElections.get();
    }

    public long getLeaderChanges() {
        return leaderChanges.get();
    }

    public long getHeartbeatsSent() {
        return heartbeatsSent.get();
    }

    public long getHeartbeatsReceived() {
        return heartbeatsReceived.get();
    }

    public long getElectionTimeouts() {
        return electionTimeouts.get();
    }

    public long getNodeFailures() {
        return nodeFailures.get();
    }

    public long getNodeRecoveries() {
        return nodeRecoveries.get();
    }

    public long getMessagesBlocked() {
        return messagesBlocked.get();
    }

    public synchronized long getElectionDurationSamples() {
        return electionDurationSamples;
    }

    public synchronized long getTotalElectionDurationMillis() {
        return totalElectionDurationMillis;
    }

    public synchronized long getMinElectionDurationMillis() {
        return electionDurationSamples == 0 ? 0 : minElectionDurationMillis;
    }

    public synchronized long getMaxElectionDurationMillis() {
        return maxElectionDurationMillis;
    }

    public synchronized long getLastElectionDurationMillis() {
        return lastElectionDurationMillis;
    }

    public synchronized double getAverageElectionDurationMillis() {
        return electionDurationSamples == 0
                ? 0.0
                : (double) totalElectionDurationMillis / electionDurationSamples;
    }

    /** A consistent, immutable snapshot of every counter. */
    public synchronized SimulationStatistics snapshot() {

        return new SimulationStatistics(
                totalElections.get(),
                successfulElections.get(),
                failedElections.get(),
                leaderChanges.get(),
                heartbeatsSent.get(),
                heartbeatsReceived.get(),
                electionTimeouts.get(),
                nodeFailures.get(),
                nodeRecoveries.get(),
                messagesBlocked.get(),
                electionDurationSamples,
                totalElectionDurationMillis,
                electionDurationSamples == 0 ? 0 : minElectionDurationMillis,
                maxElectionDurationMillis,
                lastElectionDurationMillis);
    }

    /** Clears every counter so the tracker can be reused. */
    public synchronized void reset() {

        totalElections.set(0);
        successfulElections.set(0);
        failedElections.set(0);
        leaderChanges.set(0);
        heartbeatsSent.set(0);
        heartbeatsReceived.set(0);
        electionTimeouts.set(0);
        nodeFailures.set(0);
        nodeRecoveries.set(0);
        messagesBlocked.set(0);

        electionDurationSamples = 0;
        totalElectionDurationMillis = 0;
        minElectionDurationMillis = Long.MAX_VALUE;
        maxElectionDurationMillis = 0;
        lastElectionDurationMillis = 0;
    }

    @Override
    public String toString() {
        return snapshot().toDisplayString();
    }
}
