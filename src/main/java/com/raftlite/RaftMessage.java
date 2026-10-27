package com.raftlite;

public class RaftMessage {

    private final int senderId;
    private final int receiverId;
    private final MessageType type;
    private final int term;

    public RaftMessage(int senderId, int receiverId,
                       MessageType type, int term) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.type = type;
        this.term = term;
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
}