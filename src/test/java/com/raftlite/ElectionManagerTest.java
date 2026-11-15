package com.raftlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for running elections, the majority rule and the leader history. */
class ElectionManagerTest {

    private List<RaftNode> nodes;
    private ElectionManager elections;
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp() {

        nodes = new ArrayList<>();
        NetworkSimulator network = new NetworkSimulator();

        for (int id = 1; id <= 5; id++) {
            RaftNode node = new RaftNode(id);
            nodes.add(node);
            network.registerNode(node);
        }

        elections = new ElectionManager(nodes, network);

        elections.addListener(new ElectionListener() {
            @Override
            public void onElectionStarted(int candidateId, int term) {
                calls.add("started " + candidateId + " t" + term);
            }

            @Override
            public void onLeaderElected(int leaderId, int term, int votes, long durationMillis) {
                calls.add("elected " + leaderId + " t" + term + " v" + votes);
            }

            @Override
            public void onElectionFailed(int candidateId, int term, int votes, int majority) {
                calls.add("failed " + candidateId + " t" + term + " v" + votes + "/" + majority);
            }
        });
    }

    private RaftNode node(int id) {
        return nodes.get(id - 1);
    }

    @Test
    void majorityIsMoreThanHalfOfTheCluster() {

        assertEquals(1, ElectionManager.majorityOf(1));
        assertEquals(2, ElectionManager.majorityOf(3));
        assertEquals(3, ElectionManager.majorityOf(4));
        assertEquals(3, ElectionManager.majorityOf(5));
        assertEquals(3, elections.getMajority());
    }

    @Test
    void candidateWithAllVotesBecomesLeader() throws ElectionException {

        assertTrue(elections.runElection(2));

        assertEquals(NodeState.LEADER, node(2).getState());
        assertEquals(2, elections.getLeaderForTerm(1).orElseThrow());
        assertEquals(List.of("started 2 t1", "elected 2 t1 v5"), calls);

        for (int id : new int[] {1, 3, 4, 5}) {
            assertEquals(1, node(id).getCurrentTerm());
            assertEquals(2, node(id).getVotedFor());
        }
    }

    @Test
    void electionSucceedsWithExactlyAMajorityAlive() throws ElectionException {

        node(4).fail();
        node(5).fail();

        assertTrue(elections.runElection(1));
        assertEquals("elected 1 t1 v3", calls.get(1));
    }

    @Test
    void electionFailsWithoutAMajority() throws ElectionException {

        node(3).fail();
        node(4).fail();
        node(5).fail();

        assertFalse(elections.runElection(1));

        assertEquals(NodeState.CANDIDATE, node(1).getState());
        assertTrue(elections.getLeaderForTerm(1).isEmpty());
        assertEquals("failed 1 t1 v2/3", calls.get(1));
    }

    @Test
    void unknownCandidateIsRejected() {

        ElectionException error = assertThrows(
                ElectionException.class, () -> elections.runElection(9));

        assertTrue(error.getMessage().contains("does not exist"));
        assertEquals(0, elections.getElectionAttempts());
    }

    @Test
    void failedCandidateIsRejected() {

        node(1).fail();

        assertThrows(ElectionException.class, () -> elections.runElection(1));
    }

    @Test
    void historyRecordsTheLeaderOfEveryTerm() throws ElectionException {

        elections.runElection(1);
        elections.runElection(3);
        elections.runElection(5);

        assertEquals(3, elections.getElectionAttempts());
        assertEquals(1, elections.getLeaderHistory().get(1));
        assertEquals(3, elections.getLeaderHistory().get(2));
        assertEquals(5, elections.getLeaderHistory().get(3));

        // Earlier leaders stepped down when they saw the newer term.
        assertEquals(NodeState.FOLLOWER, node(1).getState());
        assertEquals(NodeState.FOLLOWER, node(3).getState());
        assertEquals(NodeState.LEADER, node(5).getState());
    }

    @Test
    void leaderHistoryCannotBeModifiedFromOutside() throws ElectionException {

        elections.runElection(1);

        assertThrows(UnsupportedOperationException.class,
                () -> elections.getLeaderHistory().put(2, 2));
    }
}
