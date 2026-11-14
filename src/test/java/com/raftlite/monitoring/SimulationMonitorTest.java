package com.raftlite.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimulationMonitorTest {

    private SimulationMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new SimulationMonitor();
    }

    @Test
    void recordsElectionLifecycleAndLeaderChange() {

        monitor.onElectionStarted(3, 1);
        monitor.onLeaderElected(3, 1, 4, 12);

        StatisticsTracker statistics = monitor.getStatisticsTracker();

        assertEquals(1, statistics.getTotalElections());
        assertEquals(1, statistics.getSuccessfulElections());
        assertEquals(0, statistics.getFailedElections());
        assertEquals(1, statistics.getLeaderChanges());
        assertEquals(1, statistics.getElectionDurationSamples());
        assertEquals(12, statistics.getLastElectionDurationMillis());

        List<SimulationEvent> events = monitor.getEventLogger().getEvents();

        assertEquals(3, events.size());
        assertEquals(SimulationEventType.ELECTION_STARTED, events.get(0).getType());
        assertEquals(3, events.get(0).getNodeId());
        assertEquals(1, events.get(0).getTerm());
        assertEquals(SimulationEventType.LEADER_ELECTED, events.get(1).getType());
        assertEquals("Node 3 won the election for term 1",
                events.get(1).getDescription().substring(0,
                        "Node 3 won the election for term 1".length()));

        assertEquals(1, monitor.getEventLogger()
                .getEvents(SimulationEventType.LEADER_CHANGED).size());
        assertTrue(monitor.getEventLogger()
                .getEvents(SimulationEventType.LEADER_CHANGED)
                .get(0)
                .getDescription()
                .contains("first leader"));
    }

    @Test
    void detectsLeaderChangesButNotReelectionsOfTheSameNode() {

        monitor.onElectionStarted(3, 1);
        monitor.onLeaderElected(3, 1, 3, 5);
        monitor.onElectionStarted(3, 2);
        monitor.onLeaderElected(3, 2, 3, 6);
        monitor.onElectionStarted(1, 3);
        monitor.onLeaderElected(1, 3, 4, 9);

        StatisticsTracker statistics = monitor.getStatisticsTracker();

        assertEquals(3, statistics.getSuccessfulElections());
        assertEquals(2, statistics.getLeaderChanges());
        assertEquals(2, monitor.getEventLogger()
                .getEvents(SimulationEventType.LEADER_CHANGED).size());

        String lastChange = monitor.getEventLogger()
                .getEvents(SimulationEventType.LEADER_CHANGED)
                .get(1)
                .getDescription();

        assertTrue(lastChange.contains("from Node 3 to Node 1"), lastChange);
        assertEquals(20.0 / 3.0, statistics.getAverageElectionDurationMillis(), 0.0001);
    }

    @Test
    void recordsFailedElectionsWithoutLeaderChanges() {

        monitor.onElectionStarted(2, 1);
        monitor.onElectionFailed(2, 1, 1, 3);

        StatisticsTracker statistics = monitor.getStatisticsTracker();

        assertEquals(1, statistics.getTotalElections());
        assertEquals(0, statistics.getSuccessfulElections());
        assertEquals(1, statistics.getFailedElections());
        assertEquals(0, statistics.getLeaderChanges());
        assertEquals(1, monitor.getEventLogger()
                .getEvents(SimulationEventType.ELECTION_FAILED).size());

        String description = monitor.getEventLogger()
                .getEvents(SimulationEventType.ELECTION_FAILED)
                .get(0)
                .getDescription();

        assertTrue(description.contains("majority = 3"), description);
    }

    @Test
    void recordsHeartbeatsTimeoutsFailuresRecoveriesAndNetworkEvents() {

        monitor.onHeartbeatSent(3, 1, 2);
        monitor.onHeartbeatSent(3, 2, 2);
        monitor.onHeartbeatReceived(3, 1, 2);
        monitor.onElectionTimeout(4, 1734);
        monitor.onNodeFailed(2);
        monitor.onNodeRecovered(2);
        monitor.onLinkDisconnected(1, 3);
        monitor.onLinkReconnected(1, 3);
        monitor.onMessageBlocked(3, 2, "a node has failed");

        StatisticsTracker statistics = monitor.getStatisticsTracker();

        assertEquals(2, statistics.getHeartbeatsSent());
        assertEquals(1, statistics.getHeartbeatsReceived());
        assertEquals(1, statistics.getElectionTimeouts());
        assertEquals(1, statistics.getNodeFailures());
        assertEquals(1, statistics.getNodeRecoveries());
        assertEquals(1, statistics.getMessagesBlocked());

        EventLogger logger = monitor.getEventLogger();

        assertEquals(2, logger.getEvents(SimulationEventType.HEARTBEAT_SENT).size());
        assertEquals(1, logger.getEvents(SimulationEventType.ELECTION_TIMEOUT).size());
        assertEquals(1, logger.getEvents(SimulationEventType.NODE_FAILED).size());
        assertEquals(1, logger.getEvents(SimulationEventType.NODE_RECOVERED).size());
        assertEquals(1, logger.getEvents(SimulationEventType.LINK_DISCONNECTED).size());
        assertEquals(1, logger.getEvents(SimulationEventType.LINK_RECONNECTED).size());
        assertEquals(1, logger.getEvents(SimulationEventType.MESSAGE_BLOCKED).size());

        // The heartbeat-received event belongs to the follower that received it.
        assertEquals(1, logger.getEventsForNode(1).stream()
                .filter(event -> event.getType() == SimulationEventType.HEARTBEAT_RECEIVED)
                .count());

        String timeoutDescription = logger
                .getEvents(SimulationEventType.ELECTION_TIMEOUT)
                .get(0)
                .getDescription();

        assertTrue(timeoutDescription.contains("1734 ms"), timeoutDescription);
    }

    @Test
    void snapshotMatchesTrackedCounters() {

        monitor.onElectionStarted(1, 1);
        monitor.onLeaderElected(1, 1, 5, 3);
        monitor.onHeartbeatSent(1, 2, 1);

        SimulationStatistics snapshot = monitor.getStatistics();

        assertEquals(1, snapshot.totalElections());
        assertEquals(1, snapshot.successfulElections());
        assertEquals(1, snapshot.leaderChanges());
        assertEquals(1, snapshot.heartbeatsSent());
        assertEquals(3, snapshot.lastElectionDurationMillis());
    }

    @Test
    void resetClearsLogStatisticsAndLeaderMemory() {

        monitor.onElectionStarted(3, 1);
        monitor.onLeaderElected(3, 1, 4, 5);
        monitor.onNodeFailed(2);

        monitor.reset();

        assertTrue(monitor.getEventLogger().isEmpty());
        assertEquals(0, monitor.getStatisticsTracker().getTotalElections());
        assertEquals(0, monitor.getStatisticsTracker().getNodeFailures());

        // After a reset the same node is a new leader, not an unchanged one.
        monitor.onElectionStarted(3, 2);
        monitor.onLeaderElected(3, 2, 4, 5);

        assertEquals(1, monitor.getStatisticsTracker().getLeaderChanges());
    }

    @Test
    void rejectsMissingCollaborators() {

        assertThrows(NullPointerException.class,
                () -> new SimulationMonitor(null, new StatisticsTracker()));
        assertThrows(NullPointerException.class,
                () -> new SimulationMonitor(new EventLogger(), null));
    }

    @Test
    void printsStatisticsAndEventLog() {

        monitor.onNodeFailed(2);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        monitor.printReport(new PrintStream(buffer, true, StandardCharsets.UTF_8));

        String report = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(report.contains("SIMULATION STATISTICS"), report);
        assertTrue(report.contains("Node failures           : 1"), report);
        assertTrue(report.contains("SIMULATION EVENT LOG (1 events)"), report);
        assertTrue(report.contains("Node 2 failed"), report);
        assertFalse(report.isBlank());

        assertThrows(NullPointerException.class, () -> monitor.printReport(null));
    }
}
