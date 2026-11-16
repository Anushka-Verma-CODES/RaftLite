package com.raftlite;

/**
 * Lets other modules (logger, statistics, UI) react to the parts of the
 * simulation that are not elections: heartbeat traffic, election timeouts,
 * node crashes and recoveries, and network link changes.
 *
 * It mirrors {@link ElectionListener}: every method has an empty default,
 * so an implementation only overrides what it needs, and the simulator
 * does not depend on any concrete monitoring class.
 */
public interface SimulationListener {

    /** A heartbeat was sent by a leader to one follower. */
    default void onHeartbeatSent(int leaderId, int followerId, int term) {
    }

    /** A candidate's vote request was delivered to another node. */
    default void onVoteRequested(int candidateId, int voterId, int term) {
    }

    /** A node answered a vote request (granted or rejected). */
    default void onVoteResponded(int voterId, int candidateId, int term, boolean granted) {
    }

    /** A follower accepted a heartbeat from a leader. */
    default void onHeartbeatReceived(int leaderId, int followerId, int term) {
    }

    /** A node's election timer ran out and it will try to become leader. */
    default void onElectionTimeout(int nodeId, long timeoutMillis) {
    }

    /** A node crashed and can no longer send or receive messages. */
    default void onNodeFailed(int nodeId) {
    }

    /** A failed node came back as a follower. */
    default void onNodeRecovered(int nodeId) {
    }

    /** The network link between two nodes was cut. */
    default void onLinkDisconnected(int nodeA, int nodeB) {
    }

    /** The network link between two nodes was restored. */
    default void onLinkReconnected(int nodeA, int nodeB) {
    }

    /**
     * A message could not be delivered, for example because a node had
     * failed or the link was disconnected.
     */
    default void onMessageBlocked(int senderId, int receiverId, String reason) {
    }
}
