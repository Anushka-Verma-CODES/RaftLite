package com.raftlite;

/**
 * Sent by a candidate to ask another node for its vote.
 */
public class VoteRequest {

    private final int term;
    private final int candidateId;

    public VoteRequest(int term, int candidateId) {
        this.term = term;
        this.candidateId = candidateId;
    }

    public int getTerm() {
        return term;
    }

    public int getCandidateId() {
        return candidateId;
    }

    @Override
    public String toString() {
        return "VoteRequest{term=" + term + ", candidate=" + candidateId + '}';
    }
}
