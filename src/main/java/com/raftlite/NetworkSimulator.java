
package com.raftlite;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkSimulator {

    private final Map<Integer, Node> nodes = new ConcurrentHashMap<>();
    private final Set<String> disconnectedLinks = ConcurrentHashMap.newKeySet();

    public void registerNode(Node node) {
        nodes.put(node.getNodeId(), node);
    }

    public boolean sendMessage(RaftMessage message) {
        Node sender = nodes.get(message.getSenderId());
        Node receiver = nodes.get(message.getReceiverId());

        if (sender == null || receiver == null) {
            System.out.println("Message failed: node not found.");
            return false;
        }

        if (sender.getState() == NodeState.FAILED ||
                receiver.getState() == NodeState.FAILED) {
            System.out.println("Message blocked: a node has failed.");
            return false;
        }

        String link = getLinkKey(
                message.getSenderId(), message.getReceiverId());

        if (disconnectedLinks.contains(link)) {
            System.out.println("Message blocked: link is disconnected.");
            return false;
        }

        switch (message.getType()) {
            case HEARTBEAT:
                receiver.receiveHeartbeat(message.getTerm());
                break;

            case REQUEST_VOTE:
                boolean voteGranted = receiver.requestVote(
                        sender, message.getTerm());

                System.out.println(
                        "Node " + receiver.getNodeId()
                                + (voteGranted
                                ? " granted the vote."
                                : " rejected the vote."));
                break;

            case VOTE_RESPONSE:
                System.out.println(
                        "Vote response delivered from Node "
                                + sender.getNodeId() + " to Node "
                                + receiver.getNodeId()
                                + ". Processing is not implemented yet.");
                break;
        }

        return true;
    }

    public void disconnectNodes(int nodeA, int nodeB) {
        String link = getLinkKey(nodeA, nodeB);
        disconnectedLinks.add(link);

        System.out.println(
                "Connection disconnected between Node "
                        + nodeA + " and Node " + nodeB);
    }

    public void reconnectNodes(int nodeA, int nodeB) {
        String link = getLinkKey(nodeA, nodeB);
        disconnectedLinks.remove(link);

        System.out.println(
                "Connection restored between Node "
                        + nodeA + " and Node " + nodeB);
    }

    private String getLinkKey(int nodeA, int nodeB) {
        int first = Math.min(nodeA, nodeB);
        int second = Math.max(nodeA, nodeB);

        return first + "-" + second;
    }

    
    public boolean requestVote(int candidateId, int receiverId, int term) {
        Node candidate = nodes.get(candidateId);
        Node receiver = nodes.get(receiverId);

        if (candidate == null || receiver == null) {
            return false;
        }

        if (candidate.getState() == NodeState.FAILED || receiver.getState() == NodeState.FAILED) {
            return false;
        }

        String link = getLinkKey(candidateId, receiverId);

    if (disconnectedLinks.contains(link)) {
            System.out.println(
                "Vote request blocked between Node "
                        + candidateId + " and Node " + receiverId);
            return false;
        }

        boolean voteGranted = receiver.requestVote(candidate, term);

        if (voteGranted) {
            System.out.println(
                    "Node " + receiverId + " granted a vote to Node " + candidateId);
        }

        return voteGranted;
    }


}
