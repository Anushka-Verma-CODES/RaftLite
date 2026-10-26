package com.raftlite;

import java.util.ArrayList;
import java.util.List;

public class RaftCluster {

    private final List<Node> nodes = new ArrayList<>();

    public RaftCluster(int numberOfNodes) {
        for (int i = 1; i <= numberOfNodes; i++) {
            nodes.add(new Node(i));
        }
    }

    public void displayClusterStatus() {
        System.out.println("\n===== RAFT-LITE CLUSTER =====");

        for (Node node : nodes) {
            node.displayStatus();
        }

        System.out.println("=============================\n");
    }

    public void electLeader(int candidateId) {
        Node candidate = nodes.get(candidateId - 1);

        if (candidate.getState() == NodeState.FAILED) {
            System.out.println("Failed node cannot start an election.");
            return;
        }

        candidate.startElection();

        for (Node node : nodes) {
            if (node.getNodeId() == candidateId) {
                continue;
            }

            if (node.requestVote(candidate, candidate.getCurrentTerm())) {
                candidate.addVote();
            }
        }

        int majority = nodes.size() / 2 + 1;

        System.out.println(
                "Node " + candidateId + " received " +
                        candidate.getVotesReceived() + " votes.");

        if (candidate.getVotesReceived() >= majority) {
            candidate.becomeLeader();
        } else {
            candidate.setState(NodeState.FOLLOWER);
            System.out.println("Election failed: no majority.");
        }
    }
}