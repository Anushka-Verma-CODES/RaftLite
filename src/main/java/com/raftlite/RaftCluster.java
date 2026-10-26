package com.raftlite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RaftCluster {

    private final List<Node> nodes = new ArrayList<>();
    private ScheduledExecutorService heartbeatScheduler;

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

    public void startHeartbeats() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            for (Node leader : nodes) {
                if (leader.getState() == NodeState.LEADER) {
                    for (Node follower : nodes) {
                        if (follower.getNodeId() != leader.getNodeId()) {
                            follower.receiveHeartbeat(
                                    leader.getCurrentTerm());
                        }
                    }

                    System.out.println(
                            "Leader Node " + leader.getNodeId()
                                    + " sent heartbeats.");
                }
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void stopHeartbeats() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
        }
    }
}