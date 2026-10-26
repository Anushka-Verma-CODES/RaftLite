package com.raftlite;
import java.util.concurrent.atomic.AtomicLong;


public class Node {

    private final int nodeId;
    private NodeState state;
    private int currentTerm;
    private Integer votedFor;
    private int votesReceived;

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

    public NodeState getState() {
        return state;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public void setState(NodeState state) {
        this.state = state;
    }

    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
    }

    public void displayStatus() {
        System.out.println(
                "Node " + nodeId +
                        " | State: " + state +
                        " | Term: " + currentTerm);
    }

    public Integer getVotedFor() {
        return votedFor;
    }

    public int getVotesReceived() {
        return votesReceived;
    }

    public void startElection() {
        currentTerm++;
        state = NodeState.CANDIDATE;
        votedFor = nodeId;
        votesReceived = 1;

        System.out.println(
                "Node " + nodeId +
                        " started election for term " + currentTerm);
    }

    public boolean requestVote(Node candidateId, int candidateTerm) {
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

        if (votedFor == null || votedFor.equals(candidateId.getNodeId())) {
            votedFor = candidateId.getNodeId();

            System.out.println(
                    "Node " + nodeId +
                            " voted for Node " + candidateId.getNodeId());

            return true;
        }

        return false;
    }

    public void becomeLeader() {
        state = NodeState.LEADER;

        System.out.println("Node " + nodeId + " became LEADER!");
    }

    public void addVote() {
        votesReceived++;
    }

    private final AtomicLong lastHeartbeatTime = new AtomicLong(
            System.currentTimeMillis());

    public void receiveHeartbeat(int leaderTerm) {
        if (state == NodeState.FAILED || leaderTerm < currentTerm) {
            return;
        }

        currentTerm = leaderTerm;

        if (state != NodeState.LEADER) {
            state = NodeState.FOLLOWER;
        }

        lastHeartbeatTime.set(System.currentTimeMillis());
    }

    public long getLastHeartbeatTime() {
        return lastHeartbeatTime.get();
    }
}