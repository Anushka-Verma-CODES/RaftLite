
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

    public synchronized void setState(NodeState state) {
        this.state = state;
    }

    public synchronized void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
    }

    public synchronized void displayStatus() {
        System.out.println(
                "Node " + nodeId +
                        " | State: " + state +
                        " | Term: " + currentTerm);
    }

    public synchronized Integer getVotedFor() {
        return votedFor;
    }

    public synchronized int getVotesReceived() {
        return votesReceived;
    }

    public synchronized void startElection() {
        if (state == NodeState.FAILED) {
            System.out.println("Failed node cannot start an election.");
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

    public synchronized void becomeLeader() {
        if (state == NodeState.FAILED) {
            return;
        }

        state = NodeState.LEADER;

        System.out.println("Node " + nodeId + " became LEADER!");
    }

    public synchronized void addVote() {
        votesReceived++;
    }

    
    public synchronized void receiveHeartbeat(int leaderTerm) {
        if (state == NodeState.FAILED || leaderTerm < currentTerm) {
            return;
        }

    if (leaderTerm > currentTerm) {
            currentTerm = leaderTerm;
            votedFor = null;
            state = NodeState.FOLLOWER;
        } else if (state != NodeState.FOLLOWER) {
            state = NodeState.FOLLOWER;
        }

        lastHeartbeatTime.set(System.currentTimeMillis());
        heartbeatTimedOut = false;
    }


    public long getLastHeartbeatTime() {
        return lastHeartbeatTime.get();
    }

    public boolean hasHeartbeatTimedOut() {
        return heartbeatTimedOut;
    }

    public synchronized boolean markHeartbeatTimedOut() {
        if (heartbeatTimedOut) {
            return false;
        }

        heartbeatTimedOut = true;
        return true;
    }

    public synchronized void fail() {
        state = NodeState.FAILED;

        System.out.println("Node " + nodeId + " has FAILED.");
    }

    public synchronized void recover() {
        state = NodeState.FOLLOWER;
        lastHeartbeatTime.set(System.currentTimeMillis());
        heartbeatTimedOut = false;

        System.out.println(
                "Node " + nodeId + " has RECOVERED as a FOLLOWER.");
    }
}
