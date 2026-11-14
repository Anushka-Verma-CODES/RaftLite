package com.raftlite.monitoring;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central event log for one simulation.
 *
 * Events are stored in the order they were recorded, which is the order the
 * simulator produced them in, so the log is a chronological history. The
 * class is thread-safe because heartbeat and timeout events arrive from the
 * simulator's scheduler threads.
 */
public final class EventLogger {

    private final List<SimulationEvent> events = new CopyOnWriteArrayList<>();

    /** Stores an already constructed event and returns it. */
    public SimulationEvent log(SimulationEvent event) {

        Objects.requireNonNull(event, "event must not be null");

        events.add(event);

        return event;
    }

    /** Creates and stores an event without a term. */
    public SimulationEvent log(SimulationEventType type,
                               int nodeId,
                               String description) {

        return log(SimulationEvent.of(type, nodeId, description));
    }

    /** Creates and stores an event in the given term. */
    public SimulationEvent log(SimulationEventType type,
                               int nodeId,
                               int term,
                               String description) {

        return log(SimulationEvent.of(type, nodeId, term, description));
    }

    /** All recorded events, oldest first. The returned list is a snapshot. */
    public List<SimulationEvent> getEvents() {
        return List.copyOf(events);
    }

    /** All recorded events of one type, oldest first. */
    public List<SimulationEvent> getEvents(SimulationEventType type) {

        Objects.requireNonNull(type, "type must not be null");

        return events.stream()
                .filter(event -> event.getType() == type)
                .toList();
    }

    /** All recorded events that are about one node, oldest first. */
    public List<SimulationEvent> getEventsForNode(int nodeId) {

        if (nodeId < 0) {
            throw new IllegalArgumentException(
                    "nodeId must not be negative: " + nodeId);
        }

        return events.stream()
                .filter(event -> event.getNodeId() == nodeId)
                .toList();
    }

    /** The newest {@code count} events, oldest first. */
    public List<SimulationEvent> getRecentEvents(int count) {

        if (count < 0) {
            throw new IllegalArgumentException(
                    "count must not be negative: " + count);
        }

        int from = Math.max(0, events.size() - count);

        return List.copyOf(events.subList(from, events.size()));
    }

    public int getEventCount() {
        return events.size();
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    /** Removes every recorded event. */
    public void clear() {
        events.clear();
    }

    /** Every event as a readable line, oldest first. */
    public List<String> toLogLines() {
        return events.stream()
                .map(SimulationEvent::toLogLine)
                .toList();
    }

    /** The whole log as one multi-line string. */
    public String format() {
        return String.join(System.lineSeparator(), toLogLines());
    }

    /** Prints the whole log to the given stream. */
    public void printLog(PrintStream out) {

        Objects.requireNonNull(out, "out must not be null");

        events.forEach(event -> out.println(event.toLogLine()));
    }

    /** Prints only the newest {@code maxEvents} events. */
    public void printLog(PrintStream out, int maxEvents) {

        Objects.requireNonNull(out, "out must not be null");

        getRecentEvents(maxEvents).forEach(event -> out.println(event.toLogLine()));
    }

    @Override
    public String toString() {
        return "EventLogger{events=" + events.size() + '}';
    }
}
