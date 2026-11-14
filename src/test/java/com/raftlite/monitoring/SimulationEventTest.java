package com.raftlite.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SimulationEventTest {

    @Test
    void createsEventWithExplicitValues() {

        Instant timestamp = Instant.parse("2026-01-02T03:04:05Z");

        SimulationEvent event = new SimulationEvent(
                timestamp,
                SimulationEventType.LEADER_ELECTED,
                3,
                7,
                "Node 3 won term 7");

        assertEquals(timestamp, event.getTimestamp());
        assertEquals(SimulationEventType.LEADER_ELECTED, event.getType());
        assertEquals(3, event.getNodeId());
        assertEquals(7, event.getTerm());
        assertTrue(event.hasTerm());
        assertEquals("Node 3 won term 7", event.getDescription());
    }

    @Test
    void factoryWithoutTermUsesCurrentTimeAndUnknownTerm() {

        Instant before = Instant.now();

        SimulationEvent event = SimulationEvent.of(
                SimulationEventType.HEARTBEAT_SENT, 1, "heartbeat");

        Instant after = Instant.now();

        assertEquals(SimulationEventType.HEARTBEAT_SENT, event.getType());
        assertEquals(1, event.getNodeId());
        assertFalse(event.hasTerm());
        assertEquals(SimulationEvent.NO_TERM, event.getTerm());
        assertFalse(event.getTimestamp().isBefore(before));
        assertFalse(event.getTimestamp().isAfter(after));
    }

    @Test
    void factoryWithTermKeepsTheTerm() {

        SimulationEvent event = SimulationEvent.of(
                SimulationEventType.ELECTION_TIMEOUT, 4, 9, "timeout");

        assertTrue(event.hasTerm());
        assertEquals(9, event.getTerm());
        assertEquals(4, event.getNodeId());
        assertFalse(event.getTimestamp().isBefore(
                Instant.now().minusSeconds(5)));
    }

    @Test
    void logLineContainsTypeNodeTermAndDescription() {

        SimulationEvent event = new SimulationEvent(
                Instant.parse("2026-01-02T03:04:05Z"),
                SimulationEventType.NODE_FAILED,
                2,
                4,
                "Node 2 failed");

        String line = event.toLogLine();

        assertTrue(line.contains("NODE_FAILED"), line);
        assertTrue(line.contains("node=2"), line);
        assertTrue(line.contains("term=4"), line);
        assertTrue(line.contains("Node 2 failed"), line);
        assertEquals(line, event.toString());
    }

    @Test
    void logLineMarksMissingTermAsDash() {

        SimulationEvent event = SimulationEvent.of(
                SimulationEventType.LINK_DISCONNECTED, 1, "link cut");

        assertTrue(event.toLogLine().contains("term=-"), event.toLogLine());
    }

    @Test
    void eventsWithSameFieldsAreEqual() {

        Instant timestamp = Instant.parse("2026-01-02T03:04:05Z");

        SimulationEvent first = new SimulationEvent(
                timestamp, SimulationEventType.NODE_RECOVERED, 2, 1, "recovered");
        SimulationEvent second = new SimulationEvent(
                timestamp, SimulationEventType.NODE_RECOVERED, 2, 1, "recovered");
        SimulationEvent different = new SimulationEvent(
                timestamp, SimulationEventType.NODE_FAILED, 2, 1, "recovered");

        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
        assertNotEquals(first, null);
        assertNotEquals(first, "not an event");
    }

    @Test
    void rejectsInvalidArguments() {

        Instant now = Instant.now();

        assertThrows(NullPointerException.class, () -> new SimulationEvent(
                null, SimulationEventType.NODE_FAILED, 1, 1, "failed"));
        assertThrows(NullPointerException.class, () -> new SimulationEvent(
                now, null, 1, 1, "failed"));
        assertThrows(NullPointerException.class, () -> SimulationEvent.of(
                null, 1, "failed"));

        assertThrows(IllegalArgumentException.class, () -> new SimulationEvent(
                now, SimulationEventType.NODE_FAILED, -1, 1, "failed"));
        assertThrows(IllegalArgumentException.class, () -> new SimulationEvent(
                now, SimulationEventType.NODE_FAILED, 1, -2, "failed"));
        assertThrows(IllegalArgumentException.class, () -> new SimulationEvent(
                now, SimulationEventType.NODE_FAILED, 1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new SimulationEvent(
                now, SimulationEventType.NODE_FAILED, 1, 1, "   "));
    }
}
