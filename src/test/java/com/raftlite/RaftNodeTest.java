package com.raftlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the Raft rules applied by a single node. */
class RaftNodeTest {

    private RaftNode node;

    @BeforeEach
    void setUp() {
        node = new RaftNode(1);
    }

    @Test
    void newNodeIsFollowerInTermZero() {

        assertEquals(NodeState.FOLLOWER, node.getState());
        assertEquals(0, node.getCurrentTerm());
        assertNull(node.getVotedFor());
        assertTrue(node.getLeaderId().isEmpty());
    }

    @Test
    void electionTimeoutIsInsideTheConfiguredRange() {

        long timeout = node.getElectionTimeout();

        assertTrue(timeout >= RaftNode.MIN_ELECTION_TIMEOUT, "timeout " + timeout);
        assertTrue(timeout < RaftNode.MAX_ELECTION_TIMEOUT, "timeout " + timeout);
    }

    @Test
    void startingAnElectionIncrementsTermAndVotesForItself() throws ElectionException {

        int term = node.startElection();

        assertEquals(1, term);
        assertEquals(NodeState.CANDIDATE, node.getState());
        assertEquals(1, node.getVotedFor());
        assertEquals(1, node.getVotesReceived());
    }

    @Test
    void failedNodeCannotStartAnElection() {

        node.fail();

        assertThrows(ElectionException.class, node::startElection);
    }

    @Test
    void grantsOnlyOneVotePerTerm() {

        VoteResponse first = node.handleVoteRequest(new VoteRequest(1, 2));
        VoteResponse second = node.handleVoteRequest(new VoteRequest(1, 3));

        assertTrue(first.isVoteGranted());
        assertFalse(second.isVoteGranted());
        assertEquals(2, node.getVotedFor());
    }

    @Test
    void repeatedRequestFromSameCandidateIsGrantedAgain() {

        node.handleVoteRequest(new VoteRequest(1, 2));

        assertTrue(node.handleVoteRequest(new VoteRequest(1, 2)).isVoteGranted());
    }

    @Test
    void rejectsVoteRequestWithStaleTerm() {

        node.handleVoteRequest(new VoteRequest(3, 2));

        VoteResponse response = node.handleVoteRequest(new VoteRequest(2, 4));

        assertFalse(response.isVoteGranted());
        assertEquals(3, response.getTerm());
    }

    @Test
    void higherTermMakesCandidateStepDown() throws ElectionException {

        node.startElection();

        VoteResponse response = node.handleVoteRequest(new VoteRequest(5, 2));

        assertTrue(response.isVoteGranted());
        assertEquals(NodeState.FOLLOWER, node.getState());
        assertEquals(5, node.getCurrentTerm());
    }

    @Test
    void sameVoterIsCountedOnlyOnce() throws ElectionException {

        int term = node.startElection();

        node.handleVoteResponse(new VoteResponse(2, term, true));
        node.handleVoteResponse(new VoteResponse(2, term, true));

        assertEquals(2, node.getVotesReceived());
    }

    @Test
    void rejectedAndOldTermVotesAreNotCounted() throws ElectionException {

        node.startElection();
        int term = node.startElection();

        node.handleVoteResponse(new VoteResponse(2, term, false));
        node.handleVoteResponse(new VoteResponse(3, term - 1, true));

        assertEquals(1, node.getVotesReceived());
    }

    @Test
    void becomesLeaderOnlyAsCandidateOfThatTerm() throws ElectionException {

        assertFalse(node.becomeLeader(1), "a follower cannot become leader");

        int term = node.startElection();

        assertFalse(node.becomeLeader(term + 1), "wrong term");
        assertTrue(node.becomeLeader(term));
        assertEquals(NodeState.LEADER, node.getState());
        assertEquals(1, node.getLeaderId().orElseThrow());
    }

    @Test
    void candidateBecomesFollowerOnHeartbeatOfSameTerm() throws ElectionException {

        int term = node.startElection();

        assertTrue(node.receiveHeartbeat(term, 4));
        assertEquals(NodeState.FOLLOWER, node.getState());
        assertEquals(4, node.getLeaderId().orElseThrow());
    }

    @Test
    void leaderRejectsHeartbeatFromAnotherLeaderOfSameTerm() throws ElectionException {

        int term = node.startElection();
        node.becomeLeader(term);

        assertFalse(node.receiveHeartbeat(term, 2));
        assertEquals(NodeState.LEADER, node.getState());
    }

    @Test
    void staleHeartbeatIsRejected() {

        node.handleVoteRequest(new VoteRequest(4, 2));

        assertFalse(node.receiveHeartbeat(3, 5));
    }

    @Test
    void leaderStepsDownWhenItLearnsAHigherTerm() throws ElectionException {

        int term = node.startElection();
        node.becomeLeader(term);

        node.updateTermIfHigher(term + 1);

        assertEquals(NodeState.FOLLOWER, node.getState());
        assertEquals(term + 1, node.getCurrentTerm());
    }

    @Test
    void crashKeepsTermAndRecoveryReturnsAsFollower() throws ElectionException {

        int term = node.startElection();
        node.becomeLeader(term);

        node.fail();

        assertEquals(NodeState.FAILED, node.getState());
        assertFalse(node.receiveHeartbeat(term + 1, 2), "failed node ignores messages");
        assertFalse(node.handleVoteRequest(new VoteRequest(term + 1, 2)).isVoteGranted());

        node.recover();

        assertEquals(NodeState.FOLLOWER, node.getState());
        assertEquals(term, node.getCurrentTerm(), "term survives a crash");
    }

    @Test
    void leaderCannotTimeOutButFollowerTimesOutOnlyOnce() throws ElectionException {

        assertTrue(node.markHeartbeatTimedOut());
        assertFalse(node.markHeartbeatTimedOut(), "reported only once");

        RaftNode leader = new RaftNode(2);
        leader.becomeLeader(leader.startElection());

        assertFalse(leader.markHeartbeatTimedOut());
    }
}
