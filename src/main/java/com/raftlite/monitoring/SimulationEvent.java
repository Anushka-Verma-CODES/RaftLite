package com.raftlite.monitoring;

import java.time.Instant;
import java.util.Objects;

/**
 * One immutable event that happened during a simulation.
 *
 * An event records when it happened, what happened, which node it is about
 * and, where it applies, the Raft term and a human readable description.
 */
public final class SimulationEvent {

    /** Used when the event does not belong to a Raft term (e.g. a link cut). */
    public static final int NO_TERM = -1;

    private final Instant timestamp;
    private final SimulationEventType type;
    private final int nodeId;
    private final int term;
    private final String description;

    /**
     * Creates an event with an explicit timestamp. Mostly useful for tests;
     * the simulator normally uses the {@code of(...)} factories.
     *
     * @throws NullPointerException     if timestamp, type or description is null
     * @throws IllegalArgumentException if nodeId is negative, if term is
     *                                  smaller than -1, or if the description
     *                                  is blank
     */
    public SimulationEvent(Instant timestamp,
                           SimulationEventType type,
                           int nodeId,
                           int term,
                           String description) {

        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");

        if (nodeId < 0) {
            throw new IllegalArgumentException("nodeId must not be negative: " + nodeId);
        }

        if (term < NO_TERM) {
            throw new IllegalArgumentException(
                    "term must be -1 (unknown) or greater: " + term);
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }

        this.nodeId = nodeId;
        this.term = term;
        this.description = description;
    }

    /** Creates an event for the current instant, without a term. */
    public static SimulationEvent of(SimulationEventType type,
                                     int nodeId,
                                     String description) {

        return new SimulationEvent(
                Instant.now(), type, nodeId, NO_TERM, description);
    }

    /** Creates an event for the current instant in the given term. */
    public static SimulationEvent of(SimulationEventType type,
                                     int nodeId,
                                     int term,
                                     String description) {

        return new SimulationEvent(Instant.now(), type, nodeId, term, description);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public SimulationEventType getType() {
        return type;
    }

    /** The node this event is about (for heartbeats: the sender or receiver). */
    public int getNodeId() {
        return nodeId;
    }

    public int getTerm() {
        return term;
    }

    public boolean hasTerm() {
        return term != NO_TERM;
    }

    public String getDescription() {
        return description;
    }

    /** A single readable log line for this event. */
    public String toLogLine() {
        return String.format(
                "%s | %-18s | node=%-3d | term=%-4s | %s",
                timestamp,
                type,
                nodeId,
                hasTerm() ? Integer.toString(term) : "-",
                description);
    }

    @Override
    public String toString() {
        return toLogLine();
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof SimulationEvent event)) {
            return false;
        }

        return nodeId == event.nodeId &&
                term == event.term &&
                timestamp.equals(event.timestamp) &&
                type == event.type &&
                description.equals(event.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, type, nodeId, term, description);
    }
}
