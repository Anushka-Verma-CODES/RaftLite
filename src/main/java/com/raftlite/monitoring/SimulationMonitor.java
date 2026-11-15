package com.raftlite.monitoring;

import com.raftlite.ElectionListener;
import com.raftlite.SimulationListener;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Connects the running simulator to the event log and the statistics.
 *
 * It is registered with {@link com.raftlite.ElectionManager} and
 * {@link com.raftlite.NetworkSimulator}, and called by
 * {@link com.raftlite.RaftCluster} for timeouts and crashes, so every log
 * entry and every counter comes from a real simulation event.
 */
public final class SimulationMonitor
        implements ElectionListener, SimulationListener {

    private static final int NO_LEADER_ID = -1;

    private final EventLogger eventLogger;
    private final StatisticsTracker statistics;

    private int lastLeaderId = NO_LEADER_ID;

    public SimulationMonitor() {
        this(new EventLogger(), new StatisticsTracker());
    }

    public SimulationMonitor(EventLogger eventLogger,
                             StatisticsTracker statistics) {

        this.eventLogger = Objects.requireNonNull(
                eventLogger, "eventLogger must not be null");
        this.statistics = Objects.requireNonNull(
                statistics, "statistics must not be null");
    }

    public EventLogger getEventLogger() {
        return eventLogger;
    }

    public StatisticsTracker getStatisticsTracker() {
        return statistics;
    }

    /** A snapshot of the statistics collected so far. */
    public SimulationStatistics getStatistics() {
        return statistics.snapshot();
    }

    // ---------------------------------------------------------------
    // Elections
    // ---------------------------------------------------------------

    @Override
    public void onElectionStarted(int candidateId, int term) {

        statistics.recordElectionStarted();

        eventLogger.log(
                SimulationEventType.ELECTION_STARTED,
                candidateId,
                term,
                "Node " + candidateId +
                        " started an election for term " + term);
    }

    @Override
    public synchronized void onLeaderElected(int leaderId,
                                             int term,
                                             int votes,
                                             long durationMillis) {

        statistics.recordElectionSucceeded(durationMillis);

        eventLogger.log(
                SimulationEventType.LEADER_ELECTED,
                leaderId,
                term,
                "Node " + leaderId + " won the election for term " + term +
                        " with " + votes + " vote(s) in " + durationMillis + " ms");

        int previousLeaderId = lastLeaderId;

        if (previousLeaderId != leaderId) {

            lastLeaderId = leaderId;
            statistics.recordLeaderChange();

            String description = previousLeaderId == NO_LEADER_ID
                    ? "Node " + leaderId +
                      " became the first leader in term " + term
                    : "Leader changed from Node " + previousLeaderId +
                      " to Node " + leaderId + " in term " + term;

            eventLogger.log(
                    SimulationEventType.LEADER_CHANGED,
                    leaderId,
                    term,
                    description);
        }
    }

    @Override
    public void onElectionFailed(int candidateId,
                                 int term,
                                 int votes,
                                 int majority) {

        statistics.recordElectionFailed();

        eventLogger.log(
                SimulationEventType.ELECTION_FAILED,
                candidateId,
                term,
                "Node " + candidateId + " did not win term " + term +
                        " with " + votes + " vote(s) (majority = " +
                        majority + ")");
    }

    // ---------------------------------------------------------------
    // Heartbeats and timeouts
    // ---------------------------------------------------------------

    @Override
    public void onHeartbeatSent(int leaderId, int followerId, int term) {

        statistics.recordHeartbeatSent();

        eventLogger.log(
                SimulationEventType.HEARTBEAT_SENT,
                leaderId,
                term,
                "Node " + leaderId +
                        " sent a heartbeat to Node " + followerId);
    }

    @Override
    public void onHeartbeatReceived(int leaderId, int followerId, int term) {

        statistics.recordHeartbeatReceived();

        eventLogger.log(
                SimulationEventType.HEARTBEAT_RECEIVED,
                followerId,
                term,
                "Node " + followerId +
                        " received a heartbeat from Node " + leaderId);
    }

    @Override
    public void onElectionTimeout(int nodeId, long timeoutMillis) {

        statistics.recordElectionTimeout();

        eventLogger.log(
                SimulationEventType.ELECTION_TIMEOUT,
                nodeId,
                "Node " + nodeId + " election timeout elapsed after " +
                        timeoutMillis + " ms");
    }

    // ---------------------------------------------------------------
    // Node failures and recoveries
    // ---------------------------------------------------------------

    @Override
    public void onNodeFailed(int nodeId) {

        statistics.recordNodeFailure();

        eventLogger.log(
                SimulationEventType.NODE_FAILED,
                nodeId,
                "Node " + nodeId + " failed and is no longer available");
    }

    @Override
    public void onNodeRecovered(int nodeId) {

        statistics.recordNodeRecovery();

        eventLogger.log(
                SimulationEventType.NODE_RECOVERED,
                nodeId,
                "Node " + nodeId + " recovered as a FOLLOWER");
    }

    // ---------------------------------------------------------------
    // Network
    // ---------------------------------------------------------------

    @Override
    public void onLinkDisconnected(int nodeA, int nodeB) {

        eventLogger.log(
                SimulationEventType.LINK_DISCONNECTED,
                nodeA,
                "Link between Node " + nodeA + " and Node " + nodeB +
                        " was disconnected");
    }

    @Override
    public void onLinkReconnected(int nodeA, int nodeB) {

        eventLogger.log(
                SimulationEventType.LINK_RECONNECTED,
                nodeA,
                "Link between Node " + nodeA + " and Node " + nodeB +
                        " was restored");
    }

    @Override
    public void onMessageBlocked(int senderId,
                                 int receiverId,
                                 String reason) {

        statistics.recordMessageBlocked();

        eventLogger.log(
                SimulationEventType.MESSAGE_BLOCKED,
                senderId,
                "Message from Node " + senderId + " to Node " +
                        receiverId + " was blocked: " + reason);
    }

    // ---------------------------------------------------------------
    // Reporting
    // ---------------------------------------------------------------

    /** Prints the statistics summary followed by the whole event log. */
    public void printReport(PrintStream out) {

        Objects.requireNonNull(out, "out must not be null");

        out.println();
        out.println("===== SIMULATION STATISTICS =====");
        out.println(statistics.snapshot().toDisplayString());
        out.println();
        out.println("===== SIMULATION EVENT LOG (" +
                eventLogger.getEventCount() + " events) =====");
        eventLogger.printLog(out);
        out.println("=================================");
    }

    /** Clears the log, the statistics and the remembered leader. */
    public synchronized void reset() {

        eventLogger.clear();
        statistics.reset();
        lastLeaderId = NO_LEADER_ID;
    }
}
