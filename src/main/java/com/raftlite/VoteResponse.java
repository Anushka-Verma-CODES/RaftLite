package com.raftlite;

/**
 * A node's answer to a VoteRequest. The term lets an out-of-date
 * candidate find out that a newer term has started.
 */
public class VoteResponse {

    private final int voterId;
    private final int term;
    private final boolean voteGranted;

    public VoteResponse(int voterId, int term, boolean voteGranted) {
        this.voterId = voterId;
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public int getVoterId() {
        return voterId;
    }

    public int getTerm() {
        return term;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }

    @Override
    public String toString() {
        return "VoteResponse{voter=" + voterId + ", term=" + term +
                ", granted=" + voteGranted + '}';
    }
}
