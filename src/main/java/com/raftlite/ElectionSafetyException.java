package com.raftlite;

/**
 * Unchecked exception thrown if two different nodes become leader in
 * the same term. This breaks Raft's Election Safety rule, so it means
 * there is a bug in the election logic.
 */
public class ElectionSafetyException extends RuntimeException {

    public ElectionSafetyException(int term, int existingLeader, int newLeader) {
        super("Election safety violated in term " + term +
                ": Node " + existingLeader + " is already leader, but Node " +
                newLeader + " was also elected.");
    }
}
