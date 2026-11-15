package com.raftlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for message delivery, crashed nodes and cut links. */
class NetworkSimulatorTest {

    private NetworkSimulator network;
    private final RaftNode[] nodes = new RaftNode[4];
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {

        network = new NetworkSimulator();

        for (int id = 1; id <= 3; id++) {
            nodes[id] = new RaftNode(id);
            network.registerNode(nodes[id]);
        }

        network.addListener(new SimulationListener() {
            @Override
            public void onLinkDisconnected(int a, int b) {
                events.add("cut " + a + "-" + b);
            }

            @Override
            public void onLinkReconnected(int a, int b) {
                events.add("restored " + a + "-" + b);
            }

            @Override
            public void onMessageBlocked(int from, int to, String reason) {
                events.add("blocked " + from + "->" + to + ": " + reason);
            }

            @Override
            public void onHeartbeatReceived(int leaderId, int followerId, int term) {
                events.add("heartbeat " + leaderId + "->" + followerId);
            }
        });
    }

    private static RaftMessage heartbeat(int from, int to, int term) {
        return new RaftMessage(from, to, MessageType.HEARTBEAT, term);
    }

    @Test
    void voteRequestIsAnsweredAndCounted() throws ElectionException {

        int term = nodes[1].startElection();

        assertTrue(network.requestVote(1, 2, term));
        assertEquals(2, nodes[1].getVotesReceived());
    }

    @Test
    void heartbeatIsDeliveredToALiveFollower() {

        assertTrue(network.sendMessage(heartbeat(1, 2, 1)));
        assertEquals(1, nodes[2].getLeaderId().orElseThrow());
        assertEquals(List.of("heartbeat 1->2"), events);
    }

    @Test
    void messageToUnknownNodeIsBlocked() {

        assertFalse(network.sendMessage(heartbeat(1, 7, 1)));
        assertEquals(List.of("blocked 1->7: node not found"), events);
    }

    @Test
    void messageToFailedNodeIsBlocked() {

        nodes[2].fail();

        assertFalse(network.sendMessage(heartbeat(1, 2, 1)));
        assertEquals(List.of("blocked 1->2: a node has failed"), events);
    }

    @Test
    void cutLinkBlocksBothDirectionsUntilRestored() {

        network.disconnectNodes(1, 2);

        assertFalse(network.sendMessage(heartbeat(1, 2, 1)));
        assertFalse(network.sendMessage(heartbeat(2, 1, 1)));
        assertTrue(network.sendMessage(heartbeat(1, 3, 1)), "other links still work");

        network.reconnectNodes(2, 1);

        assertTrue(network.sendMessage(heartbeat(1, 2, 1)));
    }

    @Test
    void linkChangesAreReportedOnlyWhenTheyChangeSomething() {

        network.disconnectNodes(1, 2);
        network.disconnectNodes(2, 1);
        network.disconnectNodes(1, 1);
        network.reconnectNodes(1, 2);
        network.reconnectNodes(1, 2);

        assertEquals(List.of("cut 1-2", "restored 1-2"), events);
    }

    @Test
    void isolatingANodeCutsAllItsLinks() {

        network.disconnectNode(2);

        assertEquals(List.of("cut 2-1", "cut 2-3"), events.stream().sorted().toList());
        assertTrue(network.sendMessage(heartbeat(1, 3, 1)));

        network.reconnectNode(2);

        assertTrue(network.sendMessage(heartbeat(1, 2, 1)));
    }

    @Test
    void heartbeatReplyMakesAStaleLeaderStepDown() throws ElectionException {

        nodes[1].becomeLeader(nodes[1].startElection());   // leader of term 1
        nodes[2].handleVoteRequest(new VoteRequest(4, 3));  // node 2 is in term 4

        network.sendMessage(heartbeat(1, 2, 1));

        assertTrue(nodes[2].getLeaderId().isEmpty(), "node 2 must not accept the old leader");

        assertEquals(NodeState.FOLLOWER, nodes[1].getState());
        assertEquals(4, nodes[1].getCurrentTerm());
    }
}
