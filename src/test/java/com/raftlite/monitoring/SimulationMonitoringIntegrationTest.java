package com.raftlite.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.raftlite.RaftCluster;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks that real simulator activity reaches the monitoring module.
 *
 * Heartbeat and timeout schedulers are not started here, so the tests stay
 * deterministic: elections are triggered directly and heartbeats are the
 * ones {@link RaftCluster#electLeader(int)} broadcasts after a win.
 */
class SimulationMonitoringIntegrationTest {

    private RaftCluster cluster;
    private SimulationMonitor monitor;

    @BeforeEach
    void setUp() {
        cluster = new RaftCluster(5);
        monitor = cluster.getMonitor();
    }

    @AfterEach
    void tearDown() {
        cluster.stopHeartbeats();
    }

    @Test
    void electionIsLoggedAndCountedFromTheRealSimulator() {

        cluster.electLeader(3);

        StatisticsTracker statistics = monitor.getStatisticsTracker();
        EventLogger logger = monitor.getEventLogger();

        assertEquals(3, cluster.getCurrentLeaderId());
        assertEquals(1, statistics.getTotalElections());
        assertEquals(1, statistics.getSuccessfulElections());
        assertEquals(0, statistics.getFailedElections());
        assertEquals(1, statistics.getLeaderChanges());
        assertEquals(1, statistics.getElectionDurationSamples());

        // One heartbeat to each of the four followers.
        assertEquals(4, statistics.getHeartbeatsSent());
        assertEquals(4, statistics.getHeartbeatsReceived());

        List<SimulationEvent> started =
                logger.getEvents(SimulationEventType.ELECTION_STARTED);
        List<SimulationEvent> elected =
                logger.getEvents(SimulationEventType.LEADER_ELECTED);
        List<SimulationEvent> changed =
                logger.getEvents(SimulationEventType.LEADER_CHANGED);

        assertEquals(1, started.size());
        assertEquals(1, elected.size());
        assertEquals(1, changed.size());

        assertEquals(3, started.get(0).getNodeId());
        assertEquals(1, started.get(0).getTerm());
        assertEquals(3, elected.get(0).getNodeId());
        assertTrue(elected.get(0).getDescription().contains("5 vote(s)"),
                elected.get(0).getDescription());
        assertTrue(changed.get(0).getDescription().contains("first leader"),
                changed.get(0).getDescription());

        // The log starts with the election and ends with real heartbeat traffic.
        assertEquals(SimulationEventType.ELECTION_STARTED,
                logger.getEvents().get(0).getType());
        assertEquals(4, logger.getEvents(SimulationEventType.HEARTBEAT_SENT).size());
        assertEquals(4, logger.getEvents(SimulationEventType.HEARTBEAT_RECEIVED).size());
    }

    @Test
    void leaderChangeIsDetectedAcrossRealElections() {

        cluster.electLeader(3);
        cluster.electLeader(1);

        StatisticsTracker statistics = monitor.getStatisticsTracker();

        assertEquals(1, cluster.getCurrentLeaderId());
        assertEquals(2, statistics.getTotalElections());
        assertEquals(2, statistics.getSuccessfulElections());
        assertEquals(2, statistics.getLeaderChanges());
        assertEquals(8, statistics.getHeartbeatsSent());
        assertEquals(8, statistics.getHeartbeatsReceived());
        assertEquals(2, monitor.getEventLogger()
                .getEvents(SimulationEventType.LEADER_CHANGED).size());

        String description = monitor.getEventLogger()
                .getEvents(SimulationEventType.LEADER_CHANGED)
                .get(1)
                .getDescription();

        assertTrue(description.contains("from Node 3 to Node 1"), description);
        assertNotNull(monitor.getStatistics().toDisplayString());
    }

    @Test
    void crashAndRecoveryAreLoggedOnlyOnce() {

        cluster.electLeader(3);

        cluster.crashNode(2);

        StatisticsTracker statistics = monitor.getStatisticsTracker();

        assertEquals(1, statistics.getNodeFailures());
        assertEquals(1, monitor.getEventLogger()
                .getEvents(SimulationEventType.NODE_FAILED).size());

        // Crashing an already failed node must not create another event.
        cluster.crashNode(2);

        assertEquals(1, statistics.getNodeFailures());
        assertEquals(1, monitor.getEventLogger()
                .getEvents(SimulationEventType.NODE_FAILED).size());

        cluster.recoverNode(2);

        assertEquals(1, statistics.getNodeRecoveries());
        assertEquals(1, monitor.getEventLogger()
                .getEvents(SimulationEventType.NODE_RECOVERED).size());

        cluster.recoverNode(2);

        assertEquals(1, statistics.getNodeRecoveries());

        // Unknown nodes are ignored by the cluster and by monitoring.
        cluster.crashNode(99);
        cluster.recoverNode(99);

        assertEquals(1, statistics.getNodeFailures());
        assertEquals(1, statistics.getNodeRecoveries());
    }

    @Test
    void failedNodeTrafficIsBlockedAndCounted() {

        cluster.electLeader(3); // 4 heartbeats sent and received
        cluster.crashNode(2);
        cluster.electLeader(1); // new leader, but Node 2 cannot answer

        StatisticsTracker statistics = monitor.getStatisticsTracker();

        assertEquals(1, cluster.getCurrentLeaderId());
        assertEquals(1, statistics.getNodeFailures());
        assertEquals(7, statistics.getHeartbeatsSent());
        assertEquals(7, statistics.getHeartbeatsReceived());

        // One vote request and one heartbeat towards the failed node.
        assertEquals(2, statistics.getMessagesBlocked());
        assertEquals(2, monitor.getEventLogger()
                .getEvents(SimulationEventType.MESSAGE_BLOCKED).size());
    }

    @Test
    void failedNodeCannotStartAnElectionAndIsNotCounted() {

        cluster.electLeader(3);
        cluster.crashNode(2);

        long electionsBefore = monitor.getStatisticsTracker().getTotalElections();
        int eventsBefore = monitor.getEventLogger().getEventCount();

        cluster.electLeader(2);

        assertEquals(electionsBefore,
                monitor.getStatisticsTracker().getTotalElections());
        assertEquals(eventsBefore, monitor.getEventLogger().getEventCount());
        assertEquals(3, cluster.getCurrentLeaderId());
    }

    @Test
    void disconnectAndReconnectAreLoggedFromTheRealNetwork() {

        cluster.disconnectNodes(1, 3);

        assertEquals(1, monitor.getEventLogger()
                .getEvents(SimulationEventType.LINK_DISCONNECTED).size());

        // Disconnecting an already disconnected link is not a second event.
        cluster.disconnectNodes(1, 3);

        assertEquals(1, monitor.getEventLogger()
                .getEvents(SimulationEventType.LINK_DISCONNECTED).size());

        cluster.reconnectNodes(1, 3);

        assertEquals(1, monitor.getEventLogger()
                .getEvents(SimulationEventType.LINK_RECONNECTED).size());
    }

    @Test
    void everyRecordedEventIsCompleteAndOrdered() {

        cluster.electLeader(3);
        cluster.crashNode(2);
        cluster.electLeader(1);

        List<SimulationEvent> events = monitor.getEventLogger().getEvents();

        assertFalse(events.isEmpty());

        for (SimulationEvent event : events) {
            assertNotNull(event.getTimestamp());
            assertNotNull(event.getType());
            assertNotNull(event.getDescription());
            assertFalse(event.getDescription().isBlank());
            assertTrue(event.getNodeId() >= 0);
            assertFalse(event.toLogLine().isBlank());
        }

        // The first heartbeat cannot be logged before the election that caused it.
        int startedIndex = events.indexOf(
                monitor.getEventLogger()
                        .getEvents(SimulationEventType.ELECTION_STARTED)
                        .get(0));
        int firstHeartbeatIndex = events.indexOf(
                monitor.getEventLogger()
                        .getEvents(SimulationEventType.HEARTBEAT_SENT)
                        .get(0));

        assertTrue(startedIndex < firstHeartbeatIndex);
    }
}
