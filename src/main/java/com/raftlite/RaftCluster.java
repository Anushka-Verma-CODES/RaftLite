
package com.raftlite;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RaftCluster {

    private final List<RaftNode> nodes =
            new CopyOnWriteArrayList<>();

    private final NetworkSimulator network =
            new NetworkSimulator();

    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledExecutorService timeoutScheduler;

    private final ElectionManager electionManager;

    private static final long HEARTBEAT_INTERVAL = 500;
    private static final long TIMEOUT_CHECK_INTERVAL = 100;

    public RaftCluster(int numberOfNodes) {

        for (int i = 1; i <= numberOfNodes; i++) {

            RaftNode node = new RaftNode(i);

            nodes.add(node);
            network.registerNode(node);
        }

        electionManager = new ElectionManager(nodes, network);
    }

    public synchronized void displayClusterStatus() {

        System.out.println("\n===== RAFT-LITE CLUSTER =====");

        for (RaftNode node : nodes) {
            node.displayStatus();
        }

        System.out.println("=============================\n");
    }

    public synchronized void electLeader(int candidateId) {

        try {

            if (electionManager.runElection(candidateId)) {
                broadcastHeartbeats();
            }

        } catch (ElectionException e) {

            System.out.println(
                    "Election not started: " + e.getMessage());
        }
    }

    public ElectionManager getElectionManager() {
        return electionManager;
    }

    private void broadcastHeartbeats() {

        for (RaftNode leader : nodes) {

            if (leader.getState() != NodeState.LEADER) {
                continue;
            }

            for (RaftNode follower : nodes) {

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
                    "Leader Node " + leader.getNodeId() +
                    " sent heartbeats.");
        }
    }

    public synchronized void startHeartbeats() {

        if (heartbeatScheduler != null &&
                !heartbeatScheduler.isShutdown()) {
            return;
        }

        heartbeatScheduler =
                Executors.newSingleThreadScheduledExecutor();

        timeoutScheduler =
                Executors.newSingleThreadScheduledExecutor();

        heartbeatScheduler.scheduleAtFixedRate(
                this::broadcastHeartbeats,
                0,
                HEARTBEAT_INTERVAL,
                TimeUnit.MILLISECONDS);

        timeoutScheduler.scheduleAtFixedRate(() -> {

            for (RaftNode node : nodes) {

                // Each node has its own random election timeout,
                // so usually only one node times out at a time.
                if (node.isElectionTimeoutElapsed() &&
                        node.markHeartbeatTimedOut()) {

                    System.out.println(
                            "Election timeout (" +
                            node.getElectionTimeout() +
                            " ms) for Node " + node.getNodeId());

                    electLeader(node.getNodeId());
                }
            }

        }, RaftNode.MIN_ELECTION_TIMEOUT,
           TIMEOUT_CHECK_INTERVAL,
           TimeUnit.MILLISECONDS);
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

        RaftNode node = getNodeById(nodeId);

        if (node == null) {

            System.out.println(
                    "Cannot crash: Node " +
                    nodeId + " not found.");

            return;
        }

        node.fail();
    }

    public void recoverNode(int nodeId) {

        RaftNode node = getNodeById(nodeId);

        if (node == null) {

            System.out.println(
                    "Cannot recover: Node " +
                    nodeId + " not found.");

            return;
        }

        node.recover();
    }

    public void disconnectNodes(int nodeA, int nodeB) {

        if (getNodeById(nodeA) == null ||
                getNodeById(nodeB) == null) {

            System.out.println(
                    "Cannot disconnect: node not found.");

            return;
        }

        network.disconnectNodes(nodeA, nodeB);
    }

    public void reconnectNodes(int nodeA, int nodeB) {

        if (getNodeById(nodeA) == null ||
                getNodeById(nodeB) == null) {

            System.out.println(
                    "Cannot reconnect: node not found.");

            return;
        }

        network.reconnectNodes(nodeA, nodeB);
    }

    public void disconnectNode(int nodeId) {
        network.disconnectNode(nodeId);
    }

    public void reconnectNode(int nodeId) {
        network.reconnectNode(nodeId);
    }

    public synchronized int getCurrentLeaderId() {

        for (RaftNode node : nodes) {

            if (node.getState() == NodeState.LEADER) {
                return node.getNodeId();
            }
        }

        return -1;
    }

    private RaftNode getNodeById(int nodeId) {

        for (RaftNode node : nodes) {

            if (node.getNodeId() == nodeId) {
                return node;
            }
        }

        return null;
    }
}
