package com.raftlite.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticsTrackerTest {

    private StatisticsTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new StatisticsTracker();
    }

    @Test
    void startsEmpty() {

        SimulationStatistics snapshot = tracker.snapshot();

        assertEquals(0, snapshot.totalElections());
        assertEquals(0, snapshot.successfulElections());
        assertEquals(0, snapshot.failedElections());
        assertEquals(0, snapshot.leaderChanges());
        assertEquals(0, snapshot.heartbeatsSent());
        assertEquals(0, snapshot.heartbeatsReceived());
        assertEquals(0, snapshot.electionTimeouts());
        assertEquals(0, snapshot.nodeFailures());
        assertEquals(0, snapshot.nodeRecoveries());
        assertEquals(0, snapshot.messagesBlocked());
        assertEquals(0, snapshot.electionDurationSamples());
        assertEquals(0, snapshot.minElectionDurationMillis());
        assertEquals(0.0, snapshot.averageElectionDurationMillis(), 0.0001);
        assertEquals(0.0, snapshot.electionSuccessRate(), 0.0001);
    }

    @Test
    void countsElectionOutcomes() {

        tracker.recordElectionStarted();
        tracker.recordElectionStarted();
        tracker.recordElectionStarted();
        tracker.recordElectionSucceeded(12);
        tracker.recordElectionSucceeded(8);
        tracker.recordElectionFailed();
        tracker.recordLeaderChange();

        assertEquals(3, tracker.getTotalElections());
        assertEquals(2, tracker.getSuccessfulElections());
        assertEquals(1, tracker.getFailedElections());
        assertEquals(1, tracker.getLeaderChanges());
        assertEquals(2.0 / 3.0, tracker.snapshot().electionSuccessRate(), 0.0001);
    }

    @Test
    void tracksElectionDurations() {

        tracker.recordElectionSucceeded(10);
        tracker.recordElectionSucceeded(30);
        tracker.recordElectionSucceeded(20);

        assertEquals(3, tracker.getElectionDurationSamples());
        assertEquals(60, tracker.getTotalElectionDurationMillis());
        assertEquals(10, tracker.getMinElectionDurationMillis());
        assertEquals(30, tracker.getMaxElectionDurationMillis());
        assertEquals(20, tracker.getLastElectionDurationMillis());
        assertEquals(20.0, tracker.getAverageElectionDurationMillis(), 0.0001);

        SimulationStatistics snapshot = tracker.snapshot();

        assertEquals(3, snapshot.electionDurationSamples());
        assertEquals(60, snapshot.totalElectionDurationMillis());
        assertEquals(10, snapshot.minElectionDurationMillis());
        assertEquals(30, snapshot.maxElectionDurationMillis());
        assertEquals(20, snapshot.lastElectionDurationMillis());
        assertEquals(20.0, snapshot.averageElectionDurationMillis(), 0.0001);
    }

    @Test
    void countsHeartbeatsTimeoutsFailuresRecoveriesAndBlockedMessages() {

        tracker.recordHeartbeatSent();
        tracker.recordHeartbeatSent();
        tracker.recordHeartbeatSent();
        tracker.recordHeartbeatReceived();
        tracker.recordHeartbeatReceived();
        tracker.recordElectionTimeout();
        tracker.recordElectionTimeout();
        tracker.recordNodeFailure();
        tracker.recordNodeRecovery();
        tracker.recordMessageBlocked();

        assertEquals(3, tracker.getHeartbeatsSent());
        assertEquals(2, tracker.getHeartbeatsReceived());
        assertEquals(2, tracker.getElectionTimeouts());
        assertEquals(1, tracker.getNodeFailures());
        assertEquals(1, tracker.getNodeRecoveries());
        assertEquals(1, tracker.getMessagesBlocked());
    }

    @Test
    void countsLeaderChangesSeparatelyFromElections() {

        tracker.recordElectionStarted();
        tracker.recordElectionSucceeded(4);
        tracker.recordLeaderChange();

        tracker.recordElectionStarted();
        tracker.recordElectionSucceeded(5);

        assertEquals(2, tracker.getSuccessfulElections());
        assertEquals(1, tracker.getLeaderChanges());
    }

    @Test
    void rejectsNegativeDurations() {

        assertThrows(IllegalArgumentException.class,
                () -> tracker.recordElectionSucceeded(-1));

        assertEquals(0, tracker.getSuccessfulElections());
        assertEquals(0, tracker.getElectionDurationSamples());
    }

    @Test
    void resetClearsEveryCounter() {

        tracker.recordElectionStarted();
        tracker.recordElectionSucceeded(15);
        tracker.recordElectionFailed();
        tracker.recordLeaderChange();
        tracker.recordHeartbeatSent();
        tracker.recordHeartbeatReceived();
        tracker.recordElectionTimeout();
        tracker.recordNodeFailure();
        tracker.recordNodeRecovery();
        tracker.recordMessageBlocked();

        tracker.reset();

        SimulationStatistics snapshot = tracker.snapshot();

        assertEquals(0, snapshot.totalElections());
        assertEquals(0, snapshot.successfulElections());
        assertEquals(0, snapshot.failedElections());
        assertEquals(0, snapshot.leaderChanges());
        assertEquals(0, snapshot.heartbeatsSent());
        assertEquals(0, snapshot.heartbeatsReceived());
        assertEquals(0, snapshot.electionTimeouts());
        assertEquals(0, snapshot.nodeFailures());
        assertEquals(0, snapshot.nodeRecoveries());
        assertEquals(0, snapshot.messagesBlocked());
        assertEquals(0, snapshot.electionDurationSamples());
        assertEquals(0, snapshot.minElectionDurationMillis());
        assertEquals(0.0, snapshot.averageElectionDurationMillis(), 0.0001);
    }

    @Test
    void snapshotHasReadableSummary() {

        tracker.recordElectionStarted();
        tracker.recordElectionSucceeded(7);

        String summary = tracker.snapshot().toDisplayString();

        assertTrue(summary.contains("Elections started"), summary);
        assertTrue(summary.contains("Heartbeats sent"), summary);
        assertTrue(summary.contains("avg=7.0"), summary);
        assertEquals(summary, tracker.snapshot().toString());
    }
}
