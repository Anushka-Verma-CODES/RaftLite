
package com.raftlite;

public class RaftMessage {

    private final int senderId;
    private final int receiverId;
    private final MessageType type;
    private final int term;
    private final boolean voteGranted;

    public RaftMessage(int senderId, int receiverId,
                       MessageType type, int term) {

        this(senderId, receiverId, type, term, false);
    }

    public RaftMessage(int senderId, int receiverId,
                       MessageType type, int term,
                       boolean voteGranted) {

        this.senderId = senderId;
        this.receiverId = receiverId;
        this.type = type;
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public MessageType getType() {
        return type;
    }

    public int getTerm() {
        return term;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }

    @Override
    public String toString() {
        return "RaftMessage{" +
                "sender=" + senderId +
                ", receiver=" + receiverId +
                ", type=" + type +
                ", term=" + term +
                ", voteGranted=" + voteGranted +
                '}';
    }
}
