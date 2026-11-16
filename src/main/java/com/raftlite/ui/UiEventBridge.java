package com.raftlite.ui;

import com.raftlite.ElectionListener;
import com.raftlite.SimulationListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Listens to the simulator (from any thread) and queues {@link UiEvent}s.
 * The dashboard drains the queue on the JavaFX thread, because JavaFX
 * nodes may only be changed from that thread.
 */
final class UiEventBridge implements SimulationListener, ElectionListener {

    private final Queue<UiEvent> queue = new ConcurrentLinkedQueue<>();

    /** Removes and returns everything queued so far, oldest first. */
    List<UiEvent> drain() {

        List<UiEvent> events = new ArrayList<>();
        UiEvent event;

        while ((event = queue.poll()) != null) {
            events.add(event);
        }

        return events;
    }

    private void add(UiEvent.Kind kind, int from, int to) {
        queue.add(new UiEvent(kind, from, to));
    }

    @Override
    public void onHeartbeatSent(int leaderId, int followerId, int term) {
        add(UiEvent.Kind.HEARTBEAT, leaderId, followerId);
    }

    @Override
    public void onVoteRequested(int candidateId, int voterId, int term) {
        add(UiEvent.Kind.VOTE_REQUEST, candidateId, voterId);
    }

    @Override
    public void onVoteResponded(int voterId, int candidateId, int term, boolean granted) {
        add(granted ? UiEvent.Kind.VOTE_GRANTED : UiEvent.Kind.VOTE_REJECTED,
                voterId, candidateId);
    }

    @Override
    public void onMessageBlocked(int senderId, int receiverId, String reason) {
        add(UiEvent.Kind.MESSAGE_BLOCKED, senderId, receiverId);
    }

    @Override
    public void onElectionTimeout(int nodeId, long timeoutMillis) {
        add(UiEvent.Kind.ELECTION_TIMEOUT, nodeId, nodeId);
    }

    @Override
    public void onElectionStarted(int candidateId, int term) {
        add(UiEvent.Kind.ELECTION_STARTED, candidateId, candidateId);
    }

    @Override
    public void onLeaderElected(int leaderId, int term, int votes, long durationMillis) {
        add(UiEvent.Kind.LEADER_ELECTED, leaderId, leaderId);
    }
}
