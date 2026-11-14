package com.raftlite.monitoring;

/**
 * An immutable snapshot of the statistics collected during a simulation.
 *
 * Election duration statistics are only recorded for elections that
 * produced a leader, because that is when the simulator reports how long
 * the election took.
 */
public record SimulationStatistics(
        long totalElections,
        long successfulElections,
        long failedElections,
        long leaderChanges,
        long heartbeatsSent,
        long heartbeatsReceived,
        long electionTimeouts,
        long nodeFailures,
        long nodeRecoveries,
        long messagesBlocked,
        long electionDurationSamples,
        long totalElectionDurationMillis,
        long minElectionDurationMillis,
        long maxElectionDurationMillis,
        long lastElectionDurationMillis) {

    /** Average duration of a successful election in milliseconds. */
    public double averageElectionDurationMillis() {
        return electionDurationSamples == 0
                ? 0.0
                : (double) totalElectionDurationMillis / electionDurationSamples;
    }

    /** Share of started elections that produced a leader, between 0 and 1. */
    public double electionSuccessRate() {
        return totalElections == 0
                ? 0.0
                : (double) successfulElections / totalElections;
    }

    /** A readable multi-line summary for the console. */
    public String toDisplayString() {

        StringBuilder summary = new StringBuilder();

        summary.append(String.format("Elections started       : %d%n", totalElections));
        summary.append(String.format("Elections won           : %d%n", successfulElections));
        summary.append(String.format("Elections failed        : %d%n", failedElections));
        summary.append(String.format("Election success rate   : %.1f%%%n",
                electionSuccessRate() * 100.0));
        summary.append(String.format("Leader changes          : %d%n", leaderChanges));
        summary.append(String.format("Election timeouts       : %d%n", electionTimeouts));
        summary.append(String.format("Heartbeats sent         : %d%n", heartbeatsSent));
        summary.append(String.format("Heartbeats received     : %d%n", heartbeatsReceived));
        summary.append(String.format("Node failures           : %d%n", nodeFailures));
        summary.append(String.format("Node recoveries         : %d%n", nodeRecoveries));
        summary.append(String.format("Messages blocked        : %d%n", messagesBlocked));

        if (electionDurationSamples == 0) {
            summary.append("Election duration (ms)  : no completed elections");
        } else {
            summary.append(String.format(
                    "Election duration (ms)  : avg=%.1f min=%d max=%d last=%d (samples=%d)",
                    averageElectionDurationMillis(),
                    minElectionDurationMillis,
                    maxElectionDurationMillis,
                    lastElectionDurationMillis,
                    electionDurationSamples));
        }

        return summary.toString();
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
