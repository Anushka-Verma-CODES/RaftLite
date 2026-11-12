package com.raftlite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs leader elections for the cluster.
 *
 * One election: the candidate starts a new term, asks every other node
 * for a vote through the network, and becomes leader only if it gets a
 * majority of the whole cluster (failed nodes still count towards the
 * cluster size, as in Raft).
 *
 * It also records the leader of every term and refuses a second leader
 * in the same term.
 */
public class ElectionManager {

    private final List<RaftNode> nodes;
    private final NetworkSimulator network;

    // term -> id of the node that won that term
    private final Map<Integer, Integer> leaderByTerm = new HashMap<>();

    private final List<ElectionListener> listeners = new ArrayList<>();

    private int electionAttempts = 0;

    public ElectionManager(List<RaftNode> nodes, NetworkSimulator network) {
        this.nodes = nodes;
        this.network = network;
    }

    /** Smallest number of votes that is more than half of the cluster. */
    public static int majorityOf(int clusterSize) {
        return clusterSize / 2 + 1;
    }

    public int getMajority() {
        return majorityOf(nodes.size());
    }

    public void addListener(ElectionListener listener) {
        listeners.add(listener);
    }

    /**
     * Runs one election with the given node as candidate.
     *
     * @return true if the candidate became leader
     * @throws ElectionException if the node does not exist or has failed
     */
    public synchronized boolean runElection(int candidateId) throws ElectionException {

        RaftNode candidate = findNode(candidateId)
                .orElseThrow(() -> new ElectionException(
                        "Node " + candidateId + " does not exist."));

        long startTime = System.currentTimeMillis();

        int term = candidate.startElection();
        electionAttempts++;

        for (ElectionListener listener : listeners) {
            listener.onElectionStarted(candidateId, term);
        }

        for (RaftNode node : nodes) {
            if (node.getNodeId() != candidateId) {
                network.requestVote(candidateId, node.getNodeId(), term);
            }
        }

        int votes = candidate.getVotesReceived();
        int majority = getMajority();

        System.out.println(
                "Node " + candidateId + " received " + votes + "/" +
                nodes.size() + " votes (majority = " + majority + ")");

        if (votes >= majority && candidate.becomeLeader(term)) {

            recordLeader(term, candidateId);

            long duration = System.currentTimeMillis() - startTime;

            for (ElectionListener listener : listeners) {
                listener.onLeaderElected(candidateId, term, votes, duration);
            }

            return true;
        }

        System.out.println(
                "Election for term " + term + " failed: majority not reached.");

        for (ElectionListener listener : listeners) {
            listener.onElectionFailed(candidateId, term, votes, majority);
        }

        return false;
    }

    private void recordLeader(int term, int leaderId) {

        Integer existing = leaderByTerm.putIfAbsent(term, leaderId);

        if (existing != null && existing != leaderId) {
            throw new ElectionSafetyException(term, existing, leaderId);
        }
    }

    public synchronized Optional<Integer> getLeaderForTerm(int term) {
        return Optional.ofNullable(leaderByTerm.get(term));
    }

    /** Copy of the term -> leader history, safe to read from other classes. */
    public synchronized Map<Integer, Integer> getLeaderHistory() {
        return Collections.unmodifiableMap(new HashMap<>(leaderByTerm));
    }

    public synchronized int getElectionAttempts() {
        return electionAttempts;
    }

    private Optional<RaftNode> findNode(int nodeId) {

        for (RaftNode node : nodes) {
            if (node.getNodeId() == nodeId) {
                return Optional.of(node);
            }
        }

        return Optional.empty();
    }
}
