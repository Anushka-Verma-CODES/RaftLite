
package com.raftlite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RaftCluster {

    private final List<Node> nodes = new ArrayList<>();
    private final NetworkSimulator network = new NetworkSimulator();

    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledExecutorService timeoutScheduler;

    private static final long HEARTBEAT_INTERVAL = 500;
    private static final long HEARTBEAT_TIMEOUT = 2000;

    public RaftCluster(int numberOfNodes) {
        for (int i = 1; i <= numberOfNodes; i++) {
            Node node = new Node(i);
            nodes.add(node);
            network.registerNode(node);
        }
    }

    public void displayClusterStatus() {
        System.out.println("\n===== RAFT-LITE CLUSTER =====");

        for (Node node : nodes) {
            node.displayStatus();
        }

        System.out.println("=============================\n");
    }

    public synchronized void electLeader(int candidateId) {
        Node candidate = getNodeById(candidateId);

        if (candidate == null) {
            System.out.println("Election failed: node not found.");
            return;
        }

        if (candidate.getState() == NodeState.FAILED) {
            System.out.println("Failed node cannot start an election.");
            return;
        }

        candidate.startElection();

        if (candidate.getState() != NodeState.CANDIDATE) {
            return;
        }

        int votes = 1;

        for (Node node : nodes) {
            if (node.getNodeId() == candidateId) {
                continue;
            }

            boolean voteGranted = network.requestVote(
                    candidateId,
                    node.getNodeId(),
                    candidate.getCurrentTerm());

            if (voteGranted) {
                candidate.addVote();
                votes++;
            }
        }

        int majority = nodes.size() / 2 + 1;

        System.out.println(
                "Node " + candidateId + " received "
                        + votes + " votes.");

        if (votes >= majority
                && candidate.getState() == NodeState.CANDIDATE) {
            candidate.becomeLeader();
        } else {
            candidate.setState(NodeState.FOLLOWER);
            System.out.println("Election failed: no majority.");
        }
    }

    public synchronized void startHeartbeats() {
        if (heartbeatScheduler != null
                && !heartbeatScheduler.isShutdown()) {
            return;
        }

        heartbeatScheduler =
                Executors.newSingleThreadScheduledExecutor();

        timeoutScheduler =
                Executors.newSingleThreadScheduledExecutor();

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            for (Node leader : nodes) {
                if (leader.getState() != NodeState.LEADER) {
                    continue;
                }

                for (Node follower : nodes) {
                    if (follower.getNodeId() == leader.getNodeId()) {
                        continue;
                    }

                    RaftMessage heartbeat = new RaftMessage(
                            leader.getNodeId(),
                            follower.getNodeId(),
                            MessageType.HEARTBEAT,
                            leader.getCurrentTerm());

                    network.sendMessage(heartbeat);
                }

                System.out.println(
                        "Leader Node " + leader.getNodeId()
                                + " sent heartbeats.");
            }
        }, 0, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);

        timeoutScheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();

            for (Node node : nodes) {
                if (node.getState() != NodeState.FOLLOWER) {
                    continue;
                }

                if (currentTime - node.getLastHeartbeatTime()
                        > HEARTBEAT_TIMEOUT) {

                    if (node.markHeartbeatTimedOut()) {
                        System.out.println(
                                "Heartbeat timeout detected for Node "
                                        + node.getNodeId());

                        electLeader(node.getNodeId());
                    }
                }
            }
        }, HEARTBEAT_TIMEOUT, 500, TimeUnit.MILLISECONDS);
    }

    public synchronized void stopHeartbeats() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
            heartbeatScheduler = null;
        }

        if (timeoutScheduler != null) {
            timeoutScheduler.shutdownNow();
            timeoutScheduler = null;
        }
    }

    public void crashNode(int nodeId) {
        Node node = getNodeById(nodeId);

        if (node == null) {
            System.out.println(
                    "Cannot crash: Node " + nodeId + " was not found.");
            return;
        }

        node.fail();
    }

    public void recoverNode(int nodeId) {
        Node node = getNodeById(nodeId);

        if (node == null) {
            System.out.println(
                    "Cannot recover: Node " + nodeId + " was not found.");
            return;
        }

        node.recover();
    }

    public void disconnectNodes(int nodeA, int nodeB) {
        if (getNodeById(nodeA) == null
                || getNodeById(nodeB) == null) {
            System.out.println("Cannot disconnect: node not found.");
            return;
        }

        network.disconnectNodes(nodeA, nodeB);
    }

    public void reconnectNodes(int nodeA, int nodeB) {
        if (getNodeById(nodeA) == null
                || getNodeById(nodeB) == null) {
            System.out.println("Cannot reconnect: node not found.");
            return;
        }

        network.reconnectNodes(nodeA, nodeB);
    }

    private Node getNodeById(int nodeId) {
        for (Node node : nodes) {
            if (node.getNodeId() == nodeId) {
                return node;
            }
        }

        return null;
    }
}
