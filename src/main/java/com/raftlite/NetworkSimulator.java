
package com.raftlite;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkSimulator {

    private final Map<Integer, RaftNode> nodes =
            new ConcurrentHashMap<>();

    private final Set<String> disconnectedLinks =
            ConcurrentHashMap.newKeySet();

    public void registerNode(RaftNode node) {

        nodes.put(node.getNodeId(), node);

        System.out.println(
                "Network: Node " + node.getNodeId() +
                " registered.");
    }

    public boolean sendMessage(RaftMessage message) {

        RaftNode sender = nodes.get(message.getSenderId());
        RaftNode receiver = nodes.get(message.getReceiverId());

        if (sender == null || receiver == null) {
            System.out.println(
                    "Message failed: node not found.");

            return false;
        }

        if (sender.getState() == NodeState.FAILED ||
                receiver.getState() == NodeState.FAILED) {

            System.out.println(
                    "Message blocked: a node has failed.");

            return false;
        }

        String link = getLinkKey(
                message.getSenderId(),
                message.getReceiverId());

        if (disconnectedLinks.contains(link)) {

            System.out.println(
                    "Message blocked: link is disconnected.");

            return false;
        }

        switch (message.getType()) {

            case HEARTBEAT:

                receiver.receiveMessage(message);

                // The heartbeat reply carries the follower's term, so a
                // stale leader learns about a newer term and steps down.
                sender.updateTermIfHigher(receiver.getCurrentTerm());
                break;

            case VOTE_REQUEST:

                VoteResponse vote = receiver.handleVoteRequest(
                        new VoteRequest(
                                message.getTerm(),
                                sender.getNodeId()));

                System.out.println(
                        "Node " + receiver.getNodeId() +
                        (vote.isVoteGranted()
                                ? " granted a vote to Node "
                                : " rejected the vote of Node ")
                        + sender.getNodeId());

                RaftMessage response = new RaftMessage(
                        receiver.getNodeId(),
                        sender.getNodeId(),
                        MessageType.VOTE_RESPONSE,
                        vote.getTerm(),
                        vote.isVoteGranted());

                sendMessage(response);
                break;

            case VOTE_RESPONSE:

                receiver.receiveMessage(message);

                System.out.println(
                        "Vote response delivered from Node " +
                        sender.getNodeId() +
                        " to Node " +
                        receiver.getNodeId());

                break;
        }

        return true;
    }

    public void disconnectNodes(int nodeA, int nodeB) {

        if (!nodes.containsKey(nodeA) ||
                !nodes.containsKey(nodeB) ||
                nodeA == nodeB) {
            return;
        }

        disconnectedLinks.add(getLinkKey(nodeA, nodeB));

        System.out.println(
                "Connection disconnected between Node " +
                nodeA + " and Node " + nodeB);
    }

    public void reconnectNodes(int nodeA, int nodeB) {

        disconnectedLinks.remove(
                getLinkKey(nodeA, nodeB));

        System.out.println(
                "Connection restored between Node " +
                nodeA + " and Node " + nodeB);
    }

    public void disconnectNode(int nodeId) {

        if (!nodes.containsKey(nodeId)) {
            return;
        }

        for (Integer otherNode : nodes.keySet()) {

            if (otherNode != nodeId) {
                disconnectNodes(nodeId, otherNode);
            }
        }
    }

    public void reconnectNode(int nodeId) {

        if (!nodes.containsKey(nodeId)) {
            return;
        }

        for (Integer otherNode : nodes.keySet()) {

            if (otherNode != nodeId) {
                reconnectNodes(nodeId, otherNode);
            }
        }
    }

    public boolean requestVote(
            int candidateId, int receiverId, int term) {

        RaftMessage request = new RaftMessage(
                candidateId,
                receiverId,
                MessageType.VOTE_REQUEST,
                term);

        return sendMessage(request);
    }

    private String getLinkKey(int nodeA, int nodeB) {

        int first = Math.min(nodeA, nodeB);
        int second = Math.max(nodeA, nodeB);

        return first + "-" + second;
    }
}
