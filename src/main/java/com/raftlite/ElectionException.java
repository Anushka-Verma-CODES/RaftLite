package com.raftlite;

/**
 * Checked exception thrown when an election cannot be started,
 * for example because the node does not exist or has failed.
 */
public class ElectionException extends Exception {

    public ElectionException(String message) {
        super(message);
    }
}
