
package com.raftlite;

import java.util.concurrent.atomic.AtomicLong;

public class Node {

    private final int nodeId;

    private NodeState state;
    private int currentTerm;
    private Integer votedFor;
    private int votesReceived;

    private final AtomicLong lastHeartbeatTime =
            new AtomicLong(System.currentTimeMillis());

    private volatile boolean heartbeatTimedOut = false;

    public Node(int nodeId) {
        this.nodeId = nodeId;
        this.state = NodeState.FOLLOWER;
        this.currentTerm = 0;
        this.votedFor = null;
        this.votesReceived = 0;
    }

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
        return votesReceived;
    }

    public synchronized void setState(NodeState state) {
        if (this.state != NodeState.FAILED) {
            this.state = state;
        }
    }

    public synchronized void displayStatus() {
        System.out.println(
                "Node " + nodeId +
                " | State: " + state +
                " | Term: " + currentTerm);
    }

    public synchronized void startElection() {

        if (state == NodeState.FAILED) {
            return;
        }

        currentTerm++;
        state = NodeState.CANDIDATE;
        votedFor = nodeId;
        votesReceived = 1;

        heartbeatTimedOut = false;

        System.out.println(
                "Node " + nodeId +
                " started election for term " + currentTerm);
    }

    public synchronized boolean requestVote(
            Node candidate, int candidateTerm) {

        if (state == NodeState.FAILED) {
            return false;
        }

        if (candidateTerm < currentTerm) {
            return false;
        }

        if (candidateTerm > currentTerm) {
            currentTerm = candidateTerm;
            state = NodeState.FOLLOWER;
            votedFor = null;
            votesReceived = 0;
        }

        if (votedFor == null ||
                votedFor.equals(candidate.getNodeId())) {

            votedFor = candidate.getNodeId();

            System.out.println(
                    "Node " + nodeId +
                    " voted for Node " + candidate.getNodeId());

            return true;
        }

        return false;
    }

    public synchronized void receiveVoteResponse(
            int responseTerm, boolean voteGranted) {

        if (state != NodeState.CANDIDATE) {
            return;
        }

        if (responseTerm > currentTerm) {
            currentTerm = responseTerm;
            state = NodeState.FOLLOWER;
            votedFor = null;
            votesReceived = 0;
            return;
        }

        if (responseTerm != currentTerm || !voteGranted) {
            return;
        }

        votesReceived++;

        System.out.println(
                "Node " + nodeId +
                " received a vote. Total votes: " +
                votesReceived);
    }

    public synchronized void becomeLeader() {

        if (state != NodeState.CANDIDATE) {
            return;
        }

        state = NodeState.LEADER;

        System.out.println(
                "Node " + nodeId + " became LEADER!");
    }

    public synchronized void receiveHeartbeat(
            int leaderTerm, int leaderId) {

        if (state == NodeState.FAILED ||
                leaderTerm < currentTerm) {
            return;
        }

        if (leaderTerm > currentTerm) {
            currentTerm = leaderTerm;
            votedFor = null;
            votesReceived = 0;
            state = NodeState.FOLLOWER;
        } else if (state == NodeState.CANDIDATE) {
            state = NodeState.FOLLOWER;
        } else if (state == NodeState.LEADER) {

            System.out.println(
                    "Node " + nodeId +
                    " received a same-term heartbeat from Node " +
                    leaderId + ". Leader conflict detected.");

            return;
        }

        lastHeartbeatTime.set(System.currentTimeMillis());
        heartbeatTimedOut = false;
    }

    public synchronized void receiveMessage(RaftMessage message) {

        if (state == NodeState.FAILED) {
            return;
        }

        switch (message.getType()) {

            case HEARTBEAT:
                receiveHeartbeat(
                        message.getTerm(),
                        message.getSenderId());
                break;

            case VOTE_RESPONSE:
                receiveVoteResponse(
                        message.getTerm(),
                        message.isVoteGranted());
                break;

            case VOTE_REQUEST:
                System.out.println(
                        "Node " + nodeId +
                        " received vote request from Node " +
                        message.getSenderId());
                break;
        }
    }

    public long getLastHeartbeatTime() {
        return lastHeartbeatTime.get();
    }

    public boolean hasHeartbeatTimedOut() {
        return heartbeatTimedOut;
    }

    public synchronized boolean markHeartbeatTimedOut() {

        if (heartbeatTimedOut ||
                state != NodeState.FOLLOWER) {
            return false;
        }

        heartbeatTimedOut = true;
        return true;
    }

    public synchronized void fail() {

        if (state == NodeState.FAILED) {
            return;
        }

        state = NodeState.FAILED;

        System.out.println(
                "Node " + nodeId + " has FAILED.");
    }

    public synchronized void recover() {

        if (state != NodeState.FAILED) {
            return;
        }

        state = NodeState.FOLLOWER;

        lastHeartbeatTime.set(System.currentTimeMillis());
        heartbeatTimedOut = false;

        System.out.println(
                "Node " + nodeId +
                " has RECOVERED as a FOLLOWER.");
    }
}
