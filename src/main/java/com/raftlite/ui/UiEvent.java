package com.raftlite.ui;

/**
 * Something the cluster view should animate. Created on simulator threads
 * and consumed on the JavaFX thread.
 *
 * @param from the node the message or event starts at
 * @param to   the receiving node, or the same as {@code from} for node events
 */
record UiEvent(Kind kind, int from, int to) {

    enum Kind {
        HEARTBEAT,
        VOTE_REQUEST,
        VOTE_GRANTED,
        VOTE_REJECTED,
        MESSAGE_BLOCKED,
        ELECTION_STARTED,
        LEADER_ELECTED,
        ELECTION_TIMEOUT
    }
}
