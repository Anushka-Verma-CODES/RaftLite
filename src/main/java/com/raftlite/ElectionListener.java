package com.raftlite;

/**
 * Lets other modules (logger, statistics, UI) react to elections
 * without changing the election code. Every method has an empty
 * default, so an implementation only overrides what it needs.
 */
public interface ElectionListener {

    default void onElectionStarted(int candidateId, int term) {
    }

    default void onLeaderElected(int leaderId, int term, int votes, long durationMillis) {
    }

    default void onElectionFailed(int candidateId, int term, int votes, int majority) {
    }
}
