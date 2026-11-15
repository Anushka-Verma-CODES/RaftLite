package com.raftlite.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventLoggerTest {

    private EventLogger logger;

    @BeforeEach
    void setUp() {
        logger = new EventLogger();
    }

    @Test
    void startsEmpty() {
        assertTrue(logger.isEmpty());
        assertEquals(0, logger.getEventCount());
        assertTrue(logger.getEvents().isEmpty());
    }

    @Test
    void keepsEventsInChronologicalRecordingOrder() {

        logger.log(new SimulationEvent(
                Instant.parse("2026-01-01T00:00:01Z"),
                SimulationEventType.ELECTION_STARTED, 3, 1, "started"));
        logger.log(new SimulationEvent(
                Instant.parse("2026-01-01T00:00:02Z"),
                SimulationEventType.LEADER_ELECTED, 3, 1, "elected"));
        logger.log(new SimulationEvent(
                Instant.parse("2026-01-01T00:00:03Z"),
                SimulationEventType.HEARTBEAT_SENT, 3, 1, "heartbeat"));

        List<SimulationEvent> events = logger.getEvents();

        assertEquals(3, events.size());
        assertEquals(SimulationEventType.ELECTION_STARTED, events.get(0).getType());
        assertEquals(SimulationEventType.LEADER_ELECTED, events.get(1).getType());
        assertEquals(SimulationEventType.HEARTBEAT_SENT, events.get(2).getType());
        assertTrue(events.get(0).getTimestamp()
                .isBefore(events.get(1).getTimestamp()));
        assertEquals(3, logger.getEventCount());
        assertFalse(logger.isEmpty());
    }

    @Test
    void convenienceMethodsCreateAndStoreEvents() {

        SimulationEvent withoutTerm = logger.log(
                SimulationEventType.ELECTION_TIMEOUT, 2, "timeout");
        SimulationEvent withTerm = logger.log(
                SimulationEventType.NODE_FAILED, 2, 5, "failed");

        assertSame(withoutTerm, logger.getEvents().get(0));
        assertSame(withTerm, logger.getEvents().get(1));
        assertEquals(SimulationEvent.NO_TERM, withoutTerm.getTerm());
        assertEquals(5, withTerm.getTerm());

        assertThrows(NullPointerException.class,
                () -> logger.log(null));
        assertThrows(NullPointerException.class,
                () -> logger.log(null, 1, "description"));
        assertThrows(IllegalArgumentException.class,
                () -> logger.log(SimulationEventType.NODE_FAILED, 1, null));
    }

    @Test
    void filtersEventsByType() {

        logger.log(SimulationEventType.HEARTBEAT_SENT, 3, 1, "heartbeat 1");
        logger.log(SimulationEventType.NODE_FAILED, 2, "failed");
        logger.log(SimulationEventType.HEARTBEAT_SENT, 3, 1, "heartbeat 2");

        List<SimulationEvent> heartbeats =
                logger.getEvents(SimulationEventType.HEARTBEAT_SENT);

        assertEquals(2, heartbeats.size());
        assertEquals("heartbeat 1", heartbeats.get(0).getDescription());
        assertEquals("heartbeat 2", heartbeats.get(1).getDescription());
        assertTrue(logger.getEvents(SimulationEventType.LEADER_ELECTED).isEmpty());

        assertThrows(NullPointerException.class, () -> logger.getEvents(null));
    }

    @Test
    void filtersEventsByNode() {

        logger.log(SimulationEventType.HEARTBEAT_RECEIVED, 1, 1, "to node 1");
        logger.log(SimulationEventType.HEARTBEAT_RECEIVED, 2, 1, "to node 2");
        logger.log(SimulationEventType.HEARTBEAT_RECEIVED, 1, 1, "to node 1 again");

        List<SimulationEvent> nodeOneEvents = logger.getEventsForNode(1);

        assertEquals(2, nodeOneEvents.size());
        assertTrue(nodeOneEvents.stream()
                .allMatch(event -> event.getNodeId() == 1));
        assertTrue(logger.getEventsForNode(99).isEmpty());

        assertThrows(IllegalArgumentException.class,
                () -> logger.getEventsForNode(-1));
    }

    @Test
    void returnsNewestEvents() {

        logger.log(SimulationEventType.HEARTBEAT_SENT, 3, "one");
        logger.log(SimulationEventType.HEARTBEAT_SENT, 3, "two");
        logger.log(SimulationEventType.HEARTBEAT_SENT, 3, "three");

        List<SimulationEvent> newest = logger.getRecentEvents(2);

        assertEquals(2, newest.size());
        assertEquals("two", newest.get(0).getDescription());
        assertEquals("three", newest.get(1).getDescription());
        assertEquals(3, logger.getRecentEvents(10).size());
        assertTrue(logger.getRecentEvents(0).isEmpty());

        assertThrows(IllegalArgumentException.class,
                () -> logger.getRecentEvents(-1));
    }

    @Test
    void returnedSnapshotsCannotModifyTheLog() {

        logger.log(SimulationEventType.NODE_FAILED, 2, "failed");

        assertThrows(UnsupportedOperationException.class,
                () -> logger.getEvents().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> logger.getEvents(SimulationEventType.NODE_FAILED).clear());

        assertEquals(1, logger.getEventCount());
    }

    @Test
    void formatsAndPrintsTheLog() {

        logger.log(SimulationEventType.ELECTION_STARTED, 3, 1, "Node 3 started");
        logger.log(SimulationEventType.LEADER_ELECTED, 3, 1, "Node 3 won");

        String formatted = logger.format();

        assertTrue(formatted.contains("ELECTION_STARTED"), formatted);
        assertTrue(formatted.contains("LEADER_ELECTED"), formatted);
        assertEquals(2, logger.toLogLines().size());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        logger.printLog(new PrintStream(buffer, true, StandardCharsets.UTF_8));

        String printed = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(printed.contains("Node 3 started"), printed);
        assertTrue(printed.contains("Node 3 won"), printed);

        ByteArrayOutputStream recentBuffer = new ByteArrayOutputStream();
        logger.printLog(
                new PrintStream(recentBuffer, true, StandardCharsets.UTF_8), 1);

        String recent = recentBuffer.toString(StandardCharsets.UTF_8);

        assertFalse(recent.contains("Node 3 started"), recent);
        assertTrue(recent.contains("Node 3 won"), recent);

        assertThrows(NullPointerException.class, () -> logger.printLog(null));
    }

    @Test
    void clearRemovesEverything() {

        logger.log(SimulationEventType.NODE_FAILED, 1, "failed");
        logger.clear();

        assertTrue(logger.isEmpty());
        assertEquals(0, logger.getEventCount());
        assertTrue(logger.format().isEmpty());
    }
}
