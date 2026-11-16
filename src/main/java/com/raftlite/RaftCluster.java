
package com.raftlite;

import com.raftlite.monitoring.SimulationMonitor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RaftCluster {

    private final List<RaftNode> nodes =
            new CopyOnWriteArrayList<>();

    private final NetworkSimulator network =
            new NetworkSimulator();

    // Logs and counts everything that happens in this cluster.
    private final SimulationMonitor monitor =
            new SimulationMonitor();

    // Observers added from outside (e.g. the dashboard). They receive the
    // cluster-level events below as well as the network events.
    private final List<SimulationListener> extraListeners =
            new CopyOnWriteArrayList<>();

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

        electionManager.addListener(monitor);
        network.addListener(monitor);
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

    /** The event log and statistics for this cluster. */
    public SimulationMonitor getMonitor() {
        return monitor;
    }

    // Package-private so tests can send one round of heartbeats on demand.
    void broadcastHeartbeats() {

        for (RaftNode leader : nodes) {

            if (leader.getState() != NodeState.LEADER) {
                continue;
            }

            int leaderTerm = leader.getCurrentTerm();

            for (RaftNode follower : nodes) {

                if (follower.getNodeId() == leader.getNodeId()) {
                    continue;
                }

                // A reply may have shown this leader a newer term, making it
                // step down mid-round. It must stop sending heartbeats then,
                // otherwise followers would accept it as leader of that newer term.
                if (leader.getState() != NodeState.LEADER ||
                        leader.getCurrentTerm() != leaderTerm) {
                    break;
                }

                RaftMessage heartbeat = new RaftMessage(
                        leader.getNodeId(),
                        follower.getNodeId(),
                        MessageType.HEARTBEAT,
                        leaderTerm);

                network.sendMessage(heartbeat);
            }

            if (leader.getState() == NodeState.LEADER) {
                System.out.println(
                        "Leader Node " + leader.getNodeId() +
                        " sent heartbeats.");
            }
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

                    notifyClusterListeners(listener ->
                            listener.onElectionTimeout(
                                    node.getNodeId(),
                                    node.getElectionTimeout()));

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

        boolean wasAlreadyFailed = node.getState() == NodeState.FAILED;

        node.fail();

        if (!wasAlreadyFailed) {
            notifyClusterListeners(listener -> listener.onNodeFailed(nodeId));
        }
    }

    public void recoverNode(int nodeId) {

        RaftNode node = getNodeById(nodeId);

        if (node == null) {

            System.out.println(
                    "Cannot recover: Node " +
                    nodeId + " not found.");

            return;
        }

        boolean wasFailed = node.getState() == NodeState.FAILED;

        node.recover();

        if (wasFailed) {
            notifyClusterListeners(listener -> listener.onNodeRecovered(nodeId));
        }
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

    /** Sends a cluster-level event to the monitor and to extra listeners. */
    private void notifyClusterListeners(Consumer<SimulationListener> event) {

        event.accept(monitor);

        for (SimulationListener listener : extraListeners) {
            event.accept(listener);
        }
    }

    /** Read-only view of the nodes, in id order (used by the dashboard). */
    public List<RaftNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    /** True if nodes A and B can currently exchange messages over their link. */
    public boolean isLinkConnected(int nodeA, int nodeB) {
        return !network.isLinkDisconnected(nodeA, nodeB);
    }

    /** Registers an extra observer of network traffic, timeouts and faults. */
    public void addSimulationListener(SimulationListener listener) {
        network.addListener(listener);
        extraListeners.add(listener);
    }

    /** Registers an extra observer of elections. */
    public void addElectionListener(ElectionListener listener) {
        electionManager.addListener(listener);
    }

    /** Number of nodes in the cluster. */
    public int getClusterSize() {
        return nodes.size();
    }

    // Package-private so tests in this package can inspect a node.
    RaftNode getNodeById(int nodeId) {

        for (RaftNode node : nodes) {

            if (node.getNodeId() == nodeId) {
                return node;
            }
        }

        return null;
    }
}
