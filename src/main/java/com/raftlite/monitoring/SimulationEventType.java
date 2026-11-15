package com.raftlite.monitoring;

/**
 * The important events produced by the RaftLite simulator.
 *
 * These are the events the simulator can actually emit: elections are
 * reported by {@link com.raftlite.ElectionManager} through
 * {@link com.raftlite.ElectionListener}, and heartbeat, timeout, crash,
 * recovery and network events come from {@link com.raftlite.RaftCluster}
 * and {@link com.raftlite.NetworkSimulator}.
 */
public enum SimulationEventType {

    /** A node started an election for a new term. */
    ELECTION_STARTED,

    /** An election finished and its candidate became leader. */
    LEADER_ELECTED,

    /** An election finished without the candidate reaching a majority. */
    ELECTION_FAILED,

    /** The cluster's leader is a different node than before. */
    LEADER_CHANGED,

    /** A leader sent a heartbeat to a follower. */
    HEARTBEAT_SENT,

    /** A follower accepted a heartbeat from a leader. */
    HEARTBEAT_RECEIVED,

    /** A node's random election timeout elapsed. */
    ELECTION_TIMEOUT,

    /** A node crashed. */
    NODE_FAILED,

    /** A crashed node came back as a follower. */
    NODE_RECOVERED,

    /** The network link between two nodes was cut. */
    LINK_DISCONNECTED,

    /** The network link between two nodes was restored. */
    LINK_RECONNECTED,

    /** A message could not be delivered (failed node or cut link). */
    MESSAGE_BLOCKED
}
