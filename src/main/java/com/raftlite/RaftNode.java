package com.raftlite;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single server in the Raft-Lite cluster.
 *
 * Holds the election state of one node (role, term, vote) and applies
 * the Raft voting rules:
 *   - a node votes at most once per term (first come, first served)
 *   - any message with a higher term makes the node a FOLLOWER of that term
 *   - messages with a lower (stale) term are rejected
 */
public class RaftNode {

    // Each node picks a random timeout in this range so that nodes
    // do not all time out together and split the vote.
    public static final long MIN_ELECTION_TIMEOUT = 1500;
    public static final long MAX_ELECTION_TIMEOUT = 2500;

    private static final Random RANDOM = new Random();

    private final int nodeId;

    private NodeState state;
    private int currentTerm;
    private Integer votedFor;
    private Integer leaderId;

    // Ids of nodes that voted for this node in the current term.
    // A Set makes sure the same voter is never counted twice.
    private final Set<Integer> votesGranted = new HashSet<>();

    private final AtomicLong lastHeartbeatTime =
            new AtomicLong(System.currentTimeMillis());

    private volatile long electionTimeout;
    private volatile boolean heartbeatTimedOut = false;

    public RaftNode(int nodeId) {
        this.nodeId = nodeId;
        this.state = NodeState.FOLLOWER;
        this.currentTerm = 0;
        this.votedFor = null;
        this.leaderId = null;
        this.electionTimeout = randomElectionTimeout();
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------

    public int getNodeId() {
        return nodeId;
    }

    public synchronized NodeState getState() {
        return state;
    }

    public synchronized int getCurrentTerm() {
        return currentTerm;
    }

    public synchronized Integer getVotedFor() {
        return votedFor;
    }

    public synchronized int getVotesReceived() {
        return votesGranted.size();
    }

    /** The leader this node currently follows, if it knows one. */
    public synchronized Optional<Integer> getLeaderId() {
        return Optional.ofNullable(leaderId);
    }

    public long getElectionTimeout() {
        return electionTimeout;
    }

    public long getLastHeartbeatTime() {
        return lastHeartbeatTime.get();
    }

    public boolean hasHeartbeatTimedOut() {
        return heartbeatTimedOut;
    }

    public synchronized void displayStatus() {
        System.out.println(
                "Node " + nodeId +
                " | State: " + state +
                " | Term: " + currentTerm +
                " | Voted for: " + (votedFor == null ? "-" : votedFor) +
                " | Leader: " + getLeaderId().map(String::valueOf).orElse("-"));
    }

    // ---------------------------------------------------------------
    // Follower -> Candidate
    // ---------------------------------------------------------------

    /**
     * Moves this node to CANDIDATE for a new term and votes for itself.
     *
     * @return the term of the new election
     * @throws ElectionException if the node has failed
     */
    public synchronized int startElection() throws ElectionException {

        if (state == NodeState.FAILED) {
            throw new ElectionException(
                    "Node " + nodeId + " has failed and cannot start an election.");
        }

        currentTerm++;
        state = NodeState.CANDIDATE;
        votedFor = nodeId;
        leaderId = null;

        votesGranted.clear();
        votesGranted.add(nodeId);

        resetElectionTimer();

        System.out.println(
                "Node " + nodeId +
                " became CANDIDATE and started election for term " +
                currentTerm);

        return currentTerm;
    }

    // ---------------------------------------------------------------
    // Voting rules
    // ---------------------------------------------------------------

    /** Decides whether to grant a vote to the candidate in the request. */
    public synchronized VoteResponse handleVoteRequest(VoteRequest request) {

        if (state == NodeState.FAILED) {
            return new VoteResponse(nodeId, currentTerm, false);
        }

        if (request.getTerm() < currentTerm) {
            System.out.println(
                    "Node " + nodeId + " rejects Node " +
                    request.getCandidateId() + ": stale term " +
                    request.getTerm() + " < " + currentTerm);

            return new VoteResponse(nodeId, currentTerm, false);
        }

        if (request.getTerm() > currentTerm) {
            stepDown(request.getTerm());
        }

        boolean canVote = votedFor == null ||
                votedFor == request.getCandidateId();

        if (!canVote) {
            System.out.println(
                    "Node " + nodeId + " rejects Node " +
                    request.getCandidateId() + ": already voted for Node " +
                    votedFor + " in term " + currentTerm);

            return new VoteResponse(nodeId, currentTerm, false);
        }

        votedFor = request.getCandidateId();

        // Granting a vote counts as hearing from a live candidate,
        // so this node should not start its own election right away.
        resetElectionTimer();

        return new VoteResponse(nodeId, currentTerm, true);
    }

    /** Counts a vote that came back to this candidate. */
    public synchronized void handleVoteResponse(VoteResponse response) {

        if (state == NodeState.FAILED) {
            return;
        }

        if (response.getTerm() > currentTerm) {
            stepDown(response.getTerm());
            return;
        }

        if (state != NodeState.CANDIDATE ||
                response.getTerm() != currentTerm ||
                !response.isVoteGranted()) {
            return;
        }

        if (votesGranted.add(response.getVoterId())) {
            System.out.println(
                    "Node " + nodeId +
                    " received a vote from Node " + response.getVoterId() +
                    ". Total votes: " + votesGranted.size());
        }
    }

    // ---------------------------------------------------------------
    // Candidate -> Leader
    // ---------------------------------------------------------------

    /**
     * Makes this node LEADER, but only if it is still a candidate of
     * the given term (it may have stepped down while votes were counted).
     *
     * @return true if the node became leader
     */
    public synchronized boolean becomeLeader(int electionTerm) {

        if (state != NodeState.CANDIDATE || currentTerm != electionTerm) {
            return false;
        }

        state = NodeState.LEADER;
        leaderId = nodeId;

        System.out.println(
                "Node " + nodeId + " became LEADER for term " + currentTerm + "!");

        return true;
    }

    // ---------------------------------------------------------------
    // Heartbeats and higher terms
    // ---------------------------------------------------------------

    public synchronized void receiveHeartbeat(int leaderTerm, int senderId) {

        if (state == NodeState.FAILED || leaderTerm < currentTerm) {
            return;
        }

        if (leaderTerm > currentTerm) {
            stepDown(leaderTerm);

        } else if (state == NodeState.LEADER) {
            // Two leaders in one term must never happen.
            System.out.println(
                    "Node " + nodeId +
                    " received a same-term heartbeat from Node " +
                    senderId + ". Leader conflict detected.");
            return;

        } else if (state == NodeState.CANDIDATE) {
            // Someone else already won this term.
            state = NodeState.FOLLOWER;

            System.out.println(
                    "Node " + nodeId +
                    " lost the election for term " + currentTerm +
                    " to Node " + senderId + " and is now a FOLLOWER.");
        }

        leaderId = senderId;
        resetElectionTimer();
    }

    /**
     * Called when this node learns another node's term (for example from
     * a heartbeat reply). A higher term means this node is out of date.
     */
    public synchronized void updateTermIfHigher(int otherTerm) {

        if (state != NodeState.FAILED && otherTerm > currentTerm) {
            stepDown(otherTerm);
        }
    }

    /** Moves to a newer term as a follower with no vote cast yet. */
    private void stepDown(int newTerm) {

        NodeState oldState = state;

        currentTerm = newTerm;
        state = NodeState.FOLLOWER;
        votedFor = null;
        leaderId = null;
        votesGranted.clear();

        if (oldState == NodeState.LEADER || oldState == NodeState.CANDIDATE) {
            System.out.println(
                    "Node " + nodeId + " saw higher term " + newTerm +
                    " and stepped down from " + oldState + " to FOLLOWER.");
        }
    }

    public synchronized void receiveMessage(RaftMessage message) {

        if (state == NodeState.FAILED) {
            return;
        }

        switch (message.getType()) {

            case HEARTBEAT:
                receiveHeartbeat(message.getTerm(), message.getSenderId());
                break;

            case VOTE_RESPONSE:
                handleVoteResponse(new VoteResponse(
                        message.getSenderId(),
                        message.getTerm(),
                        message.isVoteGranted()));
                break;

            case VOTE_REQUEST:
                System.out.println(
                        "Node " + nodeId +
                        " received vote request from Node " +
                        message.getSenderId());
                break;
        }
    }

    // ---------------------------------------------------------------
    // Election timeout
    // ---------------------------------------------------------------

    public boolean isElectionTimeoutElapsed() {
        return System.currentTimeMillis() - lastHeartbeatTime.get() >= electionTimeout;
    }

    /**
     * Marks that this node's election timer ran out. Followers and
     * candidates (whose election did not finish) can time out; leaders cannot.
     *
     * @return true the first time the timeout is detected
     */
    public synchronized boolean markHeartbeatTimedOut() {

        if (heartbeatTimedOut ||
                (state != NodeState.FOLLOWER && state != NodeState.CANDIDATE)) {
            return false;
        }

        heartbeatTimedOut = true;
        return true;
    }

    private void resetElectionTimer() {
        lastHeartbeatTime.set(System.currentTimeMillis());
        electionTimeout = randomElectionTimeout();
        heartbeatTimedOut = false;
    }

    private static long randomElectionTimeout() {
        return MIN_ELECTION_TIMEOUT +
                RANDOM.nextInt((int) (MAX_ELECTION_TIMEOUT - MIN_ELECTION_TIMEOUT));
    }

    // ---------------------------------------------------------------
    // Crash / recovery
    // ---------------------------------------------------------------

    /**
     * Crashes the node. Term and vote are kept, as in real Raft
     * where they are saved on disk and survive a crash.
     */
    public synchronized void fail() {

        if (state == NodeState.FAILED) {
            return;
        }

        state = NodeState.FAILED;
        leaderId = null;
        votesGranted.clear();

        System.out.println("Node " + nodeId + " has FAILED.");
    }

    public synchronized void recover() {

        if (state != NodeState.FAILED) {
            return;
        }

        state = NodeState.FOLLOWER;
        resetElectionTimer();

        System.out.println(
                "Node " + nodeId + " has RECOVERED as a FOLLOWER.");
    }
}
